#!/bin/bash
# Builds a signed release APK + latest.json into dist/ (run from the project root on the Mac)
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
  "apkUrl": "https://github.com/MysticalMayfly/Medusa/releases/download/v$NAME/medusa-$NAME.apk",
  "sha256": "$SHA",
  "notes": "$NOTES"
}
JSON
echo "Built $APK (versionCode $CODE)"
if command -v gh >/dev/null; then
  gh release create "v$NAME" "$APK" dist/latest.json --repo MysticalMayfly/Medusa --title "Medusa $NAME" --notes "$NOTES"
  echo "Published v$NAME"
else
  echo "gh not installed: upload $APK and dist/latest.json to a new GitHub release tagged v$NAME"
fi
