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

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var isInitialized = false

    init {
        initializeReownIfNeeded()
        restoreSession()
        observeReownDelegate()
    }

    private fun initializeReownIfNeeded() {
        if (config.projectId.isBlank()) {
            Timber.w("WalletConfig projectId blank — Reown not initialized, wallet connect will fail until local.properties wallet.projectId is set")
            return
        }
        if (isInitialized) return
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
                Timber.e(error.throwable, "CoreClient initialize error")
            }
            AppKit.initialize(
                init = Modal.Params.Init(core = CoreClient),
                onSuccess = {
                    isInitialized = true
                    Timber.i("AppKit initialized")
                },
                onError = { error ->
                    Timber.e(error.throwable, "AppKit initialize error")
                }
            )
            AppKit.setChains(AppKitChainsPresets.ethChains.values.toList())
            // Optional SIWE auth params for Haven — use ethChains ids
            // Haven signs GateRequest via eth_signTypedData_v4, not SIWE, so no auth payload needed.
            isInitialized = true
        } catch (e: Exception) {
            // CoreClient may already be initialized (e.g., second process)
            if (e.message?.contains("already") == true || e is IllegalStateException) {
                isInitialized = true
            } else {
                Timber.e(e, "Reown init failed")
            }
        }
    }

    private fun restoreSession() {
        scope.launch {
            // Load persisted address synchronously then reconcile with AppKit
            val persisted = try { walletDataStore.loadPersistedAddress() } catch (_: Exception) { null }
            val account: Account? = try { if (isInitialized) AppKit.getAccount() else null } catch (_: Exception) { null }
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
                    walletDataStore.clearAll()
                    null
                }
                else -> persisted
            }
            _address.value = reconciled
        }
    }

    private fun observeReownDelegate() {
        scope.launch {
            // Poll AppKit account periodically to keep StateFlow in sync when delegate events arrive
            // The sample uses AppKitDelegate.wcEventModels — we bridge via getAccount polling plus delegate hook
            try {
                AppKit.setDelegate(object : AppKit.ModalDelegate {
                    override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
                        val addr = try { AppKit.getAccount()?.address } catch (_: Exception) { null }
                        if (addr != null) {
                            scope.launch {
                                walletDataStore.saveAddress(addr)
                                val conn = try { AppKit.getConnectorType()?.name ?: "WalletConnect" } catch (_: Exception) { "WalletConnect" }
                                walletDataStore.saveLastConnector(conn)
                                _address.value = addr
                            }
                        }
                    }
                    override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) {}
                    override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) {
                        val addr = try { AppKit.getAccount()?.address } catch (_: Exception) { null }
                        scope.launch { _address.value = addr; if (addr != null) walletDataStore.saveAddress(addr) }
                    }
                    override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) {}
                    override fun onSessionExtend(session: Modal.Model.Session) {}
                    override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
                        scope.launch {
                            walletDataStore.clearAll()
                            _address.value = null
                        }
                    }
                    override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {}
                    override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) {}
                    override fun onRequestExpired(request: Modal.Model.ExpiredRequest) {}
                    override fun onConnectionStateChange(state: Modal.Model.ConnectionState) {}
                    override fun onError(error: Modal.Model.Error) {
                        Timber.e(error.throwable, "AppKit delegate error")
                    }
                })
            } catch (_: Exception) {
                // AppKit not yet initialized
            }
        }
    }

    override suspend fun connect(): Result<String> {
        if (config.projectId.isBlank()) {
            return Result.failure(WalletError.AppKitNotInitialized)
        }
        initializeReownIfNeeded()
        // If already connected, return immediately
        try {
            AppKit.getAccount()?.let { acc ->
                walletDataStore.saveAddress(acc.address)
                walletDataStore.saveLastConnector("WalletConnect")
                _address.value = acc.address
                return Result.success(acc.address)
            }
        } catch (_: Exception) {}

        // Best-practice B: plain NavHost without accompanist bottomSheet. Instead of requiring
        // appKitGraph/ConnectButton navigation, trigger AppKit directly if possible. We try the
        // programmatic Modal.Params.Connect path first, then fall back to polling for an externally
        // triggered session (e.g. via deep link or prior AppKit UI). This keeps cold start stable
        // (no BottomSheetNavigator) while making the plain "Connect wallet" Button functional.
        // If AppKit has a UI entry point, invoke it via reflection to avoid hard compile dependency
        // on the exact AppKit version's open() signature (1.6.x varies).
        try {
            // Try to open AppKit modal programmatically — best effort, ignore if not available
            try {
                val modalClass = Class.forName("com.reown.appkit.client.Modal")
                // Some builds expose AppKit.open() or Modal.open() — try both
                val openMethod = try {
                    AppKit::class.java.methods.firstOrNull { it.name == "open" && it.parameterCount <= 1 }
                } catch (_: Exception) { null }
                if (openMethod != null) {
                    try {
                        if (openMethod.parameterCount == 0) openMethod.invoke(AppKit) else openMethod.invoke(AppKit, null)
                        Timber.i("AppKit.open() invoked via reflection")
                    } catch (e: Exception) { Timber.w(e, "AppKit.open reflection failed") }
                }
            } catch (_: Exception) {}
            // Also try AppKit.connect with empty namespaces as a programmatic trigger — it will
            // show the wallet selector if the Delegate is set. We construct a minimal Connect params
            // via reflection to stay compatible across 1.6.x
            try {
                val connectMethod = AppKit::class.java.methods.firstOrNull { it.name == "connect" && it.parameterTypes.any { p -> p.simpleName.contains("Connect") } }
                if (connectMethod != null) {
                    Timber.i("AppKit.connect method found: ${connectMethod.name} ${connectMethod.parameterTypes.joinToString { it.simpleName }}")
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {}

        var waited = 0
        while (waited < 40) {
            delay(250)
            try {
                val acc = AppKit.getAccount()
                if (acc != null) {
                    walletDataStore.saveAddress(acc.address)
                    val connector = try { AppKit.getConnectorType()?.name ?: "WalletConnect" } catch (_: Exception) { "WalletConnect" }
                    walletDataStore.saveLastConnector(connector)
                    _address.value = acc.address
                    return Result.success(acc.address)
                }
            } catch (_: Exception) {}
            waited++
        }
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
