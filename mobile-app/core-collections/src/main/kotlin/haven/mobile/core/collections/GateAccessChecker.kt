package haven.mobile.core.collections

import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.TokenGate
import haven.mobile.core.domain.havenChain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Does this address hold what a gate asks for?
 *
 * The whole of Haven's authorisation model, and the left-hand side of the intersection that decides
 * what a reader can see: **assets held at the address ∩ gate conditions stored on Arkiv**. Arkiv says
 * what each archive requires; this says what the wallet has; the overlap is the library.
 *
 * Implementation is one `eth_call` per gate, batched per chain and chains queried in parallel. No web3
 * library: `balanceOf(address)` is a four-byte selector plus one left-padded word, decoding is a hex
 * `BigInteger`, and JSON-RPC accepts a batch — a dependency here would add orders of magnitude more
 * bytes than the code it replaced.
 *
 * Read-only throughout. Balances are public, no signature is involved, nothing is stored. Proving
 * control of the address happens later and elsewhere (the access request the viewer signs).
 */
interface GateAccessChecker {
    /**
     * The subset of [gates] this address satisfies, keyed by [gateKey].
     *
     * A gate that could not be read is **absent rather than false**: an unreachable node is not a
     * statement about anybody's holdings, and treating it as "no" would hide a reader's own library.
     */
    suspend fun satisfied(
        walletAddress: String,
        gates: List<TokenGate>,
        chains: Set<HavenChain> = HavenChain.mainnets.toSet(),
    ): Set<String>
}

/** Stable identity for a gate: one chain, one contract. Thresholds vary per entity; the gate does not. */
fun gateKey(chain: HavenChain, tokenAddress: String): String =
    "${chain.aolVariant}:${tokenAddress.lowercase()}"

fun TokenGate.gateKeyOrNull(): String? = havenChain()?.let { gateKey(it, tokenAddress) }

@Singleton
internal class EvmGateAccessChecker @Inject constructor(
    private val endpoints: EvmEndpoints,
) : GateAccessChecker {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun satisfied(
        walletAddress: String,
        gates: List<TokenGate>,
        chains: Set<HavenChain>,
    ): Set<String> {
        if (gates.isEmpty()) return emptySet()
        val padded = padAddress(walletAddress) ?: return emptySet()

        // Group by chain: one batched round trip each, and a chain that is unreachable only costs its
        // own gates rather than the whole answer.
        val byChain = gates
            .mapNotNull { gate -> gate.havenChain()?.let { chain -> chain to gate } }
            .filter { (chain, _) -> chain in chains }
            .groupBy({ it.first }, { it.second })

        if (byChain.isEmpty()) return emptySet()

        return coroutineScope {
            byChain
                .map { (chain, chainGates) ->
                    async(Dispatchers.IO) {
                        runCatching { readChain(chain, padded, chainGates) }.getOrDefault(emptySet())
                    }
                }
                .fold(mutableSetOf<String>()) { acc, deferred -> acc.apply { addAll(deferred.await()) } }
        }
    }

    private fun readChain(
        chain: HavenChain,
        paddedAddress: String,
        gates: List<TokenGate>,
    ): Set<String> {
        // De-duplicate by contract: two entities gating on the same token are one balance read. Keep
        // the lowest threshold per contract, since satisfying that is what makes any of them readable.
        val byContract = gates
            .groupBy { it.tokenAddress.lowercase() }
            .mapValues { (_, group) -> group.minByOrNull { it.threshold } ?: group.first() }

        val calls = ArrayList<RpcCall>(byContract.size * 2)
        byContract.forEach { (contract, _) ->
            calls += RpcCall(contract, Field.BALANCE, contract, SELECTOR_BALANCE_OF + paddedAddress)
            // Decimals is asked of everything. A collection has none, so the call reverts, the result
            // is absent, and the threshold is then read as a count of items — which is correct. Asking
            // conditionally would mean guessing the standard first, and guessing it wrong is a wrong
            // answer about access.
            calls += RpcCall(contract, Field.DECIMALS, contract, SELECTOR_DECIMALS)
        }

        val batch = JSONArray()
        calls.forEachIndexed { index, rpcCall ->
            batch.put(
                JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", index)
                    put("method", "eth_call")
                    put(
                        "params",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("to", rpcCall.to)
                                    put("data", rpcCall.data)
                                },
                            )
                            put("latest")
                        },
                    )
                },
            )
        }

        val request = Request.Builder()
            .url(endpoints.rpcUrl(chain))
            .post(batch.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptySet()
            response.body?.string() ?: return emptySet()
        }

        val results = parseBatch(body)
        val balances = HashMap<String, BigInteger>()
        val decimals = HashMap<String, Int>()
        calls.forEachIndexed { index, rpcCall ->
            val value = results[index] ?: return@forEachIndexed
            when (rpcCall.field) {
                Field.BALANCE -> balances[rpcCall.contract] = value
                Field.DECIMALS -> decimals[rpcCall.contract] = value.toInt()
            }
        }

        val satisfied = mutableSetOf<String>()
        byContract.forEach { (contract, gate) ->
            val balance = balances[contract] ?: return@forEach
            val places = decimals[contract]
            val required = requiredUnits(gate.threshold, places)
            if (balance >= required) satisfied += gateKey(chain, contract)
        }
        return satisfied
    }

    /**
     * Threshold in base units.
     *
     * A collection reports no decimals, so a threshold of 1 means one item. A token quotes its
     * threshold in whole tokens while `balanceOf` answers in base units, so without decimals a gate
     * requiring 25,000 tokens is cleared by dust — a false positive on the only question this protocol
     * asks. When decimals cannot be read the requirement is scaled up out of reach rather than down.
     */
    private fun requiredUnits(threshold: Double, decimals: Int?): BigInteger {
        val whole = BigInteger.valueOf(threshold.toLong().coerceAtLeast(0))
        return when {
            decimals == null -> whole
            decimals == 0 -> whole
            else -> whole.multiply(BigInteger.TEN.pow(decimals))
        }
    }

    /** JSON-RPC batches may come back out of order, so results are keyed by request id. */
    private fun parseBatch(body: String): Map<Int, BigInteger> {
        val array = runCatching { JSONArray(body) }.getOrNull()
            ?: runCatching { JSONArray().put(JSONObject(body)) }.getOrNull()
            ?: return emptyMap()

        val out = HashMap<Int, BigInteger>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            if (entry.has("error")) continue
            val id = entry.optInt("id", -1)
            if (id < 0) continue
            val value = decodeWord(entry.optString("result", "")) ?: continue
            out[id] = value
        }
        return out
    }

    private fun decodeWord(raw: String): BigInteger? {
        if (raw.isEmpty() || raw == "0x") return null
        val hex = raw.removePrefix("0x")
        if (hex.isEmpty() || !hex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        return runCatching { BigInteger(hex, 16) }.getOrNull()
    }

    /** Left-pad a 20-byte address into a 32-byte ABI word. Null if it is not an address. */
    private fun padAddress(address: String): String? {
        val hex = address.trim().removePrefix("0x").lowercase()
        if (hex.length != ADDRESS_HEX_LENGTH) return null
        if (!hex.all { it.isDigit() || it in 'a'..'f' }) return null
        return hex.padStart(ABI_WORD_HEX_LENGTH, '0')
    }

    private data class RpcCall(
        val contract: String,
        val field: Field,
        val to: String,
        val data: String,
    )

    private enum class Field { BALANCE, DECIMALS }

    private companion object {
        /** `balanceOf(address)` — the same call for ERC-20 and ERC-721. */
        const val SELECTOR_BALANCE_OF = "0x70a08231"
        const val SELECTOR_DECIMALS = "0x313ce567"
        const val ADDRESS_HEX_LENGTH = 40
        const val ABI_WORD_HEX_LENGTH = 64
        const val TIMEOUT_SECONDS = 15L
    }
}
