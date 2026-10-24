# Library

Implemented in `feature-library/.../LibraryScreen.kt`. This brief matches the code;
where they disagree, code wins and this file is the bug.

```
┌ TopBar: "Library" + "N items · M offline"        fixed
├─ Search field                                     fixed
├─ Chips: [Offline M] [All] [Video] [Audio] …       fixed, scrolls sideways
├─ Grid (default) or list of media items            scrolls, pull-to-refresh
└─ Selection action bar (only while selecting)      fixed
```

- Grid is the default: a 16:9 cover plate says more at a glance than a row of text.
  List is one tap away for density and full CIDs. The choice persists.
- Subtitle carries residency — total items and how many work offline — because that is
  the fact a reader wants at a glance. Cache/attestation state is badged per item.
- Search and kind chips stay fixed above the scroll so they remain reachable in a
  library of hundreds. Chips carry counts; an empty kind is visible before tapping it.
  The Offline chip is first: residency is a different axis from kind.
- Pull-to-refresh and the top-bar refresh button both call the same refresh; the
  progress indicator and any refresh error appear above the list, never instead of it.
- Selection: long-press or the checklist button enters selection; checked items can be
  unlocked (keys only) or downloaded (keys + files) together, with the signature count
  disclosed before the wallet pops. Hidden items leave the list but stay recoverable
  via "Show N hidden".
- States: skeleton while loading; "No wallet connected" when disconnected; error with
  retry; empty library points at communities (joining one is how items arrive);
  no-matches state names the active filters and offers to clear them.
- Motion: chip and state changes follow `HavenMotion`; see [../motion.md](../motion.md).
