package haven.mobile.feature.watch

import haven.mobile.core.domain.HavenChain
import haven.mobile.core.wallet.WalletSession
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal in-app Mint Club trading for Arkiv-associated gate tokens.
 *
 * Scope is deliberately narrow: this only ever trades the gate token on the
 * [DripPump] sheet — i.e. tokens already associated with both Mint Club and an
 * Arkiv premiere. No token picker, no Uniswap, no new screens.
 *
 * Execution goes through the connected wallet ([WalletSession.sendTransaction]);
 * quotes are read-only `eth_call`s against public RPCs. Calldata layouts mirror
 * the `mint-club-kotlin-sdk` `BondContract.Fn` definitions; selectors were
 * derived from the canonical signatures with keccak-256.
 */
object MintClubTrade {

    /** `MCV2_Bond` per chain. Mainnets share one deployment; Sepolia has its own. */
    fun bondAddress(chain: HavenChain): String = when (chain) {
        HavenChain.ETH_SEPOLIA -> "0x8dce343A86Aa950d539eeE0e166AFfd0Ef515C0c"
        else -> "0xc5a076cad94176c2996B32d8466Be1cE757FAa27"
    }

    /** First public RPC per chain (matches the SDK's built-in lists). */
    fun rpcUrl(chain: HavenChain): String = when (chain) {
        HavenChain.ETH_MAINNET -> "https://ethereum-rpc.publicnode.com"
        HavenChain.BASE_MAINNET -> "https://base-rpc.publicnode.com"
        HavenChain.ARBITRUM_ONE -> "https://arbitrum-one.publicnode.com"
        HavenChain.OPTIMISM_MAINNET -> "https://optimism-rpc.publicnode.com"
        HavenChain.ETH_SEPOLIA -> "https://ethereum-sepolia-rpc.publicnode.com"
    }

    // Selectors: keccak(sig)[0..4].
    const val SEL_MINT = "f74bfe8e" // mint(address,uint256,uint256,address)
    const val SEL_BURN = "5a4d5311" // burn(address,uint256,uint256,address)
    const val SEL_APPROVE = "095ea7b3" // approve(address,uint256)
    const val SEL_QUOTE_BUY = "76a9864b" // getReserveForToken(address,uint256)
    const val SEL_QUOTE_SELL = "c9cb204b" // getRefundForTokens(address,uint256)
    const val SEL_ALLOWANCE = "dd62ed3e" // allowance(address,address)
    const val SEL_DECIMALS = "313ce567" // decimals()
    const val SEL_TOKEN_BOND = "d9fe0eae" // tokenBond(address)

    const val DEFAULT_SLIPPAGE_BPS = 100
    val MAX_UINT: BigInteger = BigInteger.valueOf(2).pow(256).subtract(BigInteger.ONE)

    fun encAddress(value: String): String {
        val clean = value.trim().removePrefix("0x").lowercase()
        require(clean.length == 40 && clean.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Not an address: $value"
        }
        return clean.padStart(64, '0')
    }

    fun encUint(value: BigInteger): String {
        require(value.signum() >= 0) { "Negative uint: $value" }
        return value.toString(16).padStart(64, '0')
    }

    /** `mint(token, amount, maxReserve, recipient)` calldata. */
    fun mintCalldata(token: String, amount: BigInteger, maxReserve: BigInteger, recipient: String): String =
        "0x$SEL_MINT${encAddress(token)}${encUint(amount)}${encUint(maxReserve)}${encAddress(recipient)}"

    /** `burn(token, amount, minRefund, recipient)` calldata. */
    fun burnCalldata(token: String, amount: BigInteger, minRefund: BigInteger, recipient: String): String =
        "0x$SEL_BURN${encAddress(token)}${encUint(amount)}${encUint(minRefund)}${encAddress(recipient)}"

    /** `approve(spender, amount)` calldata. */
    fun approveCalldata(spender: String, amount: BigInteger): String =
        "0x$SEL_APPROVE${encAddress(spender)}${encUint(amount)}"

    /** `allowance(owner, spender)` call data for `eth_call`. */
    fun allowanceCalldata(owner: String, spender: String): String =
        "0x$SEL_ALLOWANCE${encAddress(owner)}${encAddress(spender)}"

    fun buyQuoteCalldata(token: String, amount: BigInteger): String =
        "0x$SEL_QUOTE_BUY${encAddress(token)}${encUint(amount)}"

    fun tokenBondCalldata(token: String): String =
        "0x$SEL_TOKEN_BOND${encAddress(token)}"

    /** Reserve token of [token]'s curve (word 4 of `tokenBond`). Null when unlisted. */
    suspend fun reserveToken(chain: HavenChain, token: String): String? {
        val raw = ethCall(chain, bondAddress(chain), tokenBondCalldata(token)) ?: return null
        val clean = raw.removePrefix("0x")
        if (clean.length < 192) return null
        val word = runCatching { clean.substring(128, 192).takeLast(40) }.getOrNull() ?: return null
        if (!word.matches(Regex("[0-9a-fA-F]{40}"))) return null
        val addr = "0x$word"
        if (addr.equals("0x0000000000000000000000000000000000000000", ignoreCase = true)) return null
        return addr
    }

    fun sellQuoteCalldata(token: String, amount: BigInteger): String =
        "0x$SEL_QUOTE_SELL${encAddress(token)}${encUint(amount)}"

    /** Parses "1.5" into base units for [decimals]. Null when malformed or non-positive. */
    fun parseAmount(text: String, decimals: Int = 18): BigInteger? {
        val clean = text.trim().replace(",", ".")
        if (clean.isEmpty()) return null
        val bd = runCatching { BigDecimal(clean) }.getOrNull() ?: return null
        if (bd.signum() <= 0) return null
        if (bd.scale() > decimals) return null
        return try {
            bd.movePointRight(decimals).toBigIntegerExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** Adds [slippageBps] headroom (buy side: max spend). */
    fun maxWithSlippage(quoted: BigInteger, slippageBps: Int = DEFAULT_SLIPPAGE_BPS): BigInteger =
        quoted.add(quoted.multiply(slippageBps.toBigInteger()).divide(10_000.toBigInteger()))

    /** Subtracts [slippageBps] floor (sell side: min refund). */
    fun minWithSlippage(quoted: BigInteger, slippageBps: Int = DEFAULT_SLIPPAGE_BPS): BigInteger {
        val cut = quoted.multiply(slippageBps.toBigInteger()).divide(10_000.toBigInteger())
        return (quoted.subtract(cut)).max(BigInteger.ZERO)
    }

    /** Decodes a `(uint256, uint256)` eth_call result into the two words. */
    fun decodeUintPair(hex: String): Pair<BigInteger, BigInteger>? {
        val clean = hex.trim().removePrefix("0x")
        if (clean.length < 128) return null
        return try {
            Pair(BigInteger(clean.substring(0, 64), 16), BigInteger(clean.substring(64, 128), 16))
        } catch (_: Exception) {
            null
        }
    }

    /** Human rendering of base-unit [amount] with [decimals], trimmed to 6dp. */
    fun formatUnits(amount: BigInteger, decimals: Int = 18): String {
        if (decimals == 0) return amount.toString()
        val s = amount.toString().padStart(decimals + 1, '0')
        val whole = s.dropLast(decimals)
        val frac = s.takeLast(decimals).trimEnd('0').take(6)
        return if (frac.isEmpty()) whole else "$whole.$frac"
    }

    private val http = OkHttpClient()
    private val jsonMedia = "application/json".toMediaType()

    /** Raw `eth_call` returning the result hex, or null on any failure. */
    suspend fun ethCall(chain: HavenChain, to: String, data: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "eth_call")
                .put("params", JSONArray().put(JSONObject().put("to", to).put("data", data)).put("latest"))
                .toString()
                .toRequestBody(jsonMedia)
            val req = Request.Builder().url(rpcUrl(chain)).post(body).build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val text = resp.body?.string() ?: return@runCatching null
                JSONObject(text).optString("result", null)?.takeIf { it.startsWith("0x") }
            }
        }.getOrNull()
    }

    /** Reserve cost (incl. royalty) for minting [amount] of [token]. */
    suspend fun quoteBuy(chain: HavenChain, token: String, amount: BigInteger): BigInteger? {
        val raw = ethCall(chain, bondAddress(chain), buyQuoteCalldata(token, amount)) ?: return null
        return decodeUintPair(raw)?.first
    }

    /** Reserve refund (after royalty) for burning [amount] of [token]. */
    suspend fun quoteSell(chain: HavenChain, token: String, amount: BigInteger): BigInteger? {
        val raw = ethCall(chain, bondAddress(chain), sellQuoteCalldata(token, amount)) ?: return null
        return decodeUintPair(raw)?.first
    }

    /** ERC-20 `allowance(owner, spender)` on [erc20]. */
    suspend fun allowance(chain: HavenChain, erc20: String, owner: String, spender: String): BigInteger? {
        val raw = ethCall(chain, erc20, allowanceCalldata(owner, spender)) ?: return null
        val clean = raw.removePrefix("0x")
        if (clean.length < 64) return null
        return runCatching { BigInteger(clean.takeLast(64), 16) }.getOrNull()
    }

    /**
     * Executes a buy: approves the reserve token when needed, then mints.
     * Returns the mint tx hash or a failure. Each wallet approval is a separate
     * confirmation — the user sees exactly what they sign.
     */
    suspend fun executeBuy(
        wallet: WalletSession,
        chain: HavenChain,
        token: String,
        me: String,
        amount: BigInteger,
        slippageBps: Int = DEFAULT_SLIPPAGE_BPS,
    ): Result<String> {
        val bond = bondAddress(chain)
        val cost = quoteBuy(chain, token, amount)
            ?: return Result.failure(IllegalStateException("No quote — is this token on Mint Club?"))
        val maxReserve = maxWithSlippage(cost, slippageBps)
        val reserve = reserveToken(chain, token)
            ?: return Result.failure(IllegalStateException("Token has no Mint Club curve here"))
        val allowed = allowance(chain, reserve, me, bond) ?: BigInteger.ZERO
        if (allowed < maxReserve) {
            val approval = wallet.sendTransaction(reserve, approveCalldata(bond, MAX_UINT), chain.chainId)
            if (approval.isFailure) return approval
        }
        return wallet.sendTransaction(bond, mintCalldata(token, amount, maxReserve, me), chain.chainId)
    }

    /** Executes a sell: approves the bond to burn when needed, then burns. */
    suspend fun executeSell(
        wallet: WalletSession,
        chain: HavenChain,
        token: String,
        me: String,
        amount: BigInteger,
        slippageBps: Int = DEFAULT_SLIPPAGE_BPS,
    ): Result<String> {
        val bond = bondAddress(chain)
        val refund = quoteSell(chain, token, amount)
            ?: return Result.failure(IllegalStateException("No quote — is this token on Mint Club?"))
        val allowed = allowance(chain, token, me, bond) ?: BigInteger.ZERO
        if (allowed < amount) {
            val approval = wallet.sendTransaction(token, approveCalldata(bond, MAX_UINT), chain.chainId)
            if (approval.isFailure) return approval
        }
        return wallet.sendTransaction(
            bond,
            burnCalldata(token, amount, minWithSlippage(refund, slippageBps), me),
            chain.chainId,
        )
    }
}
