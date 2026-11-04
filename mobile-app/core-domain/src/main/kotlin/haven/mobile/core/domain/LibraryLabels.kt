package haven.mobile.core.domain

/**
 * Library labeling language — the single source of truth for the wording decided in
 * `planning/library-labels.md`. Wording only: no layout, no badges, no sections.
 *
 * Two facts, always in this order, joined by `·`: provenance first, shade second —
 * e.g. `My contribution · Members`. These exact strings are the only words used for
 * these facts anywhere in the app (cards, detail screen, filters, empty states).
 */
const val PROVENANCE_SELF = "My contribution"
const val PROVENANCE_POOL = "The pool"

const val SHADE_EVERYONE = "Everyone"
const val SHADE_MEMBERS = "Members"
const val SHADE_COLLECTORS = "Collectors"
const val SHADE_ONLY_YOU = "Only you"

/** Detail sheet, creator address absent — absence of proof, never suspicion. */
const val CREATOR_UNVERIFIABLE = "Contribution not yet verified"

/** Detail sheet, gate closed for this viewer — describes the viewer's state. */
const val GATE_FAILED_HEADLINE = "You don't hold this gate's token"

/**
 * Pre-signature disclosure headline, before any gate verdict exists.
 *
 * This screen appears on every first unlock — including gates that stand open —
 * so it must never claim the gate is closed. [GATE_FAILED_HEADLINE] stays
 * reserved for an actual failed gate check.
 *
 * The headline names the reader's intent, never the mechanism: "wallet" is
 * Haven's implementation detail, and leading with it asks for trust in the
 * scariest object on the phone before any value has been delivered. The full
 * explanation is shown once (first time); after that the screen is a short
 * confirmation, because repeating the lecture teaches readers to tap through
 * wallet prompts without reading them.
 */
const val SIGN_DISCLOSURE_HEADLINE = "Prove it's you to open this"

/** Disclosure headline once the full explanation has been seen and accepted. */
const val SIGN_DISCLOSURE_HEADLINE_RETURNING = "Open this item"

fun signDisclosureHeadline(firstTime: Boolean): String =
    if (firstTime) SIGN_DISCLOSURE_HEADLINE else SIGN_DISCLOSURE_HEADLINE_RETURNING

/**
 * What the signature does and does not do, as checkable promises.
 *
 * Order matters: what it proves first, then the two things a frightened
 * reader is actually asking (will this cost me money, can this drain me),
 * then the anti-phishing line an unknown app must say out loud because it
 * has no brand to say it implicitly.
 */
val SIGN_DISCLOSURE_GUARANTEES: List<String> = listOf(
    "Proves you hold the token this item requires",
    "No transaction and no gas fee — nothing leaves your wallet",
    "Haven will never ask for your seed phrase",
)

/**
 * The honest scope of "sign once": this item's key is kept for the session.
 * Items sharing a gate epoch may open without signing again, but that is the
 * key cache's doing, not a promise this screen makes per item.
 */
const val SIGN_DISCLOSURE_SESSION_NOTE =
    "Haven keeps this item's key on this device for this session, so reopening it won't ask you to sign again."

/** The long answer, behind "Why am I signing?" — present, never in the way. */
const val SIGN_DISCLOSURE_WHY_BODY =
    "This item is encrypted. Its key is released only to a wallet that holds the token it " +
        "requires, and a signature is how you prove that wallet is yours: your wallet signs a " +
        "message, Haven's key service checks it, and the key is derived on this device.\n\n" +
        "That signature cannot move funds, approve spending, or change anything on-chain — it " +
        "is a proof, not a payment. Before you sign, check in your wallet that the request is a " +
        "message signature from Haven and that it shows no amount and no gas fee.\n\n" +
        "If a wallet prompt ever shows a transaction, asks for a fee, or asks for your seed " +
        "phrase, stop and reject it — that request did not come from Haven."

/**
 * Stated only when a read-only balance check (no signature) already proved
 * the connected wallet holds the gate token. An unreadable or unheld gate
 * returns null: absence of proof is never presented as a failed gate, and a
 * held gate is never claimed without the check. The signed unlock remains
 * the authority either way.
 */
/**
 * Batch variant of the disclosure (library selection, community "unlock
 * all"): the same guarantees, plus the two facts that make a batch legible —
 * how much is being asked for, and that sharing a gate means sharing a
 * signature. Used as a confirmation-dialog body before anything signs.
 */
fun signDisclosureBatchBody(itemCount: Int): String =
    "Your wallet will be asked to sign for $itemCount item${if (itemCount == 1) "" else "s"} — " +
        "once per gate, however many of them share it.\n\n" +
        SIGN_DISCLOSURE_GUARANTEES.joinToString("\n") { "• $it" }

fun signDisclosureGateLine(gateHeld: Boolean?): String? =
    if (gateHeld == true) {
        "This wallet already holds the token this item requires — checked read-only, with no signature."
    } else {
        null
    }

/** Detail sheet, gate closed — always names what would open it. */
fun gateRequirement(tokenName: String): String = "Requires the $tokenName token"

/**
 * Is this the connected wallet's own upload?
 *
 * Until the DAO contribution-token contract exists, this falls back to comparing the
 * entity's on-chain creator address against the connected wallet, then the owner
 * address. Comparison is case-insensitive; a null or blank wallet is never self.
 */
fun MediaItem.isSelfUpload(walletAddress: String?): Boolean {
    val wallet = walletAddress?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
    val creator = creatorAddress?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    if (creator != null) return creator == wallet
    return owner.trim().lowercase() == wallet
}

/** Provenance fact: [PROVENANCE_SELF] for own uploads, [PROVENANCE_POOL] for the rest. */
fun MediaItem.provenanceLabel(walletAddress: String?): String =
    if (isSelfUpload(walletAddress)) PROVENANCE_SELF else PROVENANCE_POOL

/**
 * Shade fact — answers *who can open this?*
 *
 * Ungated items are [SHADE_EVERYONE]. Gated items follow the token standard:
 * fungible gates are [SHADE_MEMBERS], NFT gates [SHADE_COLLECTORS]. A gated item
 * with no parsable gate record is [SHADE_ONLY_YOU].
 */
fun MediaItem.shadeLabel(): String {
    if (!isEncrypted) return SHADE_EVERYONE
    return when (gate?.tokenStandard) {
        TokenStandard.ERC721, TokenStandard.ERC1155 -> SHADE_COLLECTORS
        TokenStandard.ERC20 -> SHADE_MEMBERS
        null -> SHADE_ONLY_YOU
    }
}

/** Caption grammar: provenance first, shade second, joined by `·` — never reordered. */
fun MediaItem.captionLine(walletAddress: String?): String =
    "${provenanceLabel(walletAddress)} · ${shadeLabel()}"

/**
 * Whether the contributor is checkable on-chain. False only when the entity carries
 * no creator address — the detail sheet then says [CREATOR_UNVERIFIABLE].
 */
fun MediaItem.isCreatorVerifiable(): Boolean = !creatorAddress.isNullOrBlank()

/** Short display name for the gate token, for [gateRequirement]. Never blank. */
fun MediaItem.gateTokenName(): String {
    val address = gate?.tokenAddress?.trim().orEmpty()
    if (address.isEmpty()) return "gate"
    return if (address.length <= 10) address else address.take(6) + "…" + address.takeLast(4)
}

/** `Requires the {name} token` for this item's gate. */
fun MediaItem.gateRequirementLine(): String = gateRequirement(gateTokenName())
