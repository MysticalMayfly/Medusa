#!/bin/bash
# Builds a signed release APK + latest.json into dist/, ready for the "updates" branch
set -e
cd "$(dirname "$0")/.."
CODE=$(grep -E '^\s*versionCode\s*=' app/build.gradle.kts | grep -oE '[0-9]+')
NAME=$(grep -E '^\s*versionName\s*=' app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/')
./gradlew --quiet assembleRelease
mkdir -p dist
APK="dist/medusa-$NAME.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"
SHA=$(shasum -a 256 "$APK" | cut -d' ' -f1)
NOTES="${1:-Medusa $NAME}"
cat > dist/latest.json <<JSON
{
  "versionCode": $CODE,
  "versionName": "$NAME",
  "apkUrl": "https://raw.githubusercontent.com/MysticalMayfly/medusa/updates/medusa-$NAME.apk",
  "sha256": "$SHA",
  "notes": "$NOTES"
}
JSON
echo "Built $APK (versionCode $CODE)"
echo "Next: push dist/medusa-$NAME.apk and dist/latest.json to the \"updates\" branch"
