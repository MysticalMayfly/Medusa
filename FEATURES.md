# Medusa: Features

Every feature that was considered, what happened to it, and the build it first shipped in.
Check this file before shipping any change (`CLAUDE.md`). Update it only when a feature's status changes.

Status: **Shipped** (in a published build) · **Kept** (wanted, not built yet) · **Dropped** (decided against) · **Idea** (from the docs, not decided)

| Feature | Status | First build | Notes |
|---|---|---|---|
| Home screen (brand, title, version card) | Dropped | b2 | Replaced by the stage 1 screens in b7. |
| Self-updater: "Check for updates" button, sha256 check, PackageInstaller | Shipped | b2 | Check on launch + button. Loop confirmed in b3. Button on the Settings screen from b7. |
| Stage 1 screens in Compose for TV, from the Mac app (header and tabs, Home, Movies, TV shows, Horror Standard, More, Universes and each universe, details panel, search, Settings) | Shipped | b7 | Empty template on placeholder data (`data/FakeMedusaRepository`). Published without the emulator update-loop test at Prince's request (2026-11-21). |
| Carousel trailers (android-youtube-player) | Shipped | b10 | Engine from tonight.js: hidden until 4.1 s of playback, 1.2 s fades, ends 6 s early, 3 s gap. Pauses under the details panel, when scrolled away and in the background. Broken-trailer reporting stays on the Mac. |
| Real database behind `MedusaRepository` (stage 2) | Shipped | b10 | Central catalogue in Supabase (view `app_titles`) built on the Mac from ~/Desktop/Films plus TMDB; the TV caches it. Watched and Interested stay on the TV. Upkeep stays on the Mac. |
| App icon (Medusa emblem) | Shipped | b4 | Current form from b6, history in `CHANGELOG.md`. |
| TV banner (Medusa artwork, 16:9) | Shipped | b4 | Current form from b6, history in `CHANGELOG.md`. |
| Loading screen recreated from the desktop Medusa app (light animation, lines in an editable file) | Shipped | b10 | 10 s, once per launch; lines in `res/values/loading_lines.xml`. |
| Automatic update checks on a timer + auto-install | Dropped | | Prince dropped it 2026-11-20. Checks stay on launch + button. |
| Showing `notes` from latest.json on screen | Idea | | From the updater's "Not implemented yet" list. |
| Auto-relaunch after update | Idea | | Android does not allow it directly. |
| Release (R8) builds | Shipped | b8 | Needed for speed: the debug APKs (b2 to b7) are debuggable and unoptimized, so Compose crawls on the TV. `scripts/release.sh` defaults to release. Emulator update-loop test b7 to b8 passed (2026-11-22). |
| Real service, IMDb and Rotten Tomatoes logos | Shipped | b10 | Converted from the Mac app's icons (`ui/components/Logos.kt`). |
| Watch now, Trailer and links open the TV apps | Shipped | b10 | JustWatch links; a note shows when no app takes the link. |
| Mac upkeep tools on the TV (Review, Database, sync, Add/Edit title) | Dropped | | Prince 2026-11-26: upkeep stays on the Mac. |
