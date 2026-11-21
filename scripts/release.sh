#!/bin/bash
# Prepares a Medusa update in dist/: medusa-<versionName>-b<versionCode>.apk + latest.json.
# It does NOT publish. Publishing = commit both files to the `updates` branch (docs/release.md).
#
#   scripts/release.sh           # = release: the APK from Studio's Assemble with Build Variant "release"
#   scripts/release.sh debug     # the debug APK from Assemble (old path, b2 to b7; slow on the TV)
#
# Publish release builds. A debug APK is debuggable and not optimized (no R8, no baseline profile):
# Compose runs many times slower, which made b7 crawl on the TV.
#
# Before touching any APK it stops with an error unless:
#   - the top row of CHANGELOG.md has Build = b<versionCode from app/build.gradle.kts>
#   - every build cited in FEATURES.md (b<number>) has a row in CHANGELOG.md
# latest.json "notes" is the Changes cell of that CHANGELOG.md row, as a plain string.
# Stops with an error if the APK fails scripts/check_apk.py (run --offline: the Mac VM cannot reach
# raw.githubusercontent.com; "higher than published" is checked in the publish step, docs/release.md).
set -euo pipefail
cd "$(dirname "$0")/.."
export PYTHONDONTWRITEBYTECODE=1   # no scripts/__pycache__ in the project folder
MODE="${1:-release}"
USAGE='usage: scripts/release.sh [debug|release]   (notes come from the top CHANGELOG.md row)'
if [ $# -gt 1 ] || { [ "$MODE" != "debug" ] && [ "$MODE" != "release" ]; }; then
  echo "$USAGE"; exit 2
fi
NAME=$(grep -E '^\s*versionName\s*=' app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/')
CODE=$(grep -E '^\s*versionCode\s*=' app/build.gradle.kts | sed -E 's/[^0-9]*([0-9]+).*/\1/')

# CHANGELOG.md / FEATURES.md checks; prints the notes for latest.json
NOTES=$(python3 - "$CODE" <<'PY'
import re, sys
code = sys.argv[1]
def table(path):
    rows = []
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if line.startswith("|"):
            cells = [c.strip() for c in re.split(r"(?<!\\)\|", line.strip("|"))]
            if not all(re.fullmatch(r":?-+:?", c) for c in cells):
                rows.append(cells)
    return rows
cl = table("CHANGELOG.md")
if not cl or cl[0][:5] != ["Build", "Version", "Date", "APK", "Changes"]:
    sys.exit("CHANGELOG.md: table header must be | Build | Version | Date | APK | Changes |")
rows = cl[1:]
top = rows[0][0] if rows else "(none)"
if top != "b" + code:
    sys.exit("CHANGELOG.md top row is Build = %s, but app/build.gradle.kts has versionCode %s.\n"
             "Write the b%s row at the top of CHANGELOG.md first (docs/release.md, step 4)." % (top, code, code))
builds = {r[0] for r in rows}
cited = set(re.findall(r"\bb\d+\b", open("FEATURES.md", encoding="utf-8").read()))
missing = sorted(cited - builds, key=lambda b: int(b[1:]))
if missing:
    sys.exit("FEATURES.md cites builds missing from CHANGELOG.md: " + ", ".join(missing))
notes = rows[0][4] if len(rows[0]) > 4 else ""
notes = re.sub(r"\*\*|`", "", notes).replace("\\|", "|").strip()   # plain string, no markdown
if not notes:
    sys.exit("CHANGELOG.md: the Changes cell of b%s is empty" % code)
print(notes)
PY
)

if [ "$MODE" = "release" ]; then
  SRC=app/build/outputs/apk/release/app-release.apk   # Studio: Build Variants = release, then Assemble
else
  SRC=app/build/outputs/apk/debug/app-debug.apk   # from the Assemble button, NOT intermediates/ (Run output)
fi
[ -f "$SRC" ] || { echo "Missing $SRC. Build it first."; exit 1; }

mkdir -p dist
APK="dist/medusa-$NAME-b$CODE.apk"   # unique per build: same versionName can repeat, versionCode never does
cp "$SRC" "$APK"

python3 scripts/check_apk.py "$APK" --offline   # exits non-zero on any problem

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
    "notes": notes,
}, indent=2))
PY
echo
cat dist/latest.json
echo
echo "Ready: $APK + dist/latest.json. Publish both in ONE commit to the 'updates' branch (docs/release.md)."
