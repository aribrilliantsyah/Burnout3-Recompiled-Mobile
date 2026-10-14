# Releasing b3x

The APK is built on the maintainer's machine. `libmain.so` is compiled from
C lifted out of the game's `default.xbe` (in `roms/`, never committed), so a
GitHub runner cannot build it. GitHub only checks each release
(`.github/workflows/release.yml`).

## What stays private

| File | Where | In git? |
|---|---|---|
| Release key `b3x-release.jks` | `~/.android-keys/` (outside the repo) | never |
| `android/keystore.properties` (key path + password) | repo, git-ignored | never |
| `dist/` (built APKs) | repo, git-ignored | never; uploaded to the Release |
| `roms/`, `.work/gen/` | repo, git-ignored | never |

Check before a push: `git check-ignore -v android/keystore.properties` must
print the `.gitignore` rule, and `git status` must not list any of these.

**Back up `~/.android-keys/b3x-release.jks` and its password** (password
manager, a second drive). Every update has to be signed with the same key;
with a lost key, phones refuse to install the next version over the old one.

## Once

1. Release key, in your own terminal (you type the password):
   ```sh
   scripts/android/keystore.sh
   ```
2. GitHub CLI:
   ```sh
   sudo pacman -S github-cli
   gh auth login
   ```
3. Remote and first push:
   ```sh
   git remote add origin https://github.com/<user>/<repo>.git
   git push -u origin main
   ```

## Every release

1. Write what changed under `## [Unreleased]` in `CHANGELOG.md` (it becomes
   the release notes), commit everything.
2. Run one of:
   ```sh
   scripts/release.sh --current   # the first release: 1.0.0, as in the file
   scripts/release.sh patch       # 1.0.1 -> 1.0.2
   scripts/release.sh minor       # 1.0.2 -> 1.1.0
   scripts/release.sh major       # 1.1.0 -> 2.0.0
   scripts/release.sh 1.4.0       # an explicit version
   ```
   `--no-native` reuses the last `libmain.so` (Java-only changes, ~10 min
   faster), `--no-publish` stops after the commit and tag, `--draft` makes a
   draft release.
3. The script: version into `android/version.properties` (versionCode =
   major·10000 + minor·100 + patch) → CHANGELOG section → native + signed
   release APK → checks the signer and versionName → `dist/b3x-<v>.apk` +
   `.sha256` → commit `Release <v>`, tag `v<v>` → push → GitHub Release with
   the APK.
4. On GitHub, the `release` workflow checks the tag, the changelog, the
   checksum, the signature and the version.

After `--no-publish`, publish by hand:
```sh
git push origin HEAD v<v>
gh release create v<v> dist/b3x-<v>.apk dist/b3x-<v>.apk.sha256 --title "b3x <v>" --notes-file <notes>
```

## Phones with a development build

Debug builds (`scripts/android/b3x.sh apk`) are signed with the debug key; a
release APK cannot be installed over one (and the other way round). Back up
the saves (`Android/data/com.ryuna.b3x/files/game/UDATA`), uninstall, then
install the release.
