# Medusa: start here (every Claude session)

## Reading order

1. `docs/RULES.md`: every session.
2. The top rows of `CHANGELOG.md`: before any change.
3. `FEATURES.md`: before shipping.
4. When the task needs them:
   - `docs/setup.md`: the Mac, toolchain, Android Studio, sandbox, GitHub, TV and adb.
   - `docs/release.md`: the checklist from code change to publish to syncing `main`.
   - `docs/updater.md`: how the updater works and the frozen `latest.json` format.

## Session behaviour

- Work on the Mac folder through the device bridge (`device_bash`, path `$HOME/mnt/Medusa`). The cloud sandbox is only for git pushes to GitHub.
- Edit docs only in the Mac folder. The sandbox clone is for pushing only.
- Deleting files in the Mac folder needs the user's permission (bridge rule). Overwriting is fine.
- Never block the chat on long waits (over ~60 s) with no output. Say what you are waiting for (e.g. the raw GitHub cache, which may lag, see `docs/updater.md` Caching) and check back.
- After a publish, edit only `CHANGELOG.md` and, if a feature's status changed, `FEATURES.md`.
- To see current versions, run `scripts/status.sh`. Never write version state into docs.
