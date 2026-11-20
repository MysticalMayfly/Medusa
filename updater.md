# Medusa: Self-updater

How the in-app updater works, and what must stay compatible so every installed version can
still reach the next one. Read `rules.md` first.

## Files

| File | Role |
|---|---|
| `app/src/main/java/com/priyabrat/medusa/update/Updater.kt` | Check, download, verify, install. Holds `MANIFEST_URL`. |
| `app/src/main/java/com/priyabrat/medusa/update/UpdateInfo.kt` | Parses `latest.json`. |
| `app/src/main/java/com/priyabrat/medusa/update/InstallResultReceiver.kt` | Receives the PackageInstaller result. |
| `app/src/main/java/com/priyabrat/medusa/MainActivity.kt` | Owns the "Check for updates" button and shows updater state. |
| `app/src/main/AndroidManifest.xml` | `INTERNET`, `REQUEST_INSTALL_PACKAGES`, `<receiver .update.InstallResultReceiver exported=false>`. |

## Flow

1. **Launch:** `MainActivity.onCreate` calls `updater.check()` once (not on rotation).
2. **Check:** GET `MANIFEST_URL?t=<millis>`, parse JSON, compare `versionCode` with the installed `longVersionCode`.
   - Higher: state `Available`, button text becomes "Install v<versionName>".
   - Equal or lower: "You are on the latest version".
3. **Download** (button press): stream `apkUrl` to `cacheDir/updates/medusa-<versionCode>.apk` (folder cleared first), compute SHA-256 while downloading, progress bar 0-100.
   - If `sha256` is present and does not match: file deleted, "Update failed: checksum mismatch".
4. **Permission:** if `packageManager.canRequestPackageInstalls()` is false, open `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for Medusa and wait. `onResume` continues the install once granted. If the TV has no such screen, the user is told to enable it under Settings > Apps > Security > Unknown sources.
5. **Install:** `PackageInstaller` session, `MODE_FULL_INSTALL`, `setAppPackageName(packageName)`, `setRequireUserAction(USER_ACTION_NOT_REQUIRED)`. Commit with an **explicit** `PendingIntent` to `InstallResultReceiver` using `FLAG_UPDATE_CURRENT | FLAG_MUTABLE` (mutable is required so the system can attach the result extras).
6. **Result** (`InstallResultReceiver`):
   - `STATUS_PENDING_USER_ACTION`: launch the system confirm dialog (`EXTRA_INTENT`).
   - `STATUS_SUCCESS`: Android replaces the app and kills the process. The app does not reopen by itself; the user opens it again. Expected.
   - Anything else: Toast "Update failed: <message>" + Logcat.

Logcat tag: **`MedusaUpdater`** (logs installed vs latest versionCode, failures, install status).

## The `latest.json` contract (frozen)

Location: branch `updates`, repo root, `https://raw.githubusercontent.com/MysticalMayfly/medusa/updates/latest.json`

```json
{
  "versionCode": 3,
  "versionName": "1.2",
  "apkUrl": "https://raw.githubusercontent.com/MysticalMayfly/medusa/updates/medusa-1.2.apk",
  "sha256": "<64 lowercase hex chars of the APK file>",
  "notes": "free text"
}
```

| Field | Type | Required | Rule |
|---|---|---|---|
| `versionCode` | integer | yes | Equals the APK's versionCode. Read with `getLong`. |
| `versionName` | string | yes | Equals the APK's versionName. Shown on the button. |
| `apkUrl` | string (https) | yes | Must already exist on the branch. Pattern: `.../updates/medusa-<versionName>-b<versionCode>.apk` (was `medusa-<versionName>.apk` up to 1.2; the app does not care about the name). |
| `sha256` | string | optional in code, **always set it** | SHA-256 of the exact APK file. |
| `notes` | string | optional | Not shown yet. |

- **Only ever add fields. Never rename, remove or change the type of these five.** Old installed versions parse this file with their own old code.
- Valid JSON only (no comments, no trailing commas). `scripts/release.sh` generates it from the APK, so use that.
- Plain HTTPS GET with redirects must keep working (old builds use `HttpURLConnection`, follow same-protocol redirects, send `User-Agent: Medusa-Updater`).

## Caching

- `raw.githubusercontent.com` caches files for up to ~5 minutes. The `?t=` query does **not** bypass it.
- After a publish, the TV may see the old version for up to 5 minutes. Not a bug. Never "fix" it by changing the URL.

## Why updates can fail (and the fix)

| Symptom | Cause | Fix |
|---|---|---|
| "package appears to be invalid" (File Manager) | APK is `testOnly` (Studio Run output) | Publish the Assemble/assembleRelease APK. check_apk blocks this. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / signature mismatch | APK signed with a different key | Rebuild with `keystore.properties` present. If a wrong-key APK was installed, the only fix is uninstall + sideload. |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | versionCode not higher | Bump versionCode. |
| `INSTALL_FAILED_OLDER_SDK` | `minSdk` above 34 | Keep `minSdk = 34`. |
| "Update failed: HTTP 404" | latest.json or APK missing on `updates` | Commit both files together; wait for cache. |
| "checksum mismatch" | APK changed after latest.json was made | Regenerate latest.json with `release.sh`. |
| Stuck on "Allow Medusa to install apps" | Unknown-sources permission off | TV Settings > Apps > Security > Unknown sources > Medusa ON. |

## Changing the updater safely

- Any change to `update/*`, the manifest entries above, or `MainActivity`'s update wiring **must be tested on the emulator** with the full loop (`build.md`, "Update-loop test") before publishing.
- The version that ships a new updater is still installed by the **old** updater. Only the version after it uses the new code. So both must work.
- **Moving the update channel** (new host, new URL, private repo, server on a PC): 
  1. Ship a "bridge" version through the **current** channel whose `MANIFEST_URL` points to the new location.
  2. Confirm the TV runs the bridge version.
  3. Keep the old `latest.json` pointing at the bridge version for as long as any device might still be on an old build. Never delete the old channel early.
- Private repo is not supported: the app has no token. Keep the repo public.

## Not implemented yet (ideas, not requirements)

- Automatic periodic checks (currently: on launch + button).
- Showing `notes` on screen.
- Auto-relaunch after update (Android does not allow it directly).
