# Medusa: Release

One checklist from code change to publish to syncing `main`. Rules: `docs/RULES.md`.

## Checklist (every update)

0. Reading order from `CLAUDE.md` done (top rows of `CHANGELOG.md`, `FEATURES.md` for the status of what you are about to ship).
1. Make the code change in the Mac folder.
2. In `app/build.gradle.kts` set `versionCode = <published + 1>` (`scripts/status.sh` prints the published one) and `versionName` only for a major update (Prince decides; otherwise keep it).
3. Studio: **Sync** (if Gradle files changed). In the **Build Variants** panel set `app` to **release**, then press **Assemble**. Wait for a new timestamp on `app/build/outputs/apk/release/app-release.apk`. (Release builds from b8 on: the debug APK is debuggable and unoptimized, and Compose runs many times slower on the TV. Switch back to **debug** for everyday Run on the emulator.)
4. Write the new build's row at the top of `CHANGELOG.md`: Build `b<versionCode>`, Version `<versionName>`, Date (IST), APK (file name per `docs/RULES.md` rule 4), Changes. The Changes cell becomes `notes` in `latest.json`, so write it as plain text.
5. `bash scripts/release.sh` (from the project root, Mac or sandbox; `release` is the default, `debug` is the old path).
   - Stops unless the top `CHANGELOG.md` row is `b<versionCode>` and every build cited in `FEATURES.md` has a `CHANGELOG.md` row.
   - Copies the APK to `dist/` (named per `docs/RULES.md` rule 4), runs `check_apk.py --offline`, writes `dist/latest.json` from the APK, with `notes` from the Changes cell.
   - Any FAIL = stop and fix. Never edit latest.json by hand to get around it.
   - It runs `check_apk.py` with `--offline` (the Mac VM has no network to the live file, see `docs/setup.md` Sandbox). The "versionCode higher than published" check happens in the publish step instead.
6. If the change touches anything in `docs/RULES.md` rule 13: run the **update-loop test** below.
7. Publish both files to `updates` ("Publish to `updates`" below).
8. Verify the live `latest.json` (step 3 of that section).
9. Sync source to `main` ("Sync source to `main`" below).
10. Update `FEATURES.md` only if a feature's status changed.

## Publish to `updates` (sandbox)

Prerequisite: step 5 (`scripts/release.sh`) PASSED and produced `dist/medusa-<V>.apk` (the name `release.sh` printed) + `dist/latest.json`.

1. Stage three files from the Mac into the sandbox:
   `device_stage_files` with
   `/Users/mysticalmirage/AndroidStudioProjects/Medusa/dist/medusa-<V>.apk`, `.../dist/latest.json` and `.../CHANGELOG.md`
   (they arrive under `/mnt/user-data/uploads/Medusa/`; wait ~2 s).
2. Check against what is published now, then commit to `updates` in **one** commit:
   ```bash
   git clone --depth 1 -b updates https://github.com/MysticalMayfly/medusa /home/claude/upd
   cd /home/claude/upd
   # stop unless the new build is higher than the published one and CHANGELOG.md matches both
   python3 - latest.json /mnt/user-data/uploads/Medusa/dist/latest.json /mnt/user-data/uploads/Medusa/CHANGELOG.md <<'PY' || exit 1
   import json, re, sys
   old = json.load(open(sys.argv[1]))["versionCode"]   # published now (latest.json in the updates clone)
   new = json.load(open(sys.argv[2]))["versionCode"]   # the new APK's versionCode (release.sh read it from the APK)
   rows = re.findall(r"^\|\s*(b\d+)\s*\|", open(sys.argv[3]).read(), re.M)
   if new <= old: sys.exit("STOP: new versionCode %s is not higher than published %s" % (new, old))
   if rows[:1] != ["b%d" % new]: sys.exit("STOP: CHANGELOG.md top row is %s, expected b%d" % (rows[:1], new))
   if rows[1:2] != ["b%d" % old]: sys.exit("STOP: CHANGELOG.md second row is %s, expected b%d (the published build)" % (rows[1:2], old))
   print("ok: b%d -> b%d" % (old, new))
   PY
   OLD=$(ls medusa-*.apk)                     # the previous APK
   git rm -q $OLD
   cp /mnt/user-data/uploads/Medusa/dist/medusa-<V>.apk /mnt/user-data/uploads/Medusa/dist/latest.json .
   # re-verify inside the sandbox before pushing
   python3 - <<'PY'
   import json, hashlib, glob
   j = json.load(open("latest.json")); apk = glob.glob("medusa-*.apk"); assert len(apk) == 1, apk
   assert j["apkUrl"].endswith("/updates/" + apk[0]), j["apkUrl"]
   assert j["sha256"] == hashlib.sha256(open(apk[0], "rb").read()).hexdigest()
   print("ok", j["versionCode"], j["versionName"])
   PY
   git add -A
   git -c user.name="MysticalMayfly" -c user.email="MysticalMayfly@users.noreply.github.com" commit -m "Publish Medusa <V>"
   git -c push.negotiate=true push origin updates
   ```
   Removing the old APK is safe: installed apps only ever download the APK named in the current latest.json.
3. Verify (the raw CDN cache may lag, see `docs/updater.md` Caching; check about once a minute, don't block the chat):
   ```bash
   curl -sS https://raw.githubusercontent.com/MysticalMayfly/medusa/updates/latest.json
   ```
   Done when it shows the new versionCode. Then the TV picks it up on next launch or on "Check for updates".

## Sync source to `main`

Run after every publish (step 9), and whenever code changes worth keeping.

1. In the Mac folder (via `device_bash`), pack only tracked files, honouring `.gitignore`:
   ```bash
   rm -rf $HOME/stage && mkdir -p $HOME/stage && cd $HOME/mnt/Medusa
   tar --exclude=./build --exclude=./app/build --exclude=./.gradle --exclude=./.kotlin --exclude=./dist -cf - . | (cd $HOME/stage && tar xf -)
   cd $HOME/stage && git init -q && git add -A && git ls-files > /tmp/files.txt
   grep -E "\.jks|keystore\.properties|local\.properties" /tmp/files.txt && echo "SECRET FOUND - STOP"
   tar -czf $HOME/mnt/Medusa/dist/src.tgz -T /tmp/files.txt
   ```
   If "SECRET FOUND" prints: stop, fix `.gitignore`, never push.
2. Stage `dist/src.tgz`, then in the sandbox:
   ```bash
   git clone --depth 1 https://github.com/MysticalMayfly/medusa /home/claude/medusa
   cd /home/claude/medusa
   git ls-files -z | xargs -0 rm -f   # exact copy: files deleted on the Mac disappear from main too
   tar xzf /mnt/user-data/uploads/Medusa/dist/src.tgz
   git add -A
   git status --short            # review: no .jks, no keystore.properties, no dist/; deletions must be intended
   git -c user.name="MysticalMayfly" -c user.email="MysticalMayfly@users.noreply.github.com" commit -m "<what changed>"
   git -c push.negotiate=true push origin main
   ```

## Publishing from the Mac terminal (alternative, not set up yet)

Possible if the user has git + GitHub credentials on the Mac: clone `updates` to a folder **outside** the project, then the same steps as above. Never run `git init` inside the project folder without the user's say-so.

## Tests and tools

### Update-loop test (emulator)

Goal: prove the **currently published** version can update to the **new** one.

1. Make sure the emulator runs the currently published version. If not, install it: build that version with Run, or install its APK.
2. Bump the version, set Build Variants to **release** and press **Assemble** (not Run: Run would install the new version directly and skip the test).
3. Write its `CHANGELOG.md` row, run `bash scripts/release.sh`, then publish to `updates` ("Publish to `updates`" above).
4. Wait until the raw latest.json shows the new versionCode (the cache may lag, see `docs/updater.md` Caching).
5. In Studio's Running Devices pane, use the on-screen D-pad: open Settings (gear, top right), focus "Check for updates", press the centre button (OK). Accept the install prompt if shown.
6. Reopen Medusa and open Settings: the version line must show the new version and build.
7. Read Logcat (tag `MedusaUpdater`) if anything fails.

If a change might break updating, publish a throwaway test build first (one build as the test, the next build as the real one) rather than risk the TV.

### scripts/check_apk.py

`python3 scripts/check_apk.py dist/<APK> [--offline]` (`release.sh` uses `--offline`)

Checks, from the APK itself: package = `com.priyabrat.medusa`, not `testOnly`, signer cert SHA-256 = the pinned key, versionCode present, and (online) versionCode higher than the live `latest.json`. Exit 0 = PASS. Also prints versionName, debuggable flag and the file SHA-256.
