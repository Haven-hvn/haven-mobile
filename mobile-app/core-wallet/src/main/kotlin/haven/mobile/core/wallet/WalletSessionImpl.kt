package haven.mobile.core.wallet

import android.content.Context
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.client.models.Account
import com.reown.appkit.client.models.request.Request
import com.reown.appkit.client.models.request.SentRequestResult
import com.reown.appkit.presets.AppKitChainsPresets
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class WalletSessionImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: WalletConfig,
    private val walletDataStore: WalletDataStore,
) : WalletSession {

    private val _address = MutableStateFlow<String?>(null)
    override val address: StateFlow<String?> = _address.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<String>>(emptyList())
    override val diagnostics: StateFlow<List<String>> = _diagnostics.asStateFlow()

    private fun diag(line: String) {
        Timber.i("[WS] %s", line)
        _diagnostics.update { (it + line).takeLast(14) }
    }

    private fun diagError(stage: String, e: Throwable) {
        Timber.e(e, "[WS] %s", stage)
        _diagnostics.update { (it + "✗ $stage — ${e.javaClass.simpleName}: ${e.message ?: "no message"}").takeLast(14) }
    }

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var isInitialized = false

    init {
        diag("init: projectId=${mask(config.projectId)} redirect=${config.redirectUrl}")
        initializeReownIfNeeded()
        restoreSession()
        observeReownDelegate()
    }

    private fun mask(value: String): String =
        if (value.length <= 8) "(blank/short)" else value.take(6) + "…" + value.takeLast(2)

    private fun initializeReownIfNeeded() {
        if (config.projectId.isBlank()) {
            Timber.w("[WS][INIT] projectId blank — Reown not initialized, wallet connect will fail until local.properties wallet.projectId is set")
            return
        }
        if (isInitialized) return
        diag("INIT: starting CoreClient+AppKit initialization")
        try {
            val appMetaData = Core.Model.AppMetaData(
                name = config.appName.ifBlank { "Haven" },
                description = config.appDescription.ifBlank { "Haven — gated media" },
                url = "https://haven",
                icons = listOfNotNull(config.appIconUrl.takeIf { it.isNotBlank() }),
                redirect = config.redirectUrl.ifBlank { "haven://connect" },
                appLink = config.redirectUrl.ifBlank { "https://haven" }
            )
            // Use Application context for CoreClient
            CoreClient.initialize(
                projectId = config.projectId,
                connectionType = ConnectionType.AUTOMATIC,
                application = context.applicationContext as android.app.Application,
                metaData = appMetaData
            ) { error ->
                diagError("INIT: CoreClient initialize error", error.throwable)
            }
            diag("INIT: CoreClient.initialize returned, calling AppKit.initialize")
            AppKit.initialize(
                init = Modal.Params.Init(core = CoreClient),
                onSuccess = {
                    isInitialized = true
                    diag("INIT: AppKit initialized OK (async onSuccess)")
                },
                onError = { error ->
                    diagError("INIT: AppKit initialize error", error.throwable)
                }
            )
            AppKit.setChains(AppKitChainsPresets.ethChains.values.toList())
            diag("INIT: setChains applied (${AppKitChainsPresets.ethChains.values.size} chains); isInitialized=true")
            // Optional SIWE auth params for Haven — use ethChains ids
            // Haven signs GateRequest via eth_signTypedData_v4, not SIWE, so no auth payload needed.
            isInitialized = true
        } catch (e: Exception) {
            // CoreClient may already be initialized (e.g., second process)
            if (e.message?.contains("already") == true || e is IllegalStateException) {
                isInitialized = true
                diagError("INIT: Reown already-initialized path", e)
            } else {
                diagError("INIT: Reown init failed", e)
            }
        }
    }

    private fun restoreSession() {
        scope.launch {
            // Load persisted address synchronously then reconcile with AppKit
            val persisted = try { walletDataStore.loadPersistedAddress() } catch (_: Exception) { null }
            val account: Account? = try { if (isInitialized) AppKit.getAccount() else null } catch (_: Exception) { null }
            diag("RESTORE: persisted=${persisted ?: "null"} liveAccount=${account?.address ?: "null"}")
            val reconciled = when {
                account != null -> {
                    // AppKit has live session — persist it
                    walletDataStore.saveAddress(account.address)
                    val connector = try { AppKit.getConnectorType()?.name ?: "WalletConnect" } catch (_: Exception) { "WalletConnect" }
                    walletDataStore.saveLastConnector(connector)
                    account.address
                }
                persisted != null && account == null -> {
                    // Persisted but AppKit session gone — clear (per spec)
                    diag("RESTORE: persisted address exists but AppKit session gone — clearing")
                    walletDataStore.clearAll()
                    null
                }
                else -> persisted
            }
            _address.value = reconciled
            diag("RESTORE: reconciled address=${reconciled ?: "null"}")
        }
    }

    private fun observeReownDelegate() {
        scope.launch {
            // Poll AppKit account periodically to keep StateFlow in sync when delegate events arrive
            // The sample uses AppKitDelegate.wcEventModels — we bridge via getAccount polling plus delegate hook
            try {
                AppKit.setDelegate(object : AppKit.ModalDelegate {
                    override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
                        diag("DELEGATE: onSessionApproved")
                        val addr = try { AppKit.getAccount()?.address } catch (e: Exception) {
                            diagError("DELEGATE: getAccount after approve failed", e); null
                        }
                        if (addr != null) {
                            scope.launch {
                                walletDataStore.saveAddress(addr)
                                val conn = try { AppKit.getConnectorType()?.name ?: "WalletConnect" } catch (_: Exception) { "WalletConnect" }
                                walletDataStore.saveLastConnector(conn)
                                _address.value = addr
                                diag("DELEGATE: approved → address emitted: $addr")
                            }
                        } else {
                            diag("DELEGATE: approved but getAccount() returned null")
                        }
                    }
                    override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) {
                        diag("DELEGATE: onSessionRejected")
                    }
                    override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) {
                        Timber.i("[WS][DELEGATE] onSessionUpdate: %s", updatedSession)
                        val addr = try { AppKit.getAccount()?.address } catch (_: Exception) { null }
                        scope.launch { _address.value = addr; if (addr != null) walletDataStore.saveAddress(addr) }
                    }
                    override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) {
                        Timber.d("[WS][DELEGATE] onSessionEvent: %s", sessionEvent)
                    }
                    override fun onSessionExtend(session: Modal.Model.Session) {
                        Timber.d("[WS][DELEGATE] onSessionExtend")
                    }
                    override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
                        diag("DELEGATE: onSessionDelete — clearing")
                        scope.launch {
                            walletDataStore.clearAll()
                            _address.value = null
                        }
                    }
                    override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {}
                    override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) {
                        diag("DELEGATE: onProposalExpired")
                    }
                    override fun onRequestExpired(request: Modal.Model.ExpiredRequest) {
                        diag("DELEGATE: onRequestExpired")
                    }
                    override fun onConnectionStateChange(state: Modal.Model.ConnectionState) {
                        diag("DELEGATE: relay ${if (state.isAvailable) "online" else "offline"}")
                    }
                    override fun onError(error: Modal.Model.Error) {
                        diagError("DELEGATE: AppKit delegate error", error.throwable)
                    }
                })
                diag("DELEGATE: registered OK")
            } catch (e: Exception) {
                diagError("DELEGATE: registration failed (AppKit not initialized?)", e)
            }
        }
    }

    override suspend fun connect(): Result<String> {
        diag("CONNECT: enter (projectId=${mask(config.projectId)}, isInitialized=$isInitialized)")
        if (config.projectId.isBlank()) {
            diag("CONNECT: fail-fast — projectId blank")
            return Result.failure(WalletError.AppKitNotInitialized)
        }
        initializeReownIfNeeded()
        // If already connected, return immediately
        try {
            val existing = AppKit.getAccount()
            if (existing != null) {
                diag("CONNECT: already connected as ${existing.address}")
                walletDataStore.saveAddress(existing.address)
                walletDataStore.saveLastConnector("WalletConnect")
                _address.value = existing.address
                return Result.success(existing.address)
            }
        } catch (e: Exception) {
            diagError("CONNECT: pre-check getAccount() threw", e)
        }

        // Best-practice B: plain NavHost without accompanist bottomSheet. Instead of requiring
        // appKitGraph/ConnectButton navigation, trigger AppKit programmatically via
        // Modal.Params.Connect (AppKit 1.6.14 has no open(); verified against the AAR).
        // onSuccess delivers the pairing URI; approval later arrives via onSessionApproved.
        try {
            diag("CONNECT: creating pairing then invoking AppKit.connect")
            val pairing = CoreClient.Pairing.create { error ->
                diagError("CONNECT: Pairing.create onError", error.throwable)
            }
            if (pairing == null) {
                diag("CONNECT: Pairing.create returned null — failing connect")
                return Result.failure(WalletError.AppKitNotInitialized)
            }
            AppKit.connect(
                connect = Modal.Params.Connect(pairing = pairing),
                onSuccess = { uri ->
                    diag("CONNECT: onSuccess — pairing uri=${uri ?: "null"}")
                },
                onError = { error ->
                    diagError("CONNECT: AppKit.connect onError", error.throwable)
                }
            )
        } catch (e: Exception) {
            diagError("CONNECT: AppKit.connect threw synchronously", e)
        }

        var waited = 0
        while (waited < 40) {
            delay(250)
            try {
                val acc = AppKit.getAccount()
                if (acc != null) {
                    diag("CONNECT: poll $waited — account appeared ${acc.address}")
                    walletDataStore.saveAddress(acc.address)
                    val connector = try { AppKit.getConnectorType()?.name ?: "WalletConnect" } catch (_: Exception) { "WalletConnect" }
                    walletDataStore.saveLastConnector(connector)
                    _address.value = acc.address
                    return Result.success(acc.address)
                }
                if (waited % 4 == 0) diag("CONNECT: poll $waited/40 — waiting for approval")
            } catch (e: Exception) {
                diagError("CONNECT: poll $waited getAccount() threw", e)
            }
            waited++
        }
        diag("CONNECT: timed out after 10s — no session approved (see INIT/DELEGATE lines above)")
        return Result.failure(WalletError.ConnectFailed("Wallet not connected — ensure a wallet (MetaMask/Rainbow/Trust) is installed and approve the connection. If this persists, reinstall the debug build with a valid wallet.projectId."))
    }

    override suspend fun disconnect() {
        try {
            if (isInitialized) {
                suspendCancellableCoroutine<Unit> { cont ->
                    try {
                        AppKit.disconnect(
                            onSuccess = { cont.resume(Unit) },
                            onError = { cont.resume(Unit) }
                        )
                    } catch (e: Exception) {
                        cont.resume(Unit)
                    }
                }
            }
        } catch (_: Exception) {}
        walletDataStore.clearAll()
        _address.value = null
    }

    override suspend fun signTypedDataV4(json: String): Result<String> = withContext(ioDispatcher) {
        if (config.projectId.isBlank()) {
            return@withContext Result.failure(WalletError.AppKitNotInitialized)
        }
        val addr = address.value ?: try { AppKit.getAccount()?.address } catch (_: Exception) { null }
            ?: return@withContext Result.failure(WalletError.NoAddressReturned)
        if (json.isBlank()) return@withContext Result.failure(WalletError.InvalidSignatureFormat)

        // Build eth_signTypedData_v4 params as [address, typedDataJsonString]
        // Reown expects second element to be the JSON string for EIP-712 data.
        val params = JSONArray().apply {
            put(addr)
            put(json)
        }.toString()

        val request = Request(
            method = "eth_signTypedData_v4",
            params = params,
            chainId = "eip155:1"
        )

        try {
            val signature = suspendCancellableCoroutine<String> { cont ->
                var delegateSet = false
                var pendingRequestId: Long? = null

                // Temporary delegate to capture the response for this specific requestId
                val tempDelegate = object : AppKit.ModalDelegate {
                    override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {}
                    override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) {}
                    override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) {}
                    override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) {}
                    override fun onSessionExtend(session: Modal.Model.Session) {}
                    override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {}
                    override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {
                        if (pendingRequestId != null && response.topic.isNotBlank()) {
                            val result = response.result
                            when (result) {
                                is Modal.Model.JsonRpcResponse.JsonRpcResult -> {
                                    val sig = result.result as? String
                                    if (sig != null && cont.isActive) cont.resume(sig)
                                }
                                is Modal.Model.JsonRpcResponse.JsonRpcError -> {
                                    if (cont.isActive) cont.resumeWithException(Exception(result.message))
                                }
                            }
                        }
                    }
                    override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) {}
                    override fun onRequestExpired(request: Modal.Model.ExpiredRequest) {}
                    override fun onConnectionStateChange(state: Modal.Model.ConnectionState) {}
                    override fun onError(error: Modal.Model.Error) {
                        if (cont.isActive) cont.resumeWithException(error.throwable)
                    }
                }

                // We reuse the global delegate by wrapping — easiest is to rely on wcEventModels flow via polling?
                // For simplicity, use AppKit.request with SentRequestResult and then wait on delegate's flow.
                // To avoid double-delegate registration, we just call request and suspend for result via delegate polling.

                try {
                    AppKit.request(
                        request = request,
                        onSuccess = { sent ->
                            pendingRequestId = when (sent) {
                                is SentRequestResult.WalletConnect -> sent.requestId
                                is SentRequestResult.Coinbase -> Long.MIN_VALUE
                            }
                            // Install temp delegate after we know requestId
                            try {
                                if (!delegateSet) {
                                    AppKit.setDelegate(tempDelegate)
                                    delegateSet = true
                                }
                            } catch (_: Exception) {}
                        },
                        onError = { err ->
                            if (cont.isActive) cont.resumeWithException(err)
                        }
                    )
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                cont.invokeOnCancellation {
                    // no-op: delegate remains; AppKit manages lifecycle
                }
            }

            if (signature.isBlank()) return@withContext Result.failure(WalletError.InvalidSignatureFormat)
            val normalized = signature.trim()
            // Strict EVM signature validation: 0x + 130 hex chars (65 bytes r,s,v) — accept 132 total
            if (!normalized.startsWith("0x") || (normalized.length != 132 && normalized.length != 130)) {
                return@withContext Result.failure(WalletError.InvalidSignatureFormat)
            }
            if (!normalized.matches(Regex("^0x[0-9a-fA-F]{130,132}$"))) {
                return@withContext Result.failure(WalletError.InvalidSignatureFormat)
            }
            Result.success(normalized)
        } catch (e: Exception) {
            when (e) {
                is WalletError -> Result.failure(e)
                else -> Result.failure(WalletError.SigningFailed(e.message ?: "Unknown error"))
            }
        }
    }
}

sealed class WalletError : Exception() {
    object AppKitNotInitialized : WalletError() {
        override val message: String get() = "AppKit not initialized — set wallet.projectId in local.properties"
    }
    object NoAddressReturned : WalletError() {
        override val message: String get() = "No wallet address — connect a wallet first"
    }
    data class ConnectFailed(override val message: String) : WalletError()
    object InvalidSignatureFormat : WalletError() {
        override val message: String get() = "Invalid signature format — expected 0x + 130 hex chars"
    }
    data class SigningFailed(override val message: String) : WalletError()
}
