# Motion

Bounded — no gratuitous sequences. Tokens mirrored in compose via `HavenMotion`.

- Navigation crossfade 200ms easeOut.
- Bottom nav indicator 150ms spring (no scale-lift).
- Chip/state change fade 150ms.
- Viewer chrome fade 150ms; PiP 250ms easeInOut.
- Page change (PDF) 150ms slide 8dp.
- Shimmer 1200ms linear loop, only on loading placeholders.
- Respects reduced-motion: durations → 0 or opacity-only.

No fade-up on every card, no hover:scale-105, no blur+border+shadow stacks.
