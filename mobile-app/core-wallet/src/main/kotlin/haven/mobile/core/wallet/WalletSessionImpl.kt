package haven.mobile.core.wallet

import android.content.Context
import com.reown.android.CoreClient
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
import kotlinx.coroutines.withTimeout
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

    private val _pairingUri = MutableStateFlow<String?>(null)
    override val pairingUri: StateFlow<String?> = _pairingUri.asStateFlow()

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

    /**
     * Hands a freshly created WalletConnect pairing URI (wc:) to installed wallet apps via
     * ACTION_VIEW. Without this the URI is only logged and nothing wallet-related ever appears
     * on screen. When no wallet handles the scheme the URI stays on [pairingUri] so the
     * onboarding UI can offer it as a fallback.
     */
    private fun openInWallet(uri: String) {
        try {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            diag("CONNECT: handed pairing uri to installed wallets")
        } catch (e: android.content.ActivityNotFoundException) {
            diag("CONNECT: no wallet app handles wc: uris — use the copy/QR fallback in the UI")
        } catch (e: Exception) {
            diagError("CONNECT: opening wallet intent failed", e)
        }
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
        isInitialized = ReownBootstrap.initialize(
            projectId = config.projectId,
            application = context.applicationContext as android.app.Application,
            appName = config.appName,
            appDescription = config.appDescription,
            appIconUrl = config.appIconUrl,
            redirectUrl = config.redirectUrl,
            log = { line -> diag(line) },
            logError = { stage, e -> diagError(stage, e) }
        )
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

        // Trigger AppKit programmatically (AppKit 1.6.14 has no open()). onSuccess delivers the
        // pairing URI; approval later arrives via onSessionApproved / getAccount().
        try {
            diag("CONNECT: creating pairing then invoking AppKit.connect")
            _pairingUri.value = null
            val pairing = CoreClient.Pairing.create { error ->
                diagError("CONNECT: Pairing.create onError", error.throwable)
            }
            if (pairing == null) {
                diag("CONNECT: Pairing.create returned null — failing connect")
                return Result.failure(WalletError.AppKitNotInitialized)
            }
            // A session proposal with no namespaces is approved by the wallet but never settles —
            // the wallet must be offered concrete chains/methods/events to grant.
            val chains = AppKitChainsPresets.ethChains.values.toList()
            val chainRefs = chains.map { "${it.chainNamespace}:${it.chainReference}" }
            val proposal = Modal.Model.Namespace.Proposal(
                chains = chainRefs,
                // Methods mirror reown-kotlin's sample Info.Eth.defaultMethods plus v4 alias.
                // Notably eth_signTypedData (unsuffixed) must be requested: MetaMask mobile
                // routes signing requests under that name.
                methods = (
                    chains.flatMap { it.requiredMethods } + listOf(
                        "eth_sendTransaction",
                        "personal_sign",
                        "eth_sign",
                        "eth_signTypedData",
                        "eth_signTypedData_v4"
                    )
                    ).distinct(),
                events = (chains.flatMap { it.events } + listOf("chainChanged", "accountsChanged")).distinct()
            )
            diag("CONNECT: proposing ${chainRefs.size} chains (${chainRefs.first()}…)")
            AppKit.connect(
                connectParams = Modal.Params.ConnectParams(
                    sessionNamespaces = mapOf(chainRefs.first().substringBefore(":") to proposal),
                    pairing = pairing
                ),
                onSuccess = { uri ->
                    diag("CONNECT: onSuccess — pairing uri=${uri ?: "null"}")
                    if (uri != null) {
                        _pairingUri.value = uri
                        openInWallet(uri)
                    }
                },
                onError = { error ->
                    diagError("CONNECT: AppKit.connect onError", error.throwable)
                }
            )
        } catch (e: Exception) {
            diagError("CONNECT: AppKit.connect threw synchronously", e)
        }

        var waited = 0
        while (waited < 240) {
            delay(500)
            try {
                val acc = AppKit.getAccount()
                if (acc != null) {
                    diag("CONNECT: poll ${waited / 2}s — account appeared ${acc.address}")
                    walletDataStore.saveAddress(acc.address)
                    val connector = try { AppKit.getConnectorType()?.name ?: "WalletConnect" } catch (_: Exception) { "WalletConnect" }
                    walletDataStore.saveLastConnector(connector)
                    _address.value = acc.address
                    _pairingUri.value = null
                    return Result.success(acc.address)
                }
                if (waited % 8 == 0) diag("CONNECT: poll ${waited / 2}s/120s — waiting for approval")
            } catch (e: Exception) {
                diagError("CONNECT: poll $waited getAccount() threw", e)
            }
            waited++
        }
        diag("CONNECT: timed out after 120s — no session approved (see INIT/DELEGATE lines above)")
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
        _pairingUri.value = null
    }

    override suspend fun signTypedDataV4(json: String): Result<String> = withContext(ioDispatcher) {
        if (config.projectId.isBlank()) {
            return@withContext Result.failure(WalletError.AppKitNotInitialized)
        }
        val addr = address.value ?: try { AppKit.getAccount()?.address } catch (_: Exception) { null }
            ?: return@withContext Result.failure(WalletError.NoAddressReturned)
        if (json.isBlank()) return@withContext Result.failure(WalletError.InvalidSignatureFormat)

        // Build eth_signTypedData params as [address, typedDataJsonString] and send under the
        // UNSUFFIXED method name: MetaMask mobile's WalletConnect router routes eth_signTypedData
        // (matches reown-kotlin's reference dapp sample); the _v4 name is dropped there — with a
        // stringified param it 500s ("unexpected character '\'"), with an object it silently
        // no-ops. EIP-712 hashing is identical for both, so signatures verify the same.
        val params = JSONArray().apply {
            put(addr)
            put(json)
        }.toString()

        val request = Request(
            method = "eth_signTypedData",
            params = params,
            chainId = "eip155:1"
        )

        try {
            diag("SIGN: requesting eth_signTypedData — addr=$addr chain=eip155:1 jsonBytes=${json.length}")
            val signature = kotlinx.coroutines.withTimeout(120_000) {
                suspendCancellableCoroutine<String> { cont ->
                    var pendingRequestId: Long? = null
                    // Wrap existing delegate: capture response but forward all events to original behavior via diag
                    val responseFlow = try { null } catch (_: Exception) { null }
                    try {
                        AppKit.request(
                            request = request,
                            onSuccess = { sent ->
                                pendingRequestId = when (sent) {
                                    is SentRequestResult.WalletConnect -> sent.requestId
                                    is SentRequestResult.Coinbase -> Long.MIN_VALUE
                                }
                                diag("SIGN: request sent requestId=$pendingRequestId — waiting for wallet")
                                // Poll AppKit delegate via shared flow: use a lightweight polling coroutine that also observes delegate
                                // Register a one-shot listener by chaining onto the existing delegate via composition is not exposed,
                                // so we poll AppKit.getAccount is not enough — we instead observe via AppKit.setDelegate wrapper that forwards.
                                // Simplest reliable: use AppKit's wcEventModels if available, fallback to delegate wrapper.
                                try {
                                    val existingDelegateField = null
                                    // Install forwarding delegate that captures our requestId
                                    val forwarding = object : AppKit.ModalDelegate {
                                        override fun onSessionApproved(s: Modal.Model.ApprovedSession) { diag("SIGN DELEGATE: onSessionApproved") }
                                        override fun onSessionRejected(s: Modal.Model.RejectedSession) { diag("SIGN DELEGATE: onSessionRejected") }
                                        override fun onSessionUpdate(s: Modal.Model.UpdatedSession) {}
                                        override fun onSessionEvent(s: Modal.Model.SessionEvent) {}
                                        override fun onSessionExtend(s: Modal.Model.Session) {}
                                        override fun onSessionDelete(s: Modal.Model.DeletedSession) { diag("SIGN DELEGATE: onSessionDelete") }
                                        override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {
                                            diag("SIGN: received response topic=${response.topic.take(10)}...")
                                            val result = response.result
                                            when (result) {
                                                is Modal.Model.JsonRpcResponse.JsonRpcResult -> {
                                                    val sig = result.result as? String
                                                    diag("SIGN: received JsonRpcResult sig=${sig?.take(12)}...")
                                                    if (sig != null && cont.isActive) cont.resume(sig)
                                                }
                                                is Modal.Model.JsonRpcResponse.JsonRpcError -> {
                                                    diag("SIGN: received JsonRpcError ${result.message}")
                                                    if (cont.isActive) cont.resumeWithException(Exception(result.message))
                                                }
                                            }
                                        }
                                        override fun onProposalExpired(p: Modal.Model.ExpiredProposal) { diag("SIGN: onProposalExpired") }
                                        override fun onRequestExpired(r: Modal.Model.ExpiredRequest) {
                                            diag("SIGN: onRequestExpired")
                                            if (cont.isActive) cont.resumeWithException(Exception("Request expired"))
                                        }
                                        override fun onConnectionStateChange(s: Modal.Model.ConnectionState) {}
                                        override fun onError(error: Modal.Model.Error) {
                                            diagError("SIGN: delegate onError", error.throwable)
                                            if (cont.isActive) cont.resumeWithException(error.throwable)
                                        }
                                    }
                                    AppKit.setDelegate(forwarding)
                                } catch (_: Exception) {}
                            },
                            onError = { err ->
                                diagError("SIGN: AppKit.request onError", err)
                                if (cont.isActive) cont.resumeWithException(err)
                            }
                        )
                    } catch (e: Exception) {
                        diagError("SIGN: AppKit.request threw", e)
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                    cont.invokeOnCancellation { diag("SIGN: cancelled") }
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
