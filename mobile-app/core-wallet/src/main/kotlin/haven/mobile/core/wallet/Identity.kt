package haven.mobile.core.wallet

import org.json.JSONArray
import org.json.JSONObject

/**
 * One identity = one wallet. [address] is stored as the wallet returned it
 * (checksummed); [id] is the lowercase form every on-disk partition uses
 * (cache dirs, mirror DB, epoch-key file, settings namespace), matching the
 * lowercasing those stores already applied to a single wallet.
 */
data class WalletIdentity(
    val address: String,
    val label: String = "",
    val connector: String = "",
    val addedAtMs: Long = 0L,
    val lastActiveAtMs: Long = 0L,
) {
    val id: String get() = address.lowercase()

    /** Label shown in the switcher: the user's rename, else a short address. */
    val displayLabel: String get() = label.ifBlank { shortAddress(address) }

    companion object {
        fun shortAddress(address: String): String =
            if (address.length > 12) "${address.take(6)}…${address.takeLast(4)}" else address

        /** Deterministic avatar hue (0–359) from the address — no stored colour. */
        fun hueFor(address: String): Int =
            (address.lowercase().hashCode() and Int.MAX_VALUE) % 360
    }
}

/** Address helpers shared by the registry, the session and the scoped stores. */
object IdentityAddress {
    fun normalize(address: String): String = address.trim().lowercase()
    fun isValid(address: String): Boolean =
        address.trim().let { it.startsWith("0x") && it.length == 42 && it.drop(2).all { c -> c.isDigit() || c.lowercaseChar() in 'a'..'f' } }
    fun same(a: String?, b: String?): Boolean =
        a != null && b != null && normalize(a) == normalize(b)
}

/**
 * Pure registry operations over an identity list + active pointer.
 *
 * Kept free of DataStore/Android so the switch contract pins on the JVM:
 * switching only moves the active pointer — no entry is removed or mutated
 * except its `lastActiveAtMs` — and forgetting one identity activates the
 * most recently used remaining one.
 */
object IdentityRegistry {
    fun upsert(
        identities: List<WalletIdentity>,
        address: String,
        connector: String = "",
        nowMs: Long = 0L,
    ): List<WalletIdentity> {
        val existing = identities.firstOrNull { IdentityAddress.same(it.address, address) }
        return if (existing == null) {
            identities + WalletIdentity(
                address = address.trim(),
                connector = connector,
                addedAtMs = nowMs,
                lastActiveAtMs = nowMs,
            )
        } else {
            identities.map {
                if (it.id == IdentityAddress.normalize(address)) {
                    it.copy(
                        address = address.trim(),
                        connector = connector.ifBlank { it.connector },
                        lastActiveAtMs = nowMs,
                    )
                } else it
            }
        }
    }

    fun rename(identities: List<WalletIdentity>, address: String, label: String): List<WalletIdentity> =
        identities.map {
            if (IdentityAddress.same(it.address, address)) it.copy(label = label.trim()) else it
        }

    fun touch(identities: List<WalletIdentity>, address: String, nowMs: Long): List<WalletIdentity> =
        identities.map {
            if (IdentityAddress.same(it.address, address)) it.copy(lastActiveAtMs = nowMs) else it
        }

    /** Removes [address]; returns the remaining list and the next active id (or null). */
    fun forget(
        identities: List<WalletIdentity>,
        activeId: String?,
        address: String,
    ): Pair<List<WalletIdentity>, String?> {
        val remaining = identities.filterNot { IdentityAddress.same(it.address, address) }
        val nextActive = when {
            remaining.isEmpty() -> null
            activeId != null && remaining.any { it.id == activeId } -> activeId
            else -> remaining.maxByOrNull { it.lastActiveAtMs }?.id
        }
        return remaining to nextActive
    }

    /** One-entry registry migrated from the legacy single-wallet record. */
    fun migrateLegacy(address: String?, connector: String = "", nowMs: Long = 0L): List<WalletIdentity> =
        address?.takeIf { it.isNotBlank() }?.let {
            listOf(WalletIdentity(address = it.trim(), connector = connector, addedAtMs = nowMs, lastActiveAtMs = nowMs))
        } ?: emptyList()
}

/** JSON codec for the persisted registry. Unreadable rows degrade to empty, never throw. */
object IdentitiesCodec {
    fun encode(identities: List<WalletIdentity>): String {
        val arr = JSONArray()
        identities.forEach { identity ->
            arr.put(
                JSONObject()
                    .put("address", identity.address)
                    .put("label", identity.label)
                    .put("connector", identity.connector)
                    .put("addedAtMs", identity.addedAtMs)
                    .put("lastActiveAtMs", identity.lastActiveAtMs),
            )
        }
        return arr.toString()
    }

    fun decode(json: String?): List<WalletIdentity> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<WalletIdentity>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val address = obj.optString("address", "").takeIf { it.isNotBlank() } ?: continue
            if (out.any { IdentityAddress.same(it.address, address) }) continue
            out += WalletIdentity(
                address = address,
                label = obj.optString("label", ""),
                connector = obj.optString("connector", ""),
                addedAtMs = obj.optLong("addedAtMs", 0L),
                lastActiveAtMs = obj.optLong("lastActiveAtMs", 0L),
            )
        }
        return out
    }
}
