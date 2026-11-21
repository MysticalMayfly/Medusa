# Medusa: Features

Every feature that was considered, what happened to it, and the build it first shipped in.
Check this file before shipping any change (`CLAUDE.md`). Update it only when a feature's status changes.

Status: **Shipped** (in a published build) · **Kept** (wanted, not built yet) · **Dropped** (decided against) · **Idea** (from the docs, not decided)

| Feature | Status | First build | Notes |
|---|---|---|---|
| Home screen (brand, title, version card) | Shipped | b2 | Replaced by the stage 1 screens below once they ship. |
| Self-updater: "Check for updates" button, sha256 check, PackageInstaller | Shipped | b2 | Check on launch + button. Loop confirmed in b3. Button moves to Settings with the stage 1 screens. |
| Stage 1 screens in Compose for TV, from the Mac app (header and tabs, Home, Movies, TV shows, Horror Standard, More, Universes and each universe, details panel, search, Settings) | Kept | | Built as an empty template on placeholder data (`data/FakeMedusaRepository`), not published. Needs the emulator update-loop test (rule 13). |
| Carousel trailers (android-youtube-player) | Kept | | Stage 1 milestone 6. Hook: `HeroPick.trailerId`. |
| Real database behind `MedusaRepository` (stage 2) | Kept | | Screens stay the same; `docs/SLOTS.md` maps each value. |
| App icon (Medusa emblem) | Shipped | b4 | Current form from b6, history in `CHANGELOG.md`. |
| TV banner (Medusa artwork, 16:9) | Shipped | b4 | Current form from b6, history in `CHANGELOG.md`. |
| Loading screen recreated from the desktop Medusa app (light animation, lines in an editable file) | Kept | | Plan approved in principle; Prince said not to start yet. |
| Automatic update checks on a timer + auto-install | Dropped | | Prince dropped it 2026-11-20. Checks stay on launch + button. |
| Showing `notes` from latest.json on screen | Idea | | From the updater's "Not implemented yet" list. |
| Auto-relaunch after update | Idea | | Android does not allow it directly. |
| Release (R8) builds | Idea | | Untested on the TV; needs the emulator update-loop test first. |
