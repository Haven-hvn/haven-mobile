package haven.mobile.core.haven.aol

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * One persisted v3/v4 epoch unlock: the transport-encrypted VetKey plus the
 * secrets to unwrap it locally. The same triple the memory cache kept in
 * `EpochUnlock`, made explicit so the store can encode it without depending
 * on the `@Singleton` holder class.
 */
internal data class EpochUnlockData(
    val encryptedVetKey: ByteArray,
    val transportSecret: ByteArray,
    val verificationKey: ByteArray,
)

/**
 * Everything that identifies one epoch-bucket VetKey on the canister side,
 * plus the wallet whose access was checked. [epoch] is the *effective* epoch
 * (0 when threshold is 0), so records the canister collapses together share
 * one entry. [marketCapTarget] is null for v3 and set for v4 — different
 * stages of one drip are different keys.
 *
 * Top-level (was nested in `HavenAolImpl`) so the persistence layer can
 * encode it without reaching inside the holder.
 */
internal data class EpochGateKey(
    val protocol: Long,
    val chainVariant: String,
    val tokenAddress: String,
    val threshold: java.math.BigInteger,
    val epoch: java.math.BigInteger,
    val marketCapTarget: java.math.BigInteger?,
    val walletLower: String,
)

/**
 * Persists v3/v4 epoch unlocks across process restarts.
 *
 * The memory cache in `HavenAolImpl` is still the fast path; this store is
 * the durable copy, loaded lazily on first use per wallet per process.
 * `clearFor` deletes the wallet's file — disconnect wipes keys just like
 * before, only now it must wipe disk as well.
 */
internal interface EpochKeyStore {
    suspend fun loadFor(walletAddress: String): Map<EpochGateKey, EpochUnlockData>
    suspend fun saveFor(walletAddress: String, entries: Map<EpochGateKey, EpochUnlockData>)
    suspend fun clearFor(walletAddress: String)
}

/** Default for tests and any host that doesn't want persistence. */
internal class NoOpEpochKeyStore : EpochKeyStore {
    override suspend fun loadFor(walletAddress: String): Map<EpochGateKey, EpochUnlockData> = emptyMap()
    override suspend fun saveFor(walletAddress: String, entries: Map<EpochGateKey, EpochUnlockData>) = Unit
    override suspend fun clearFor(walletAddress: String) = Unit
}

/**
 * Totally non-persistent store for JVM unit tests: behaves like the real
 * thing across *instances* sharing one store (which is how a restart is
 * simulated) but never touches disk.
 */
internal class InMemoryEpochKeyStore : EpochKeyStore {
    private val lock = Mutex()
    private val byWallet = mutableMapOf<String, MutableMap<EpochGateKey, EpochUnlockData>>()

    override suspend fun loadFor(walletAddress: String): Map<EpochGateKey, EpochUnlockData> =
        lock.withLock {
            byWallet[walletLower(walletAddress)]?.mapValues { (_, v) ->
                EpochUnlockData(v.encryptedVetKey.copyOf(), v.transportSecret.copyOf(), v.verificationKey.copyOf())
            } ?: emptyMap()
        }

    override suspend fun saveFor(walletAddress: String, entries: Map<EpochGateKey, EpochUnlockData>) {
        lock.withLock {
            byWallet[walletLower(walletAddress)] = entries.mapValues { (_, v) ->
                EpochUnlockData(v.encryptedVetKey.copyOf(), v.transportSecret.copyOf(), v.verificationKey.copyOf())
            }.toMutableMap()
        }
    }

    override suspend fun clearFor(walletAddress: String) {
        lock.withLock { byWallet.remove(walletLower(walletAddress)) }
    }

    companion object {
        internal fun walletLower(walletAddress: String): String = walletAddress.lowercase()
    }
}

/**
 * `AndroidKeyStore`-backed persistence for epoch unlocks.
 *
 * One file per wallet under [Context.noBackupFilesDir], so `allowBackup=false`
 * plus `data_extraction_rules.xml` already exclude it from cloud backup and
 * device transfer. The file itself is AES-256-GCM encrypted with a
 * non-extractable Keystore key; the plaintext triple never touches disk.
 * A corrupt or unreadable file degrades to “no keys” rather than failing
 * opens — the next unlock just signs again.
 */
internal class KeystoreEpochStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : EpochKeyStore {

    override suspend fun loadFor(walletAddress: String): Map<EpochGateKey, EpochUnlockData> =
        withContext(Dispatchers.IO) {
            val file = fileFor(walletAddress) ?: return@withContext emptyMap()
            if (!file.isFile || file.length() == 0L) return@withContext emptyMap()
            runCatching {
                val sealedBytes = file.readBytes()
                val plain = decrypt(sealedBytes)
                decodeEntries(walletLower(walletAddress), plain)
            }.getOrDefault(emptyMap())
        }

    override suspend fun saveFor(walletAddress: String, entries: Map<EpochGateKey, EpochUnlockData>) {
        withContext(Dispatchers.IO) {
            val file = fileFor(walletAddress) ?: return@withContext
            if (entries.isEmpty()) {
                runCatching { file.delete() }
                return@withContext
            }
            val plain = encodeEntries(entries)
            runCatching {
                file.parentFile?.mkdirs()
                val sealedBytes = encrypt(plain)
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeBytes(sealedBytes)
                if (!tmp.renameTo(file)) {
                    // Cross-filesystem or locked: fall back to overwrite.
                    file.writeBytes(sealedBytes)
                    runCatching { tmp.delete() }
                }
            }
        }
    }

    override suspend fun clearFor(walletAddress: String) {
        withContext(Dispatchers.IO) {
            runCatching { fileFor(walletAddress)?.delete() }
        }
    }

    private fun fileFor(walletAddress: String): File? {
        val lower = walletLower(walletAddress)
        if (lower.isEmpty() || !lower.all { it in 'a'..'z' || it in '0'..'9' }) return null
        val base = context.noBackupFilesDir ?: context.filesDir
        return File(File(base, "epoch-keys"), "$lower.enc")
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey as SecretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plain)
    }

    private fun decrypt(sealed: ByteArray): ByteArray {
        require(sealed.size > 12) { "epoch key file too short" }
        val iv = sealed.copyOfRange(0, 12)
        val body = sealed.copyOfRange(12, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    companion object {
        private const val KEY_ALIAS = "haven-epoch-keys-v1"

        internal fun walletLower(walletAddress: String): String =
            walletAddress.trim().lowercase()

        /**
         * Wallet file -> entries JSON. Pure so the codec pins on the JVM.
         * All `BigInteger` fields are decimal strings (never JSON numbers).
         */
        internal fun encodeEntries(entries: Map<EpochGateKey, EpochUnlockData>): ByteArray {
            val arr = JSONArray()
            entries.forEach { (key, data) ->
                val obj = JSONObject()
                obj.put("protocol", key.protocol)
                obj.put("chain", key.chainVariant)
                obj.put("token", key.tokenAddress)
                obj.put("threshold", key.threshold.toString())
                obj.put("epoch", key.epoch.toString())
                if (key.marketCapTarget != null) obj.put("target", key.marketCapTarget.toString())
                obj.put("encryptedVetKey", b64(data.encryptedVetKey))
                obj.put("transportSecret", b64(data.transportSecret))
                obj.put("verificationKey", b64(data.verificationKey))
                arr.put(obj)
            }
            return arr.toString().toByteArray(Charsets.UTF_8)
        }

        internal fun decodeEntries(walletLower: String, plain: ByteArray): Map<EpochGateKey, EpochUnlockData> {
            val arr = runCatching { JSONArray(plain.toString(Charsets.UTF_8)) }.getOrNull() ?: return emptyMap()
            val out = mutableMapOf<EpochGateKey, EpochUnlockData>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val protocol = obj.optLong("protocol", -1)
                if (protocol != 3L && protocol != 4L) continue
                val chain = obj.optString("chain", null)?.takeIf { it.isNotBlank() } ?: continue
                val token = obj.optString("token", null)?.takeIf { it.isNotBlank() } ?: continue
                val threshold = obj.optString("threshold", null)?.let { runCatching { java.math.BigInteger(it) }.getOrNull() } ?: continue
                val epoch = obj.optString("epoch", null)?.let { runCatching { java.math.BigInteger(it) }.getOrNull() } ?: continue
                val target = if (obj.has("target") && !obj.isNull("target")) {
                    runCatching { java.math.BigInteger(obj.getString("target")) }.getOrNull()
                } else null
                val enc = obj.optString("encryptedVetKey", null)?.let { unb64(it) } ?: continue
                val secret = obj.optString("transportSecret", null)?.let { unb64(it) } ?: continue
                val ver = obj.optString("verificationKey", null)?.let { unb64(it) } ?: continue
                val key = EpochGateKey(protocol, chain, token, threshold, epoch, target, walletLower)
                out[key] = EpochUnlockData(enc, secret, ver)
            }
            return out
        }

        private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
        private fun unb64(text: String): ByteArray? =
            runCatching { Base64.getDecoder().decode(text) }.getOrNull()
    }
}
