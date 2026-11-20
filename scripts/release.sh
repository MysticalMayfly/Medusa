#!/bin/bash
# Prepares a Medusa update in dist/: medusa-<versionName>.apk + latest.json.
# It does NOT publish. Publishing = commit both files to the `updates` branch (see github.md).
#
#   scripts/release.sh debug  "notes"   # uses the APK from Studio's Assemble button (tested path)
#   scripts/release.sh release "notes"  # builds assembleRelease with Gradle (R8; NOT yet tested on the TV)
#
# Stops with an error if the APK fails scripts/check_apk.py.
set -euo pipefail
cd "$(dirname "$0")/.."
MODE="${1:-debug}"
NOTES="${2:-}"
NAME=$(grep -E '^\s*versionName\s*=' app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/')

if [ "$MODE" = "release" ]; then
  ./gradlew --quiet assembleRelease
  SRC=app/build/outputs/apk/release/app-release.apk
elif [ "$MODE" = "debug" ]; then
  SRC=app/build/outputs/apk/debug/app-debug.apk   # from the Assemble button, NOT intermediates/ (Run output)
else
  echo "usage: scripts/release.sh [debug|release] \"notes\""; exit 2
fi
[ -f "$SRC" ] || { echo "Missing $SRC. Build it first."; exit 1; }

mkdir -p dist
APK="dist/medusa-$NAME.apk"
cp "$SRC" "$APK"

python3 scripts/check_apk.py "$APK"     # exits non-zero on any problem

# latest.json is generated from the APK itself, so it can never disagree with it
python3 - "$APK" "$NAME" "$NOTES" > dist/latest.json <<'PY'
import hashlib, json, sys, zipfile, os
sys.path.insert(0, "scripts")
from check_apk import parse_manifest
apk, name, notes = sys.argv[1], sys.argv[2], sys.argv[3]
m = parse_manifest(zipfile.ZipFile(apk).read("AndroidManifest.xml"))
if m["manifest.versionName"] != name:
    sys.exit("APK versionName %s != build.gradle.kts %s (stale build? press Assemble again)" % (m["manifest.versionName"], name))
print(json.dumps({
    "versionCode": m["manifest.versionCode"],
    "versionName": name,
    "apkUrl": "https://raw.githubusercontent.com/MysticalMayfly/medusa/updates/" + os.path.basename(apk),
    "sha256": hashlib.sha256(open(apk, "rb").read()).hexdigest(),
    "notes": notes or ("Medusa " + name),
}, indent=2))
PY
echo
cat dist/latest.json
echo
echo "Ready: $APK + dist/latest.json. Publish both in ONE commit to the 'updates' branch (github.md)."
