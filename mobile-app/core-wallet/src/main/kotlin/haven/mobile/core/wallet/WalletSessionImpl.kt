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

    private val _identities = MutableStateFlow<List<WalletIdentity>>(emptyList())
    override val identities: StateFlow<List<WalletIdentity>> = _identities.asStateFlow()

    private val _activeIdentity = MutableStateFlow<WalletIdentity?>(null)
    override val activeIdentity: StateFlow<WalletIdentity?> = _activeIdentity.asStateFlow()

    private val _isLive = MutableStateFlow(false)
    override val isLive: StateFlow<Boolean> = _isLive.asStateFlow()

    private fun liveAccountAddress(): String? =
        try { if (isInitialized) AppKit.getAccount()?.address else null } catch (_: Exception) { null }

    private fun refreshLive() {
        _isLive.value = IdentityAddress.same(liveAccountAddress(), _address.value) && _address.value != null
    }

    /** Points the browsing flows at [address] (already in [_identities]) and persists the registry. */
    private suspend fun activate(address: String, connector: String = "") {
        val now = System.currentTimeMillis()
        _identities.value = IdentityRegistry.upsert(_identities.value, address, connector, now)
        val identity = _identities.value.firstOrNull { IdentityAddress.same(it.address, address) }
        _address.value = identity?.address ?: address
        _activeIdentity.value = identity
        refreshLive()
        walletDataStore.saveIdentityState(IdentityState(_identities.value, _address.value))
    }

    /** Ends the live WalletConnect session, if any. Stored identities and data are untouched. */
    private suspend fun endLiveSession() {
        try {
            if (isInitialized && liveAccountAddress() != null) {
                suspendCancellableCoroutine<Unit> { cont ->
                    try {
                        AppKit.disconnect(
                            onSuccess = { cont.resume(Unit) },
                            onError = { cont.resume(Unit) },
                        )
                    } catch (_: Exception) {
                        cont.resume(Unit)
                    }
                }
            }
        } catch (_: Exception) {
        }
        _isLive.value = false
        _pairingUri.value = null
    }

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
            // Load the identity registry (migrating the legacy single wallet), then reconcile
            // with AppKit. A stored identity whose WalletConnect session is gone is NOT cleared:
            // it stays active for browsing and shows as not-live until reconnected.
            val state = try { walletDataStore.loadIdentityState() } catch (_: Exception) { IdentityState() }
            _identities.value = state.identities
            val account: Account? = try { if (isInitialized) AppKit.getAccount() else null } catch (_: Exception) { null }
            diag("RESTORE: identities=${state.identities.size} active=${state.activeAddress ?: "null"} liveAccount=${account?.address ?: "null"}")
            when {
                account != null -> {
                    val connector = try { AppKit.getConnectorType()?.name ?: "WalletConnect" } catch (_: Exception) { "WalletConnect" }
                    activate(account.address, connector)
                }
                state.activeAddress != null -> {
                    val identity = state.identities.firstOrNull { IdentityAddress.same(it.address, state.activeAddress) }
                    _address.value = identity?.address ?: state.activeAddress
                    _activeIdentity.value = identity
                    _isLive.value = false
                }
                else -> {
                    _address.value = null
                    _activeIdentity.value = null
                    _isLive.value = false
                }
            }
            diag("RESTORE: reconciled address=${_address.value ?: "null"} live=${_isLive.value}")
        }
    }

    private fun observeReownDelegate() {
        scope.launch {
            // Poll AppKit account periodically to keep StateFlow in sync when delegate events arrive
            // The sample uses AppKitDelegate.wcEventModels — we bridge via getAccount polling plus delegate hook
            registerSessionDelegate()
        }
    }

    /**
     * Base session-lifecycle delegate. Re-installed after each sign round-trip because
     * [signTypedDataV4] temporarily swaps in a response-capturing delegate
     * (AppKit supports a single delegate via setDelegate).
     */
    private fun registerSessionDelegate() {
        try {
            AppKit.setDelegate(object : AppKit.ModalDelegate {
                    override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
                        diag("DELEGATE: onSessionApproved")
                        val addr = try { AppKit.getAccount()?.address } catch (e: Exception) {
                            diagError("DELEGATE: getAccount after approve failed", e); null
                        }
                        if (addr != null) {
                            scope.launch {
                                val conn = try { AppKit.getConnectorType()?.name ?: "WalletConnect" } catch (_: Exception) { "WalletConnect" }
                                activate(addr, conn)
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
                        scope.launch { if (addr != null) activate(addr) else _isLive.value = false }
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
                            _isLive.value = false
                            diag("DELEGATE: session deleted — identity kept, reconnect to sign")
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
                activate(existing.address, "WalletConnect")
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
            // Sepolia is appended explicitly: the presets ship mainnet L1s/L2s only,
            // and Haven gates live on Sepolia — a session approved without it
            // rejects every Sepolia signature instantly at request time.
            val chains = withSepolia(AppKitChainsPresets.ethChains.values.toList())
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
                    val connector = try { AppKit.getConnectorType()?.name ?: "WalletConnect" } catch (_: Exception) { "WalletConnect" }
                    activate(acc.address, connector)
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
        endLiveSession()
        diag("DISCONNECT: live session ended — identity and its data kept")
    }

    override suspend fun switchTo(address: String): Result<Unit> {
        val target = _identities.value.firstOrNull { IdentityAddress.same(it.address, address) }
            ?: return Result.failure(WalletError.UnknownIdentity(address))
        if (IdentityAddress.same(_address.value, target.address)) {
            refreshLive()
            return Result.success(Unit)
        }
        val live = liveAccountAddress()
        if (live != null && !IdentityAddress.same(live, target.address)) endLiveSession()
        _identities.value = IdentityRegistry.touch(_identities.value, target.address, System.currentTimeMillis())
        val touched = _identities.value.firstOrNull { it.id == target.id } ?: target
        _address.value = touched.address
        _activeIdentity.value = touched
        _pairingUri.value = null
        refreshLive()
        walletDataStore.saveIdentityState(IdentityState(_identities.value, _address.value))
        diag("SWITCH: active identity now ${touched.address} live=${_isLive.value} (nothing cleared)")
        return Result.success(Unit)
    }

    override suspend fun connectNewIdentity(): Result<String> {
        if (liveAccountAddress() != null) endLiveSession()
        return connect()
    }

    override suspend fun reconnect(): Result<String> = connect()

    override suspend fun renameIdentity(address: String, label: String): Result<Unit> {
        if (_identities.value.none { IdentityAddress.same(it.address, address) }) {
            return Result.failure(WalletError.UnknownIdentity(address))
        }
        _identities.value = IdentityRegistry.rename(_identities.value, address, label)
        _activeIdentity.value = _identities.value.firstOrNull { IdentityAddress.same(it.address, _address.value) }
        walletDataStore.saveIdentityState(IdentityState(_identities.value, _address.value))
        return Result.success(Unit)
    }

    override suspend fun forgetIdentity(address: String): Result<Unit> {
        val wasActive = IdentityAddress.same(_address.value, address)
        if (wasActive && IdentityAddress.same(liveAccountAddress(), address)) endLiveSession()
        val (remaining, nextActiveId) = IdentityRegistry.forget(_identities.value, _activeIdentity.value?.id, address)
        _identities.value = remaining
        if (wasActive) {
            val next = remaining.firstOrNull { it.id == nextActiveId }
            _address.value = next?.address
            _activeIdentity.value = next
        }
        refreshLive()
        walletDataStore.saveIdentityState(IdentityState(_identities.value, _address.value))
        diag("FORGET: identity $address removed; active=${_address.value ?: "none"}")
        return Result.success(Unit)
    }

    override suspend fun signTypedDataV4(json: String, chainId: Long): Result<String> = withContext(ioDispatcher) {
        if (config.projectId.isBlank()) {
            return@withContext Result.failure(WalletError.AppKitNotInitialized)
        }
        val activeAddr = address.value
        val liveAddr = try { AppKit.getAccount()?.address } catch (_: Exception) { null }
        if (activeAddr == null || !IdentityAddress.same(liveAddr, activeAddr)) {
            return@withContext Result.failure(WalletError.NotLive)
        }
        val addr = liveAddr ?: return@withContext Result.failure(WalletError.NotLive)
        if (json.isBlank()) return@withContext Result.failure(WalletError.InvalidSignatureFormat)

        // EIP-712 / WalletConnect: eth_signTypedData_v4 params MUST be [address, typedDataObject].
        // A stringified second element (escaped JSON string) is rejected by strict wallets:
        // Trust Wallet cannot process it, MetaMask answers -32603 "unexpected character '\'".
        // 4dfcd30 had the object form; 167b506 regressed it to the string form — restore objects.
        val typedData = try {
            org.json.JSONObject(json)
        } catch (e: Exception) {
            diagError("SIGN: typed-data JSON malformed", e)
            return@withContext Result.failure(WalletError.InvalidSignatureFormat)
        }
        val params = JSONArray().apply {
            put(addr)
            put(typedData)
        }.toString()

        val request = Request(
            method = "eth_signTypedData_v4",
            params = params,
            chainId = "eip155:$chainId"
        )

        try {
            diag("SIGN: requesting eth_signTypedData_v4 — addr=$addr chain=eip155:$chainId jsonBytes=${json.length}")
            val signature: String
            try {
                signature = kotlinx.coroutines.withTimeout(120_000) {
                    suspendCancellableCoroutine<String> { cont ->
                        // Install the response-capturing delegate BEFORE sending: setDelegate
                        // replaces the previous delegate, so installing first avoids losing a
                        // fast response, and the base delegate is restored in the finally below.
                        // Session-lifecycle events arriving mid-sign are handled like the base
                        // delegate so connect/session state stays in sync.
                        val forwarding = object : AppKit.ModalDelegate {
                            override fun onSessionApproved(s: Modal.Model.ApprovedSession) {
                                diag("SIGN DELEGATE: onSessionApproved")
                                val approvedAddr = try { AppKit.getAccount()?.address } catch (e: Exception) {
                                    diagError("SIGN DELEGATE: getAccount after approve failed", e); null
                                }
                                if (approvedAddr != null) {
                                    scope.launch {
                                        activate(approvedAddr)
                                    }
                                }
                            }
                            override fun onSessionRejected(s: Modal.Model.RejectedSession) { diag("SIGN DELEGATE: onSessionRejected") }
                            override fun onSessionUpdate(s: Modal.Model.UpdatedSession) {
                                val updatedAddr = try { AppKit.getAccount()?.address } catch (_: Exception) { null }
                                scope.launch { if (updatedAddr != null) activate(updatedAddr) else _isLive.value = false }
                            }
                            override fun onSessionEvent(s: Modal.Model.SessionEvent) {}
                            override fun onSessionExtend(s: Modal.Model.Session) {}
                            override fun onSessionDelete(s: Modal.Model.DeletedSession) {
                                diag("SIGN DELEGATE: onSessionDelete — clearing")
                                scope.launch {
                                    _isLive.value = false
                                }
                            }
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
                                        diag("SIGN: received JsonRpcError code=${result.code} msg=${result.message}")
                                        if (cont.isActive) cont.resumeWithException(Exception("${result.code}: ${result.message}"))
                                    }
                                }
                            }
                            override fun onProposalExpired(p: Modal.Model.ExpiredProposal) { diag("SIGN: onProposalExpired") }
                            override fun onRequestExpired(r: Modal.Model.ExpiredRequest) {
                                diag("SIGN: onRequestExpired")
                                if (cont.isActive) cont.resumeWithException(Exception("Request expired"))
                            }
                            override fun onConnectionStateChange(s: Modal.Model.ConnectionState) {
                                diag("SIGN DELEGATE: relay ${if (s.isAvailable) "online" else "offline"}")
                            }
                            override fun onError(error: Modal.Model.Error) {
                                diagError("SIGN: delegate onError", error.throwable)
                                if (cont.isActive) cont.resumeWithException(error.throwable)
                            }
                        }
                        try {
                            AppKit.setDelegate(forwarding)
                        } catch (e: Exception) {
                            diagError("SIGN: setDelegate threw", e)
                            if (cont.isActive) cont.resumeWithException(e)
                            return@suspendCancellableCoroutine
                        }
                        try {
                            AppKit.request(
                                request = request,
                                onSuccess = { sent ->
                                    val requestId = when (sent) {
                                        is SentRequestResult.WalletConnect -> sent.requestId
                                        is SentRequestResult.Coinbase -> Long.MIN_VALUE
                                    }
                                    diag("SIGN: request sent requestId=$requestId — waiting for wallet")
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
            } finally {
                try {
                    registerSessionDelegate()
                } catch (_: Exception) {}
                diag("SIGN: base delegate restored")
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
                else -> Result.failure(WalletError.SigningFailed(signFailureMessage(e, chainId)))
            }
        }
    }

    override suspend fun sendTransaction(to: String, data: String, chainId: Long, valueHex: String): Result<String> =
        withContext(Dispatchers.IO) {
            val addr = try {
                AppKit.getAccount()?.address
            } catch (e: Exception) {
                diagError("TX: getAccount threw", e)
                return@withContext Result.failure(WalletError.NoAddressReturned)
            }
            if (addr.isNullOrBlank()) return@withContext Result.failure(WalletError.NoAddressReturned)
            val activeTx = _address.value
            if (activeTx != null && !IdentityAddress.same(addr, activeTx)) {
                return@withContext Result.failure(WalletError.NotLive)
            }

            val tx = org.json.JSONObject().apply {
                put("from", addr)
                put("to", to)
                put("data", data)
                put("value", valueHex)
            }
            val params = JSONArray().apply { put(tx) }.toString()
            val request = Request(
                method = "eth_sendTransaction",
                params = params,
                chainId = "eip155:$chainId"
            )

            try {
                diag("TX: requesting eth_sendTransaction — to=${to.take(12)}… chain=eip155:$chainId")
                val hash: String = kotlinx.coroutines.withTimeout(180_000) {
                    suspendCancellableCoroutine<String> { cont ->
                        val forwarding = object : AppKit.ModalDelegate {
                            override fun onSessionApproved(s: Modal.Model.ApprovedSession) {}
                            override fun onSessionRejected(s: Modal.Model.RejectedSession) {}
                            override fun onSessionUpdate(s: Modal.Model.UpdatedSession) {}
                            override fun onSessionEvent(s: Modal.Model.SessionEvent) {}
                            override fun onSessionExtend(s: Modal.Model.Session) {}
                            override fun onSessionDelete(s: Modal.Model.DeletedSession) {
                                scope.launch {
                                    _isLive.value = false
                                }
                            }
                            override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {
                                when (val result = response.result) {
                                    is Modal.Model.JsonRpcResponse.JsonRpcResult -> {
                                        val h = result.result as? String
                                        if (h != null && cont.isActive) cont.resume(h)
                                    }
                                    is Modal.Model.JsonRpcResponse.JsonRpcError -> {
                                        if (cont.isActive) {
                                            cont.resumeWithException(Exception("${result.code}: ${result.message}"))
                                        }
                                    }
                                }
                            }
                            override fun onProposalExpired(p: Modal.Model.ExpiredProposal) {}
                            override fun onRequestExpired(r: Modal.Model.ExpiredRequest) {
                                if (cont.isActive) cont.resumeWithException(Exception("Request expired"))
                            }
                            override fun onConnectionStateChange(s: Modal.Model.ConnectionState) {}
                            override fun onError(error: Modal.Model.Error) {
                                diagError("TX: delegate onError", error.throwable)
                                if (cont.isActive) cont.resumeWithException(error.throwable)
                            }
                        }
                        try {
                            AppKit.setDelegate(forwarding)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resumeWithException(e)
                            return@suspendCancellableCoroutine
                        }
                        try {
                            AppKit.request(
                                request = request,
                                onSuccess = { sent ->
                                    diag("TX: request sent — waiting for wallet approval")
                                },
                                onError = { err ->
                                    diagError("TX: AppKit.request onError", err)
                                    if (cont.isActive) cont.resumeWithException(err)
                                }
                            )
                        } catch (e: Exception) {
                            diagError("TX: AppKit.request threw", e)
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                }
                try {
                    registerSessionDelegate()
                } catch (_: Exception) {}
                if (!hash.startsWith("0x") || hash.length != 66) {
                    return@withContext Result.failure(WalletError.TransactionFailed("Bad tx hash: $hash"))
                }
                diag("TX: sent ${hash.take(12)}…")
                Result.success(hash)
            } catch (e: Exception) {
                try {
                    registerSessionDelegate()
                } catch (_: Exception) {}
                when (e) {
                    is WalletError -> Result.failure(e)
                    else -> Result.failure(WalletError.TransactionFailed(e.message ?: "Unknown error"))
                }
            }
        }
}

sealed class WalletError : Exception() {
    object AppKitNotInitialized : WalletError() {
        override val message: String get() = "AppKit not initialized — set wallet.projectId in local.properties"
    }
    data class UnknownIdentity(val address: String) : WalletError() {
        override val message: String get() = "Unknown identity: $address"
    }
    object NotLive : WalletError() {
        override val message: String get() = "This identity is not connected right now — reconnect it to sign"
    }
    object NoAddressReturned : WalletError() {
        override val message: String get() = "No wallet address — connect a wallet first"
    }
    data class ConnectFailed(override val message: String) : WalletError()
    object InvalidSignatureFormat : WalletError() {
        override val message: String get() = "Invalid signature format — expected 0x + 130 hex chars"
    }
    data class SigningFailed(override val message: String) : WalletError()
    data class TransactionFailed(override val message: String) : WalletError()
}

/**
 * Pure: the SDK preset chains plus Sepolia (absent from the presets), exactly once.
 * Approved chains are fixed at session approval, so sessions connected before
 * this existed must disconnect + reconnect to gain Sepolia.
 */
internal fun withSepolia(
    preset: List<com.reown.appkit.client.Modal.Model.Chain>,
): List<com.reown.appkit.client.Modal.Model.Chain> {
    if (preset.any { it.chainNamespace == "eip155" && it.chainReference == "11155111" }) return preset
    val mainnet = preset.firstOrNull { it.chainNamespace == "eip155" && it.chainReference == "1" }
        ?: return preset
    val sepolia = mainnet.copy(
        chainName = "Sepolia",
        chainReference = "11155111",
        rpcUrl = "https://rpc.sepolia.org",
        blockExplorerUrl = "https://sepolia.etherscan.io",
    )
    return preset + sepolia
}

/**
 * Pure: reader-facing copy for a failed signature request.
 *
 * A timeout means the wallet never answered — usually its app is closed or
 * the request went to a chain the wallet isn't on — so it names the chain
 * and the fix instead of leaking `Timed out waiting for 120000 ms`.
 */
internal fun signFailureMessage(cause: Exception, chainId: Long): String =
    if (cause is kotlinx.coroutines.TimeoutCancellationException) {
        "Your wallet didn't answer the signature request within 2 minutes. " +
            "Open your wallet app, approve the request on eip155:$chainId, and try again."
    } else {
        cause.message ?: "Unknown error"
    }
