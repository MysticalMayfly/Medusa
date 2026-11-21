# Medusa: Setup

Environment reference: the Mac, toolchain, Android Studio, sandbox, GitHub, TV and adb.

## Mac

| Item | Value |
|---|---|
| Project on Mac | `/Users/mysticalmirage/AndroidStudioProjects/Medusa` |

### Toolchain (as of 2026-11-20)

| Item | Value |
|---|---|
| IDE | Android Studio on Prince's M5 Mac |
| AGP / Gradle | 9.5.1 / 9.6.0 (wrapper) |
| Language / UI | Kotlin (built into AGP, no separate kotlin plugin), Android Views + AppCompat + Material. No Compose. |
| SDK levels | compileSdk 37, targetSdk 37, **minSdk 34 (do not raise, TV is API 34)** |
| Java target | 11 |
| Signing | `signingConfigs.medusa` from `keystore.properties`, applied to **both** debug and release |
| Emulator | "Television (1080p)" AVD in Studio's Running Devices pane |

### Gradle from a terminal (untested)

`./gradlew` needs a JDK. Studio's bundled one: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Not verified yet on this Mac.

## Android Studio

### Where APKs come from

| Button / task | Output | Installable on TV? | Publish? |
|---|---|---|---|
| **Run 'app'** (green play) | `app/build/intermediates/apk/debug/app-debug.apk` | **No** (`testOnly`) | **Never** |
| **Assemble 'app' Run Configuration** (hammer icon) | `app/build/outputs/apk/debug/app-debug.apk` | Yes | Yes (current method) |
| `./gradlew assembleRelease` | `app/build/outputs/apk/release/app-release.apk` | Yes | Not yet: R8 build untested on TV, test the update loop first |

- Debug and release are signed with the same key, so switching from debug builds to release builds later is a normal update.
- Run also installs onto the selected device. Assemble only builds.

### Driving Android Studio (Claude, via computer use)

- Studio is granted to Claude at **click** tier: plain left-clicks on buttons only. Toolbar buttons work (Sync, Run, Assemble); typing, menus, keyboard shortcuts and right-click do not.
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

## Sandbox

### The sandbox cannot build

- `device_bash` runs in a Linux aarch64 VM with the project mounted at `$HOME/mnt/Medusa`. No Android SDK; Google's build tools are x86_64 only. Do not try to build there. All builds happen in Android Studio on the Mac.
- It has `python3` and `keytool`, enough for `scripts/check_apk.py` and key inspection.
- It cannot reach the Mac's `localhost` (no access to Studio's adb server).
- Its network cannot reach `raw.githubusercontent.com` (checked 2026-11-20). So `scripts/release.sh` runs `check_apk.py --offline` and `scripts/status.sh` prints "live: unreachable here"; the "higher than published" check runs in the publish step of `docs/release.md`.

## GitHub

### Repo layout

| | |
|---|---|
| Repo | See "Frozen identifiers" in `docs/RULES.md` (must stay public, rule 7) |
| Clone URL used | `https://github.com/MysticalMayfly/medusa` (lowercase works) |
| `main` | Source code. Mirror of the Mac project minus git-ignored files. |
| `updates` | **Published update channel.** Only `latest.json` + the current APK (named per `docs/RULES.md` rule 4). Linear history, one commit per publish. |
| Releases / tags | Not used (see constraints). |

### Constraints in Claude sessions (learned 2026-11-20)

- `gh repo create` from the sandbox: **blocked**. The repo was created by hand in the built-in browser (user signed in to GitHub there).
- GitHub **Releases** API from the sandbox: **403 "not permitted for this session type"**. That is why the `updates` branch exists. Do not switch the channel to Releases.
- Push access: call `add_repo` with owner `MysticalMayfly`, repo `Medusa`, access `push`. Then plain `git push` over HTTPS works from the sandbox.
- The Mac project folder is **not** a git repo (git inside the bridged folder breaks on lock-file deletes). All git work happens in a sandbox clone.
- Commit identity used: `MysticalMayfly <MysticalMayfly@users.noreply.github.com>`, plus the session's attribution trailer lines.

## TV

| Item | Value |
|---|---|
| TV | Smart_TV_Pro (G08_4K_GB), Android 14 / API 34. 4K panel, UI runs 1920x1080 @ 320 dpi (xhdpi). Google TV launcher. Mac adb is paired with it. |
| LAN IP | Not stored (DHCP, may change). Ask the user when needed. |

### First install / logs (needs debugging ON, ask the user)

- The user turns TV debugging OFF when not in use (security). Updates never need debugging; only a fresh TV (first install), a broken install, or reading logs do.
- To use it: TV Developer options > Wireless debugging ON. The user turns it OFF afterwards.
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
- Pair once per sandbox: `adb pair <ip>:<pairing port> <6-digit code>` (`<ip>` = the TV's LAN IP, ask the user) (from "Pair device with pairing code"). Connect: `adb connect <ip>:<port on the main Wireless debugging screen>`. Both ports change whenever debugging is toggled: ask the user for fresh ones.
- First install: `adb push dist/medusa-X.apk /sdcard/Download/`, then the user installs it with the TV's File Manager (allow "Install unknown apps" for the File Manager). Or `adb install -r` if the user agrees.
- Logs: `adb logcat -d | grep -E "MedusaUpdater|PackageInstaller|AndroidRuntime"` (add `timeout`, logcat can hang).
