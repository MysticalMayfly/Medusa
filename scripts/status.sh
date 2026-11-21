#!/bin/bash
# Prints Medusa's current versions. Docs never store them: run this instead.
#
#   scripts/status.sh
#
# Shows versionCode/versionName from app/build.gradle.kts, versionCode from the live latest.json,
# and the top build in CHANGELOG.md, and warns if they differ.
set -uo pipefail
cd "$(dirname "$0")/.."
NAME=$(grep -E '^[[:space:]]*versionName[[:space:]]*=' app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/')
CODE=$(grep -E '^[[:space:]]*versionCode[[:space:]]*=' app/build.gradle.kts | sed -E 's/[^0-9]*([0-9]+).*/\1/')
TOP=$(grep -m1 -E '^\|[[:space:]]*b[0-9]+[[:space:]]*\|' CHANGELOG.md | sed -E 's/^\|[[:space:]]*b([0-9]+).*/\1/')
LIVE=$(python3 - <<'PY'
import json, urllib.request
url = "https://raw.githubusercontent.com/MysticalMayfly/medusa/updates/latest.json"
try:
    req = urllib.request.Request(url, headers={"User-Agent": "medusa-status"})
    j = json.load(urllib.request.urlopen(req, timeout=15))
    print("%s %s" % (j["versionCode"], j["versionName"]))
except Exception as e:
    print("ERR %s" % e)
PY
)
LIVE_CODE=${LIVE%% *}
echo "app/build.gradle.kts : versionCode ${CODE:-?}, versionName ${NAME:-?}"
MISMATCH=0
if [ "$LIVE_CODE" = "ERR" ]; then
  echo "live latest.json     : live: unreachable here"   # e.g. the Mac VM; not counted as a mismatch
else
  echo "live latest.json     : versionCode $LIVE_CODE (versionName ${LIVE#* })"
  [ "$CODE" != "$LIVE_CODE" ] && MISMATCH=1
fi
echo "CHANGELOG.md top row : b${TOP:-?}"
[ "$CODE" != "$TOP" ] && MISMATCH=1
if [ "$MISMATCH" = 1 ]; then
  echo
  echo "WARNING: these differ."
  echo "  The live latest.json cache may lag after a publish, see docs/updater.md Caching."
  echo "  Mid-release it is normal for build.gradle.kts and CHANGELOG.md to be one build ahead of the live file."
fi
exit 0
