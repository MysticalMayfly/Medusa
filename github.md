# Medusa: GitHub

Read `rules.md` first.

## Repo layout

| | |
|---|---|
| Repo | `https://github.com/MysticalMayfly/Medusa` (**public**, must stay public) |
| Clone URL used | `https://github.com/MysticalMayfly/medusa` (lowercase works) |
| `main` | Source code. Mirror of the Mac project minus git-ignored files. |
| `updates` | **Published update channel.** Only `latest.json` + the current `medusa-<versionName>.apk`. Linear history, one commit per publish. |
| Releases / tags | Not used (see constraints). |

Never: force-push, delete or rename either branch, rename/transfer/delete the repo, or make it private. Installed apps read
`https://raw.githubusercontent.com/MysticalMayfly/medusa/updates/latest.json` forever.

## Constraints in Claude sessions (learned 2026-11-20)

- `gh repo create` from the sandbox: **blocked**. The repo was created by hand in the built-in browser (user signed in to GitHub there).
- GitHub **Releases** API from the sandbox: **403 "not permitted for this session type"**. That is why the `updates` branch exists. Do not switch the channel to Releases.
- Push access: call `add_repo` with owner `MysticalMayfly`, repo `Medusa`, access `push`. Then plain `git push` over HTTPS works from the sandbox.
- The Mac project folder is **not** a git repo (git inside the bridged folder breaks on lock-file deletes). All git work happens in a sandbox clone.
- Commit identity used: `MysticalMayfly <MysticalMayfly@users.noreply.github.com>`, plus the session's attribution trailer lines.

## Publish an update (sandbox)

Prerequisite: `scripts/release.sh` PASSED and produced `dist/medusa-<V>.apk` + `dist/latest.json` (see `build.md`).

1. Stage both files from the Mac into the sandbox:
   `device_stage_files` with
   `/Users/mysticalmirage/AndroidStudioProjects/Medusa/dist/medusa-<V>.apk` and `.../dist/latest.json`
   (they arrive under `/mnt/user-data/uploads/Medusa/dist/`; wait ~2 s).
2. Commit them to `updates` in **one** commit:
   ```bash
   git clone --depth 1 -b updates https://github.com/MysticalMayfly/medusa /home/claude/upd
   cd /home/claude/upd
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
3. Verify (raw CDN caches up to ~5 min; check about once a minute, don't block the chat):
   ```bash
   curl -sS https://raw.githubusercontent.com/MysticalMayfly/medusa/updates/latest.json
   ```
   Done when it shows the new versionCode. Then the TV picks it up on next launch or on "Check for updates".

## Sync source to `main`

Run after every publish (and whenever code changes worth keeping).

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
   cd /home/claude/medusa && tar xzf /mnt/user-data/uploads/Medusa/dist/src.tgz
   git status --short            # review: no .jks, no keystore.properties, no dist/
   git add -A && git -c user.name="MysticalMayfly" -c user.email="MysticalMayfly@users.noreply.github.com" commit -m "<what changed>"
   git -c push.negotiate=true push origin main
   ```
   Note: extracting only adds/overwrites. If a file was deleted on the Mac, `git rm` it in the clone too.

## Publishing from the Mac terminal (alternative, not set up yet)

Possible if the user has git + GitHub credentials on the Mac: clone `updates` to a folder **outside** the project, then the same steps as above. Never run `git init` inside the project folder without the user's say-so.

## Secrets checklist (before any push to main)

- Not present in the commit: `release.jks`, any `*.jks`, `keystore.properties`, `local.properties`, `dist/`.
- `.gitignore` in the project root lists all of them. Do not remove those lines.
