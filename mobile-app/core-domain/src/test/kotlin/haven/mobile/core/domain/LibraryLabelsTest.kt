package haven.mobile.core.domain

import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LibraryLabelsTest {
    @Test
    fun `own upload by creator address reads My contribution`() {
        val item = item(creator = "0xABC", owner = "0xother")
        assertEquals("My contribution", item.provenanceLabel("0xabc"))
    }

    @Test
    fun `own upload falls back to owner address`() {
        val item = item(creator = null, owner = "0xabc")
        assertEquals("My contribution", item.provenanceLabel("0xABC"))
    }

    @Test
    fun `everything else is The pool`() {
        val item = item(creator = "0xother", owner = "0xother")
        assertEquals("The pool", item.provenanceLabel("0xabc"))
        assertEquals("The pool", item.provenanceLabel(null))
    }

    @Test
    fun `shade ladder answers who can open this`() {
        assertEquals("Everyone", item(encrypted = false).shadeLabel())
        assertEquals("Members", item(gate = TokenGate("base", "0xtoken", 1.0)).shadeLabel())
        assertEquals(
            "Collectors",
            item(gate = TokenGate("base", "0xnft", 1.0, TokenStandard.ERC721)).shadeLabel(),
        )
        assertEquals(
            "Collectors",
            item(gate = TokenGate("base", "0xnft", 1.0, TokenStandard.ERC1155)).shadeLabel(),
        )
        assertEquals("Only you", item(encrypted = true, gate = null).shadeLabel())
    }

    @Test
    fun `caption order is provenance first shade second`() {
        val item = item(creator = "0xabc", gate = TokenGate("base", "0xtoken", 1.0))
        assertEquals("My contribution · Members", item.captionLine("0xabc"))
        assertEquals("The pool · Members", item.captionLine("0xother"))
    }

    @Test
    fun `creator without address is not yet verified`() {
        assertFalse(item(creator = null).isCreatorVerifiable())
        assertTrue(item(creator = "0xabc").isCreatorVerifiable())
    }

    @Test
    fun `gate requirement names the token`() {
        assertEquals(
            "Requires the 0xtoken token",
            item(gate = TokenGate("base", "0xtoken", 1.0)).gateRequirementLine(),
        )
        assertEquals("Requires the gate token", item(encrypted = true).gateRequirementLine())
    }

    // ── Pre-signature disclosure: the trust contract of an unknown app ──────
    // These pin the promises the disclosure makes, so a copy edit cannot
    // quietly drop one.

    @Test
    fun `disclosure headline leads with intent, not the wallet mechanism`() {
        assertFalse(signDisclosureHeadline(firstTime = true).contains("wallet", ignoreCase = true))
        assertFalse(signDisclosureHeadline(firstTime = false).contains("wallet", ignoreCase = true))
        assertEquals("Open this item", signDisclosureHeadline(firstTime = false))
    }

    @Test
    fun `disclosure guarantees promise no transaction, no gas, no seed phrase`() {
        val joined = SIGN_DISCLOSURE_GUARANTEES.joinToString(" ")
        assertTrue(SIGN_DISCLOSURE_GUARANTEES.first().contains("hold", ignoreCase = true))
        assertTrue(joined.contains("No transaction"), joined)
        assertTrue(joined.contains("no gas", ignoreCase = true), joined)
        assertTrue(joined.contains("seed phrase", ignoreCase = true), joined)
    }

    @Test
    fun `disclosure session note promises reopening will not ask again`() {
        assertTrue(SIGN_DISCLOSURE_SESSION_NOTE.contains("reopening it later", ignoreCase = true))
        assertTrue(SIGN_DISCLOSURE_SESSION_NOTE.contains("won't ask", ignoreCase = true))
        assertTrue(SIGN_DISCLOSURE_SESSION_NOTE.contains("disconnect", ignoreCase = true))
    }

    @Test
    fun `disclosure why body tells the reader when to reject a wallet prompt`() {
        assertTrue(SIGN_DISCLOSURE_WHY_BODY.contains("reject", ignoreCase = true))
        assertTrue(SIGN_DISCLOSURE_WHY_BODY.contains("cannot move", ignoreCase = true))
    }

    @Test
    fun `batch disclosure states the count and carries the same guarantees`() {
        val body = signDisclosureBatchBody(itemCount = 7)
        assertTrue(body.contains("7"), body)
        assertTrue(body.contains("once per gate", ignoreCase = true), body)
        assertTrue(body.contains("seed phrase", ignoreCase = true), body)
        assertTrue(body.contains("No transaction"), body)
    }

    @Test
    fun `a read-only held check is stated, an unknown one is not claimed`() {
        assertTrue(signDisclosureGateLine(gateHeld = true)?.contains("no signature") == true)
        assertEquals(null, signDisclosureGateLine(gateHeld = false))
        assertEquals(null, signDisclosureGateLine(gateHeld = null))
    }

    private fun item(
        creator: String? = "0xother",
        owner: String = "0xother",
        encrypted: Boolean = true,
        gate: TokenGate? = null,
    ) = MediaItem(
        id = "id",
        kind = MediaKind.VIDEO,
        owner = owner,
        title = "title",
        description = null,
        mimeType = null,
        fileExtension = null,
        filenameHint = null,
        sizeBytes = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        createdAtBlock = null,
        expiresAtBlock = null,
        pieceRef = null,
        filecoinCid = null,
        encryptedCid = null,
        cidHash = null,
        gate = gate,
        isEncrypted = encrypted,
        encryptionMetadata = null,
        cidEncryptionMetadata = null,
        attestation = null,
        arkivStatus = ArkivStatus.FRESH,
        contentCacheStatus = ContentCacheStatus.CACHED,
        lastAccessedAt = null,
        creatorAddress = creator,
    )
}
