# Medusa: Changelog

One row per published build (what is live, or was live, on the `updates` branch). Newest first.
Dates are India time (IST, UTC+5:30). APK file names follow `docs/RULES.md` rule 4.
Read the top rows before making any change (`CLAUDE.md`). Write the row for a new build before running
`scripts/release.sh` (`docs/release.md`): its Changes cell becomes the `notes` in `latest.json`.
If that build is then not published, remove its row.

| Build | Version | Date | APK | Changes |
|---|---|---|---|---|
| b8 | 1.3 | 2026-11-22 | `medusa-1.3-b8.apk` | Speed: first release (R8) build with a baseline profile instead of a debug APK. Carousel zoom off, lighter focus animations, rows no longer fade in again on scroll. Focus fixes: Up from a screen lands on the open tab, Up next buttons, leaving the search field with the D-pad, the Settings button during an update check. |
| b7 | 1.3 | 2026-11-21 | `medusa-1.3-b7.apk` | New Compose for TV screens (stage 1 template on placeholder data): header and tabs, Home, Movies, TV shows, Horror Standard, More, Universes, details panel, search and Settings. Check for updates moved to the Settings screen. Published without the emulator update-loop test at Prince's request. |
| b6 | 1.3 | 2026-11-21 | `medusa-1.3-b6.apk` | Icon and TV banner sized per density, all lossless PNG. Banner 640x360 (xhdpi, exact for the TV) plus 960x540 / 1280x720, smoothing on. Icon foreground 108 to 432 px (216 px at xhdpi, used 1:1 on the TV). b5 hi-res files removed. |
| b5 | 1.3 | 2026-11-20 | `medusa-1.3-b5.apk` | Icon and TV banner shipped as single hi-res unscaled files (1152 px icon, 1280x720 banner in `drawable-nodpi`). Old low-res icon/banner files removed. Known issue: looks worse on the TV (launcher downscales them with a cheap filter); fix planned. |
| b4 | 1.3 | 2026-11-20 | `medusa-1.3-b4.apk` | New Medusa app icon (adaptive, from "Glossy Medusa Emblem Portrait") and new TV banner (Prince's 16:9 image). APKs now named by build (`docs/RULES.md` rule 4); versionName only changes for major updates. |
| b3 | 1.2 | 2026-11-20 | `medusa-1.2.apk` | Update test: TV updated itself from b2 to b3. Self-update loop confirmed. |
| b2 | 1.1 | 2026-11-20 | `medusa-1.1.apk` | First clean build: home screen (brand, title, version card) and "Check for updates" self-updater. Sideloaded on the TV. |
| b1 | 1.0 | 2026-11-20 | `medusa-1.0.apk` | Broken: published from Android Studio's Run output (testOnly), TV refused it. Do not reuse. |
