package haven.mobile.core.attestation

import haven.mobile.core.domain.Attestation
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AttestationVerifierLogicTest {
    @Test
    fun `Attestation data class holds merkle proof`() {
        val att = Attestation(
            subject = "bafy123",
            signature = ByteArray(64) { 1 },
            signerKeyId = "key1",
            merkleProof = listOf(ByteArray(32) { 2 }),
            issuedAt = Instant.parse("2024-01-01T00:00:00Z")
        )
        assertEquals("bafy123", att.subject)
        assertNotNull(att.merkleProof)
        assertEquals(1, att.merkleProof!!.size)
    }

    @Test
    fun `Attestation without proof is unverified path`() {
        val att = Attestation(
            subject = "cid123",
            signature = ByteArray(64) { 0 },
            signerKeyId = "k",
            merkleProof = null,
            issuedAt = Instant.parse("2024-01-01T00:00:00Z")
        )
        assertNull(att.merkleProof)
    }
}
