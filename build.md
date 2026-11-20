# Medusa: Build & Test

Read `rules.md` first.

## Toolchain (as of 2026-11-20)

| Item | Value |
|---|---|
| IDE | Android Studio on Prince's M5 Mac |
| AGP / Gradle | 9.5.1 / 9.6.0 (wrapper) |
| Language / UI | Kotlin (built into AGP, no separate kotlin plugin), Android Views + AppCompat + Material. No Compose. |
| SDK levels | compileSdk 37, targetSdk 37, **minSdk 34 (do not raise, TV is API 34)** |
| Java target | 11 |
| Signing | `signingConfigs.medusa` from `keystore.properties`, applied to **both** debug and release |
| Emulator | "Television (1080p)" AVD in Studio's Running Devices pane |

## Where APKs come from

| Button / task | Output | Installable on TV? | Publish? |
|---|---|---|---|
| **Run 'app'** (green play) | `app/build/intermediates/apk/debug/app-debug.apk` | **No** (`testOnly`) | **Never** |
| **Assemble 'app' Run Configuration** (hammer icon) | `app/build/outputs/apk/debug/app-debug.apk` | Yes | Yes (current method) |
| `./gradlew assembleRelease` | `app/build/outputs/apk/release/app-release.apk` | Yes | Not yet: R8 build untested on TV, test the update loop first |

- Debug and release are signed with the same key, so switching from debug builds to release builds later is a normal update.
- Run also installs onto the selected device. Assemble only builds.

## Driving Android Studio (Claude, via computer use)

- Studio is granted at **click** tier: plain left-clicks on buttons only. No typing, no menus, no shortcuts, no right-click.
- Use `computer_app_screenshot` first, then click by `element_index`. Toolbar buttons (AX titles):
  - `Sync Project with Gradle Files`: after any change to `*.gradle.kts`, `libs.versions.toml`, `gradle.properties`
  - `Run 'app'`: build + install on the device selected in the toolbar (emulator)
  - `Assemble 'app' Run Configuration`: build only (use this for publishable APKs)
  - `Stop 'app'`
- Window ids change between sessions: call `computer_app_list_windows` and pick the window titled `Medusa`.
- A build takes ~20-50 s. Verify by file timestamp, not by guessing:
  `date -r app/build/outputs/apk/debug/app-debug.apk`
- If Run shows "Application Installation Failed ... different signature" on the emulator: clicking OK uninstalls the emulator copy (emulator data only) and reinstalls. Never do this on the real TV without asking.
- Studio picks up file edits from the bridge automatically. Gradle file edits need Sync.

## The sandbox cannot build

- `device_bash` runs in a Linux aarch64 VM with the project mounted at `$HOME/mnt/Medusa`. No Android SDK; Google's build tools are x86_64 only. Do not try to build there.
- It has `python3` and `keytool`, enough for `scripts/check_apk.py` and key inspection.
- It cannot reach the Mac's `localhost` (no access to Studio's adb server).

## Release steps (every update)

1. Make the code change in the Mac folder.
2. In `app/build.gradle.kts` set `versionCode = <published + 1>` and `versionName` only for a major update (Prince decides; otherwise keep it).
3. Studio: **Sync** (if Gradle files changed), then **Assemble**. Wait for a new timestamp on `app/build/outputs/apk/debug/app-debug.apk`.
4. `bash scripts/release.sh debug "what changed"` (from the project root, Mac or sandbox).
   - Copies the APK to `dist/medusa-<versionName>-b<versionCode>.apk`, runs `check_apk.py`, writes `dist/latest.json` from the APK.
   - Any FAIL = stop and fix. Never edit latest.json by hand to get around it.
5. If the change touches anything in `rules.md` rule 13: run the **update-loop test** below.
6. Publish both files (`github.md`).
7. Push source to `main` (`github.md`).
8. Update the "Current state" table and version history in `rules.md`.

## Update-loop test (emulator)

Goal: prove the **currently published** version can update to the **new** one.

1. Make sure the emulator runs the currently published version. If not, install it: build that version with Run, or install its APK.
2. Bump the version and press **Assemble** (not Run: Run would install the new version directly and skip the test).
3. `scripts/release.sh debug "..."` then publish to `updates` (`github.md`).
4. Wait until the raw latest.json shows the new versionCode (up to ~5 min).
5. In Studio's Running Devices pane, use the on-screen D-pad: focus "Check for updates", press the centre button (OK). Accept the install prompt if shown.
6. Reopen Medusa: the version card must show the new version and build.
7. Read Logcat (tag `MedusaUpdater`) if anything fails.

If a change might break updating, publish a throwaway test version first (e.g. 1.3 test, then 1.4 real) rather than risk the TV.

## scripts/check_apk.py

`python3 scripts/check_apk.py dist/medusa-X.apk [--offline]`

Checks, from the APK itself: package = `com.priyabrat.medusa`, not `testOnly`, signer cert SHA-256 = the pinned key, versionCode present, and (online) versionCode higher than the live `latest.json`. Exit 0 = PASS. Also prints versionName, debuggable flag and the file SHA-256.

## Gradle from a terminal (untested)

`./gradlew` needs a JDK. Studio's bundled one: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Not verified yet on this Mac.

## TV: first install / logs (needs debugging ON, ask the user)

Updates never need this. Only for a fresh TV, a broken install, or reading logs.

- TV: Developer options > Wireless debugging ON. The user turns it OFF afterwards (security).
- Sandbox adb: Ubuntu 22.04's adb (v28) cannot pair. A working adb 34 is built from Ubuntu noble packages with their own loader:
  ```bash
  D=$HOME/apt-noble; mkdir -p $D/lists/partial $D/cache/archives/partial $D/etc
  echo "deb [trusted=yes] http://ports.ubuntu.com/ubuntu-ports noble main universe" > $D/etc/sources.list
  O="-o Dir::Etc::sourcelist=$D/etc/sources.list -o Dir::Etc::sourceparts=- -o Dir::State::Lists=$D/lists -o Dir::Cache=$D/cache -o Debug::NoLocking=1"
  apt-get $O update
  R=$HOME/adb34; mkdir -p $R/debs && cd $R/debs
  apt-get $O download adb android-libbase android-libboringssl android-libcutils android-liblog android-libziparchive libbrotli1 libc6 libgcc-s1 liblz4-1 libprotobuf32t64 libstdc++6 libusb-1.0-0 libzstd1 zlib1g libudev1 libcap2
  for d in *.deb; do dpkg -x $d $R/root; done
  # wrapper $R/adb runs: $R/root/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1 --library-path <all lib dirs under $R/root> $R/root/usr/lib/android-sdk/platform-tools/adb "$@"
  ```
- The adb server cannot run as a detached daemon in the sandbox (blocked). In **each** call: `$A -a nodaemon server & SRV=$!; sleep 3; ...commands...; kill $SRV`. The server does not survive between calls, so `connect` again each call.
- Pair once per sandbox: `adb pair <ip>:<pairing port> <6-digit code>` (from "Pair device with pairing code"). Connect: `adb connect <ip>:<port on the main Wireless debugging screen>`. Both ports change whenever debugging is toggled: ask the user for fresh ones.
- First install: `adb push dist/medusa-X.apk /sdcard/Download/`, then the user installs it with the TV's File Manager (allow "Install unknown apps" for the File Manager). Or `adb install -r` if the user agrees.
- Logs: `adb logcat -d | grep -E "MedusaUpdater|PackageInstaller|AndroidRuntime"` (add `timeout`, logcat can hang).
