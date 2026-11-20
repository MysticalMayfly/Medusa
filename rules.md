# Medusa: Rules (read this first, every session)

Medusa is Prince's Android TV app. It **updates itself from GitHub**. The loop is:

```
edit code -> bump version -> Assemble APK -> check_apk.py PASS -> commit APK + latest.json to `updates` branch
-> TV app reads latest.json -> downloads APK -> verifies sha256 -> installs over itself
```

Every rule below exists to keep that loop alive. Breaking one can strand the TV on an old
version, and the only fix is a manual uninstall + sideload (all app data lost).

Companion docs: `updater.md` (how the updater works), `build.md` (building/testing),
`github.md` (publishing), plus `scripts/check_apk.py` and `scripts/release.sh`.

## Current state (update this table after every publish)

| Item | Value |
|---|---|
| Published version | 1.3 (versionCode 5) |
| Installed on TV | 1.2 (versionCode 3) |
| Package | `com.priyabrat.medusa` |
| Signing cert SHA-256 | `1e11bdb1a4638bfa287145a86e83fe1c3312e41f6fff7e6fc717a2591e31a2ec` |
| Update manifest | `https://raw.githubusercontent.com/MysticalMayfly/medusa/updates/latest.json` |
| Repo | `github.com/MysticalMayfly/Medusa` (public) |
| Project on Mac | `/Users/mysticalmirage/AndroidStudioProjects/Medusa` |
| TV | Smart_TV_Pro (G08_4K_GB), Android 14 / API 34, IP 192.168.1.9 (DHCP, may change) |

## Golden rules (never break)

1. **Never change `applicationId`** (`com.priyabrat.medusa`). A new ID is a different app; the old one never updates again.
2. **Never lose, replace or regenerate the signing key.** `release.jks` + `keystore.properties` (project root, git-ignored). Android refuses updates signed by another key. Keep a backup outside the project (USB drive or password manager).
3. **Every APK must be signed with that key.** `check_apk.py` verifies the cert SHA-256 above. If `keystore.properties` is missing, Gradle silently signs with the default debug key: check_apk catches this.
4. **`versionCode` must go up on every publish** (by 1). Never reuse or lower it. It is the only number the updater and Android compare. `versionName` (e.g. 1.3) is display text: keep it until Prince calls a major update. The APK filename is `medusa-<versionName>-b<versionCode>.apk`, so it is always unique.
5. **Never publish an APK from Android Studio's Run button.** Run output (`app/build/intermediates/apk/...`) is `testOnly` and the TV installer rejects it as "package appears to be invalid". Use the **Assemble** button output (`app/build/outputs/apk/debug/app-debug.apk`) or `assembleRelease`.
6. **Never publish without `python3 scripts/check_apk.py <apk>` printing PASS.**
7. **The update channel is frozen forever.** Do not change: the repo owner/name, its public visibility, the `updates` branch name, the filename `latest.json`, its field names or types, or `Updater.MANIFEST_URL`. Old builds on the TV only know this exact URL and schema. Migration rules are in `updater.md`.
8. **`latest.json` and its APK go in the same commit.** latest.json must never point at a file that does not exist yet.
9. **Never force-push, rewrite or delete the `updates` branch.** Never delete `main`.
10. **Never raise `minSdk` above 34.** The TV is Android 14 (API 34); a higher minSdk makes the APK uninstallable there.
11. **Never remove** the `INTERNET` / `REQUEST_INSTALL_PACKAGES` permissions, the `InstallResultReceiver` manifest entry, or the update button and its `Updater` wiring in `MainActivity`. Without them the next version cannot be pulled.
12. **Never commit secrets** to git: `release.jks`, `*.jks`, `keystore.properties`, `local.properties`, `dist/`. They are in `.gitignore`; check before every push.
13. **Test the update on the emulator before publishing anything that touches** `update/*`, `AndroidManifest.xml`, `build.gradle.kts`, signing or SDK levels (procedure in `build.md`).

## Session protocol (for Claude)

- Work on the Mac folder through the device bridge (`device_bash`, path `$HOME/mnt/Medusa`). The cloud sandbox is only for git pushes to GitHub.
- Android Studio is granted **click-only** to Claude: toolbar buttons work (Sync, Run, Assemble); menus, typing and keyboard shortcuts do not.
- The Linux sandbox cannot build Android (no SDK, aarch64). All builds happen in Android Studio on the Mac.
- Deleting files in the Mac folder needs the user's permission (bridge rule). Overwriting is fine.
- Never block the chat on long waits (over ~60 s) with no output. Say what you are waiting for (e.g. GitHub's ~5 min cache) and check back.
- The user turns TV debugging OFF when not in use. Updates never need debugging; only first installs and log reading do.
- After a publish: update the "Current state" table above.
- User preferences: short bullet replies, no em dashes, minimal token use, desktop over cloud.

## Version history

| versionName | versionCode | Notes |
|---|---|---|
| 1.0 | 1 | Broken: published from Run output (testOnly). Do not reuse. |
| 1.1 | 2 | First clean build. Installed on TV by sideload. |
| 1.2 | 3 | Update test: TV updated 1.1 -> 1.2 by itself. Loop confirmed 2026-11-20. |
| 1.3 | 4 | New Medusa icon + TV banner. First APK named by build (`medusa-1.3-b4.apk`). 2026-11-20. |
| 1.3 | 5 | Hi-res icon (1152 px) and banner (1280x720) in drawable-nodpi so 4K launchers show them sharp. 2026-11-20. |
