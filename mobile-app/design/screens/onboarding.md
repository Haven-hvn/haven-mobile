# Screens — Onboarding

Wireframe: vertical center, 320dp max width. Illustration 160dp → headline Display 32/400 → body 16/400 → primary CTA 48dp → secondary link 40dp below. Brand amber CTA, not purple.

States:
- Disconnected: “Connect wallet” (Reown AppKit). Terms checkbox required.
- Connecting: CTA shows LinearProgress 2dp top, disabled.
- Connected: address mono `0xABCD…1234`, “Continue to Library”.
- Error: inline message + Retry, error code preserved.

Error panel (Sprint 3 alt) shows last trigger directly under CTA, not modal.

Light/dark: same layout, surface swaps per tokens.

A11y: CTA contentDescription “Connect wallet”, terms link role Button.
