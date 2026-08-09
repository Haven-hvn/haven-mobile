# Haven Mobile — Design Index

**Status:** v1 — Task 0.0 deliverable. Brand: Haven dark-first, Material 3 baseline. No purple gradient, no Inter-only identity.
**Scope:** 5 screens (Onboarding, Library, Watch, Community, Settings) + Debug. This index links every artifact a UX Engineer needs without follow-up questions.

## Brand direction
Haven is archival, not flashy. Dark surface by default (near-black #0B0E14, paper not cream), light theme supported. Accent is **amber/ochre** (Haven seal) — low saturation, used only for active nav + primary CTAs + Verified badge. No purple-indigo, no aurora blobs, no glassmorphism everywhere. Typography: system-adaptive sans + mono for addresses/hashes. Hierarchy via size/weight, not all-caps kickers.

## Map
- Tokens: [tokens.md](tokens.md)
- Components: [components.md](components.md)
- Screens: [screens/onboarding.md](screens/onboarding.md) · [screens/library.md](screens/library.md) · [screens/watch.md](screens/watch.md) · [screens/community.md](screens/community.md) · [screens/settings.md](screens/settings.md)
- A11y: [a11y.md](a11y.md)
- Motion: [motion.md](motion.md)
- Source: Figma pending — exported vectors under `mobile-app/design/icons/` (MediaKind 5, attestation 3). Placeholder `0xDEAD…BEEF` in all redlines.

## Navigation structure (implemented)
`AppNavGraph` (`app/NavGraph.kt`) bottom nav: Library | Community | Watch(center) | Settings | Onboarding. Watch central emphasis (larger hit target) mirrors file-player sample without copying nPlayer 5-icon layout. Top bar per screen: title left, Search + View-toggle (grid/list) right. Fixed header/footer, scrollable middle — vertical-first, thumb-reachable.

## How to use
UX Tasks 1.5/1.6/1.7 cross-check components.md + respective screen md + tokens.md. Every state (empty/loading/error/populated) and both light/dark are shown. No chat/discovery/paywall/treasury designs in v1 scope.
