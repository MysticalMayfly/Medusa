# Medusa: Rules

## The self-update loop

```
edit code -> bump version -> Assemble APK -> check_apk.py PASS -> commit APK + latest.json to `updates` branch
-> TV app reads latest.json -> downloads APK -> verifies sha256 -> installs over itself
```

Every rule below exists to keep that loop alive. Breaking one can strand the TV on an old
version, and the only fix is a manual uninstall + sideload (all app data lost).

## Frozen identifiers

| Item | Value |
|---|---|
| Package | `com.priyabrat.medusa` |
| Signing cert SHA-256 | `1e11bdb1a4638bfa287145a86e83fe1c3312e41f6fff7e6fc717a2591e31a2ec` |
| Update manifest | `https://raw.githubusercontent.com/MysticalMayfly/medusa/updates/latest.json` |
| Repo | `github.com/MysticalMayfly/Medusa` (public) |

## Golden rules (never break)

1. **Never change `applicationId`** (`com.priyabrat.medusa`). A new ID is a different app; the old one never updates again.
2. **Never lose, replace or regenerate the signing key.** `release.jks` + `keystore.properties` (project root, git-ignored). Android refuses updates signed by another key. Keep a backup outside the project (USB drive or password manager).
3. **Every APK must be signed with that key.** `check_apk.py` verifies the cert SHA-256 in "Frozen identifiers" above. If `keystore.properties` is missing, Gradle silently signs with the default debug key: check_apk catches this.
4. **`versionCode` must go up on every publish** (by 1). Never reuse or lower it. It is the only number the updater and Android compare. `versionName` (e.g. 1.3) is display text: keep it until Prince calls a major update. The APK filename is `medusa-<versionName>-b<versionCode>.apk`, so it is always unique.
5. **Never publish an APK from Android Studio's Run button.** Run output (`app/build/intermediates/apk/...`) is `testOnly` and the TV installer rejects it as "package appears to be invalid". Use the **Assemble** button output (`app/build/outputs/apk/debug/app-debug.apk`) or `assembleRelease`.
6. **Never publish without `python3 scripts/check_apk.py <apk>` printing PASS.**
7. **The update channel is frozen forever.** Do not change: the repo owner/name, its public visibility, the `updates` branch name, the filename `latest.json`, its field names or types, or `Updater.MANIFEST_URL`. Never rename, transfer or delete the repo, or make it private. Old builds on the TV only know this exact URL and schema. Migration rules are in `docs/updater.md`.
8. **`latest.json` and its APK go in the same commit.** latest.json must never point at a file that does not exist yet.
9. **Never force-push, rewrite, delete or rename the `updates` branch.** Never force-push, delete or rename `main`.
10. **Never raise `minSdk` above 34.** The TV is Android 14 (API 34); a higher minSdk makes the APK uninstallable there.
11. **Never remove** the `INTERNET` / `REQUEST_INSTALL_PACKAGES` permissions, the `InstallResultReceiver` manifest entry, or the update button and its `Updater` wiring in `MainActivity`. Without them the next version cannot be pulled.
12. **Never commit secrets** to git: `release.jks`, `*.jks`, `keystore.properties`, `local.properties`, `dist/`. They are in `.gitignore`; check before every push. Do not remove those lines from `.gitignore`.
13. **Test the update on the emulator before publishing anything that touches** `update/*`, `AndroidManifest.xml`, `build.gradle.kts`, `MainActivity`'s update wiring, signing or SDK levels (procedure in `docs/release.md`, "Update-loop test").
