# EIP-712 sign attempts — catalog (Trust Wallet / MetaMask via WalletConnect)

Payload under test (Debug fixture): `GateRequest(itemId, gate: Gate, nonce)`
with `EIP712Domain(name, version, chainId: uint256, verifyingContract: address)`.

## What has been tried (chronological)

1. `3e2a4cb` — fixture declares `GateRequest`/`Gate` in `types`.
   Did not work before because: `primaryType: GateRequest` had no matching
   definition and `EIP712Domain` was empty → wallets rejected with
   "invalid primary type definition". Fixed the shape; wallets then parsed it.

2. `82837ad` — send typed data as raw JSON **object** in params[1]:
   `["addr",{...}]` instead of stringified `["addr","{...}"]`.
   Reason: MetaMask mobile rejected the string form with
   `json parse error: unexpected character '\'`.

3. `cfcf9d3` — switched method to unsuffixed `eth_signTypedData` with
   **stringified** params, claiming MetaMask's WC router drops `_v4`
   (string → -32603 parse error, object → silent no-op). Also expanded the
   session proposal methods (`eth_sendTransaction`, `personal_sign`,
   `eth_sign`, `eth_signTypedData`, `eth_signTypedData_v4`).
   Did not work as a general fix: stringified params are off-spec, and
   Trust Wallet needs `_v4` + object form.

4. `1969d1c` / `0017c41` — sign UX only (received/timeout diag lines,
   surface `JsonRpcError` code). No payload change.

5. `a99cbe2` — `uint256` numbers: builder emitted `chainId: "0x1"` (string)
   and quoted nonce/threshold. Trust Wallet showed `{\n` escapes and
   "couldn't analyze". Now numeric `chainId: 1`, numeric threshold/nonce/epoch.

6. `f06f0ee` — fixture `nonce` type `string` → `uint256` to match builder.
   (Type said string, value was number `123` → strict parsers reject.)

7. `be6ecc5` — builder `verifyingContract` zero address → `0x...01`
   (deployed verifier). Zero-address verifier contributed to Trust Wallet
   "couldn't analyze". NOTE: the Debug fixture was left at `0x...00`
   (fixed later — see "Current fix").

8. `4dfcd30` — back to `eth_signTypedData_v4` with typed data as **object**
   (`put(JSONObject(json))`), removing the MetaMask stringified hack.
   This was the correct shape.

9. `167b506` — **REGRESSION**: `put(JSONObject(json))` → `put(json)`
   (raw String), plus a test enshrining the string form
   ("params second element is stringified JSON"). `JSONArray.put(String)`
   emits an escaped JSON string: `["0x…","{\"types\":…}"]`.
   Trust Wallet's strict parser cannot process this — the wallet shows the
   request unprocessed / fails. This is the state at HEAD.

10. `0e99720` — dynamic `chainId` plumbing (was hardcoded `eip155:1`) and
    `threshold` via `toLong()`. Correct; kept.

## Root cause (HEAD)

`WalletSessionImpl.signTypedDataV4` sends params[1] as a JSON **string**,
not an **object**. Per EIP-712 + WalletConnect, `eth_signTypedData_v4`
requires `[address, typedDataObject]`. Also: the sign flow installed its
response-capturing delegate inside `onSuccess` (race with fast responses)
and never restored the base session delegate (clobbers future
`onSessionApproved`/`onSessionDelete` handling).

## Current fix (this change)

- `put(JSONObject(json))` → `["addr",{...}]`, with malformed-JSON validation
  before sending (fail fast with `InvalidSignatureFormat`).
- Response delegate installed BEFORE `AppKit.request`; base session delegate
  re-registered in `finally` (extracted `registerSessionDelegate()`), with
  session-lifecycle events mirrored during the sign window.
- `SignRequestShapeTest` rewritten: asserts the object form + a regression
  test proving the string form is a different, invalid shape.
- Debug fixture `verifyingContract` `0x...00` → `0x...01` to match the builder.

## If this still fails in Trust Wallet, next hypotheses (untried)

- Session methods: confirm the approved Trust Wallet session actually granted
  `eth_signTypedData_v4` on `eip155:1` (check `CONNECT` diag proposal vs
  wallet-approved namespaces).
- `Gate.chain` value `"eip155:1"` inside the message is a plain string field —
  harmless per schema, but if the wallet's security analyzer chokes, try `"1"`.
- Zero-address `tokenAddress` in the fixture: fine for a fixture, but Trust
  Wallet risk analysis may flag it; a non-zero fixture token would isolate that.
