# Medusa

Android TV app by Priyabrat. It updates itself: the installed app reads `latest.json` from this repo's
`updates` branch, downloads the APK it names, and installs it over itself.

## Docs

- `CLAUDE.md`: starting point for every Claude session (reading order, session behaviour)
- `docs/RULES.md`: golden rules and frozen identifiers
- `docs/updater.md`: how the updater works and the frozen `latest.json` format
- `docs/release.md`: one checklist from code change to publish to syncing `main`
- `docs/setup.md`: environment reference (Mac, toolchain, Android Studio, sandbox, GitHub, TV, adb)
- `CHANGELOG.md`: one row per published build
- `FEATURES.md`: one row per feature with its current status, plus ideas

## Scripts

- `scripts/release.sh`: prepares `dist/` (APK + `latest.json`) for a publish
- `scripts/check_apk.py`: publish guard, must print PASS
- `scripts/status.sh`: prints the current versions
