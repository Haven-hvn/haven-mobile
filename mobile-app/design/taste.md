# Design taste, translated for Compose

How the design-taste-frontend skill (`~/.agents/skills/design-taste-frontend`) applies to
this native Android app. The skill's web tooling (draw/inspect/lint scripts, CSS assets,
scroll scenes) does not run here; its app-screen branch and house rules do, translated below.

## What carries over

| Skill rule (web) | Compose translation |
| --- | --- |
| Idea from the subject, competitor test | Haven's idea is the archive only its owner can open: seal, attestation, residency. A screen that could belong to any media player has failed, however clean. |
| Tokens first, everything references tokens | `HavenTheme`, `HavenSpacing`, `HavenShapes`, `HavenTypography`, `HavenAccents`. No hard-coded hex, dp, or sp in feature modules. |
| One ground, one ink, one accent | Void/Paper grounds, full-ink running text, Ember accent on the primary action and at most one other thing. |
| No gradients, glows, glass | Flat color. Exceptions the skill itself allows: a neutral scrim under text on artwork (`Media.kt`), and the skeleton sheen (`States.kt`). |
| No shadows at rest | Tone steps separate surfaces; elevation only on floating UI (menus, dialogs, sheets). |
| No eyebrows / tiny uppercase tracked labels | Section labels are sentence case (`SectionHeader`, onboarding). Put the information in the heading or first sentence. |
| No monospace outside real code/data | Ledger mono is reserved for evidence: addresses, CIDs, byte counts, error codes. That is this app's "real code" exception. |
| No icon-topped feature cards / identical card grids | Media cards carry real cover art, not icons. Where no art exists, the kind glyph is a placeholder for the item, never decoration. |
| Type: few sizes, big contrast of scale | Material type scale with tightened display tracking; five sizes do the visible work per screen. |
| Real content, full density | Every state shown at real density: empty, loading, error, populated. No invented stats, reviews, or proof. |
| Look at what you made | The inspector's role is played by emulator/Maestro screenshots at phone widths, critiqued before merge. A green build is not a looked-at screen. |
| Motion: entrance, feedback, reduced motion | `HavenMotion` tokens; press/focus feedback on every control; content never hidden if animation is off. |

## What does not carry over

- Page rules against hairlines, small labels, and repeated panels: the skill exempts real
  app screens, and a 1dp outline plus repeated media rows are correct here.
- Web compositions (heroes, scroll scenes, section blueprints): screens follow the
  per-screen briefs in [screens/](screens/) instead.
- The draw script may still deal an app style (`draw.mjs --app --keep palette,type`) as
  inspiration for composition; its output is mapped onto the tokens above, never copied
  as CSS values.

## Haven-specific checks per screen

1. First screen answers the first question in large type: what is this, what can I do.
2. Residency is visible at a glance (offline counts, cache state) — it is Haven's
   differentiating fact, so it outranks decoration.
3. Meaning hues (attestation, cache, protocol) are never reused as decoration.
4. Every control reachable and operable by thumb; 48dp targets; visible focus.
5. Empty states name the fix (join a community), not just the absence.
