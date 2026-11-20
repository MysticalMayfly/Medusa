# Medusa

Android TV app by Priyabrat.

## Updates
The app reads `latest.json` from the `updates` branch and updates itself.
To publish: bump `versionCode` and `versionName` in `app/build.gradle.kts`, run `scripts/release.sh`, then commit `dist/medusa-<version>.apk` and `dist/latest.json` to the `updates` branch.
