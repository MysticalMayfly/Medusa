# Medusa

Android TV app by Priyabrat.

## Updates
The app reads `latest.json` from the `updates` branch and updates itself.
To publish: follow `build.md` (Release steps) and `github.md`.

## Docs (read before changing anything)
- `changelog.md`: every published build, newest first (read before any change)
- `features.md`: every feature considered, its status and build (check before shipping)
- `rules.md`: the rules that keep the self-update loop alive, current state, version history
- `updater.md`: how the updater works, the frozen `latest.json` contract, safe changes
- `build.md`: building and testing in Android Studio, emulator update test, TV/adb
- `github.md`: publishing to the `updates` branch, syncing `main`, secrets
- `scripts/check_apk.py`: publish guard (must PASS) · `scripts/release.sh`: prepares dist/
