# Medusa: Features

Every feature that was considered, what happened to it, and the build it shipped in.
Check this file before shipping any change (`rules.md`), and update it in the same session.

Status: **Shipped** (in a published build) · **Kept** (wanted, not built yet) · **Dropped** (decided against) · **Idea** (from the docs, not decided)

| Feature | Status | Build | Notes |
|---|---|---|---|
| Home screen (brand, title, version card) | Shipped | 1.1 (b2) | |
| Self-updater: "Check for updates" button, sha256 check, PackageInstaller | Shipped | 1.1 (b2) | Check on launch + button. Loop confirmed in 1.2 (b3). |
| New app icon (Medusa emblem) | Shipped | 1.3 (b4) | Reworked in b5. |
| New TV banner (Medusa artwork, 16:9) | Shipped | 1.3 (b4) | Prince's 16:9 image. Reworked in b5. |
| APK named by build number, versionName only for major updates | Shipped | 1.3 (b4) | `medusa-<versionName>-b<versionCode>.apk`. Rule 4 updated. |
| Hi-res unscaled icon and banner | Shipped, then replaced | 1.3 (b5) | Looked worse on the TV. Replaced in b6. |
| Per-density icon/banner sized to the TV's real density | Shipped | 1.3 (b6) | TV UI is 1080p @ 320 dpi (xhdpi), so the 640x360 banner and 216 px icon are used 1:1. |
| Loading screen recreated from the desktop Medusa app (light animation, lines in an editable file) | Kept | | Plan approved in principle; Prince said not to start yet. |
| Automatic update checks on a timer + auto-install | Dropped | | Prince dropped it 2026-11-20. Checks stay on launch + button. |
| Showing `notes` from latest.json on screen | Idea | | From `updater.md`. |
| Release (R8) builds | Idea | | Untested on the TV; needs the emulator update-loop test first. |
