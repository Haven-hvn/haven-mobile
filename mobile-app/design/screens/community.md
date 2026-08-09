# Community — grouped feed

- AppBar same as Library (56dp, no search; filter by MediaKind optional).
- Feed: LazyColumn grouped by day, header 12/500 + divider. Rows 72dp: leading author identicon 40dp + MediaKind badge overlay 16dp, title 16/600, subtitle address mono short, trailing timestamp 12/400 + attestation badge.
- Tap row → Watch (piece). Badge in feed: Verified (green check) / Unverified (gray minus) / Failed (red alert) — not collapsed.
- Empty: illustration + “No posts yet” + Explore Library CTA. Loading: top LinearProgress + 6 row shimmer. Error: Retry.

Motion: group header sticky, chip fade 150ms.
