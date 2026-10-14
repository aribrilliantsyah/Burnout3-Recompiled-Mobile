#!/usr/bin/env bash
# Builds a release APK on this machine and publishes it as a GitHub Release.
#
#   scripts/release.sh patch      1.0.1 -> 1.0.2
#   scripts/release.sh minor      1.0.2 -> 1.1.0
#   scripts/release.sh major      1.1.0 -> 2.0.0
#   scripts/release.sh 1.4.0      exactly this version
#   scripts/release.sh --current  release the version already in the file
#                                 (the first release: 1.0.0)
#
# Options:
#   --no-native     reuse the libmain.so already built (Java-only changes)
#   --no-publish    build, commit and tag, but push/upload nothing
#   --draft         make the GitHub release a draft
#
# Steps: check the tree is clean -> new version into
# android/version.properties -> CHANGELOG [Unreleased] becomes the version
# section -> native + signed release APK -> dist/b3x-<v>.apk + .sha256 ->
# commit "Release <v>", tag v<v> -> push -> GitHub Release with the APK.
#
# Why local: libmain.so is compiled from C lifted out of default.xbe, which
# only exists here (roms/); the lifted code is never committed. GitHub's
# workflow (.github/workflows/release.yml) checks the tag and the notes.
#
# Needs: android/keystore.properties (scripts/android/keystore.sh), and to
# publish, the GitHub CLI logged in (gh auth login) or GITHUB_TOKEN set.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE"
VFILE=android/version.properties

die() { echo "[release] error: $*" >&2; exit 1; }
say() { echo "[release] $*"; }

BUMP="" NATIVE=1 PUBLISH=1 DRAFT=""
for a in "$@"; do
    case "$a" in
        patch|minor|major|--current) BUMP="$a" ;;
        --no-native) NATIVE=0 ;;
        --no-publish) PUBLISH=0 ;;
        --draft) DRAFT=--draft ;;
        [0-9]*.[0-9]*.[0-9]*) BUMP="$a" ;;
        -h|--help) sed -n '2,27p' "$0"; exit 0 ;;
        *) die "unknown argument: $a (see --help)" ;;
    esac
done
[ -n "$BUMP" ] || die "say patch, minor, major, a version (1.2.0) or --current"

# ------------------------------------------------------------ version
CUR="$(sed -n 's/^VERSION_NAME=//p' "$VFILE")"
[[ "$CUR" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || die "bad VERSION_NAME '$CUR' in $VFILE"
MA=${BASH_REMATCH[1]} MI=${BASH_REMATCH[2]} PA=${BASH_REMATCH[3]}
case "$BUMP" in
    patch) NEW="$MA.$MI.$((PA + 1))" ;;
    minor) NEW="$MA.$((MI + 1)).0" ;;
    major) NEW="$((MA + 1)).0.0" ;;
    --current) NEW="$CUR" ;;
    *) NEW="$BUMP" ;;
esac
[[ "$NEW" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] || die "bad version '$NEW'"
(( BASH_REMATCH[2] < 100 && BASH_REMATCH[3] < 100 )) || die "minor and patch must be < 100 (versionCode)"
if [ "$BUMP" != --current ]; then
    newest="$(printf '%s\n%s\n' "$CUR" "$NEW" | sort -V | tail -1)"
    [ "$newest" = "$NEW" ] && [ "$NEW" != "$CUR" ] || die "$NEW is not newer than $CUR"
fi
TAG="v$NEW"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && die "tag $TAG already exists"

# ------------------------------------------------------------ checks
[ -z "$(git status --porcelain)" ] || die "commit or stash your changes first"
[ -f android/keystore.properties ] || die "no release key: run scripts/android/keystore.sh"
[ -f roms/game/default.xbe ] || die "roms/game/default.xbe missing (needed to build libmain.so)"
if [ "$PUBLISH" = 1 ]; then
    git remote get-url origin >/dev/null 2>&1 || die "no git remote 'origin' (or use --no-publish)"
    command -v gh >/dev/null || die "GitHub CLI 'gh' not installed (or use --no-publish)"
    gh auth status >/dev/null 2>&1 || [ -n "${GITHUB_TOKEN:-}" ] || die "gh not logged in: gh auth login"
fi
say "$CUR -> $NEW ($TAG)"

# ------------------------------------------------------------ files
sed -i "s/^VERSION_NAME=.*/VERSION_NAME=$NEW/" "$VFILE"
TODAY="$(date +%Y-%m-%d)"
NOTES="$(mktemp)"
python3 - "$NEW" "$TODAY" "$NOTES" <<'EOF'
import re, sys
new, today, notes_path = sys.argv[1:4]
p = "CHANGELOG.md"
s = open(p, encoding="utf-8").read()
m = re.search(r"^## \[Unreleased\]\n(.*?)(?=^## \[|\Z)", s, re.S | re.M)
body = m.group(1).strip() if m else ""
if not body:
    body = "- Maintenance release."
open(notes_path, "w", encoding="utf-8").write(body + "\n")
section = "## [Unreleased]\n\n## [%s] - %s\n\n%s\n\n" % (new, today, body)
if m:
    s = s[:m.start()] + section + s[m.end():]
else:
    i = s.find("\n## ")
    s = s[:i + 1] + section + s[i + 1:] if i >= 0 else s + "\n" + section
open(p, "w", encoding="utf-8").write(s)
EOF

# ------------------------------------------------------------ build
CMDS=(apk)
[ "$NATIVE" = 1 ] && CMDS=(native apk)
say "building: ${CMDS[*]} (release)"
scripts/android/b3x.sh --release "${CMDS[@]}"
APK=android/app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || die "no $APK"

BT="$(ls -d "${ANDROID_HOME:-$HOME/Documents/Repos/Android/SDK}"/build-tools/* | sort -V | tail -1)"
"$BT/apksigner" verify --print-certs "$APK" | grep -q "CN=b3x" \
    || die "$APK is not signed with the release key"
"$BT/aapt2" dump badging "$APK" | grep -q "versionName='$NEW'" \
    || die "$APK does not carry versionName $NEW"

mkdir -p dist
OUT="dist/b3x-$NEW.apk"
cp "$APK" "$OUT"
(cd dist && sha256sum "b3x-$NEW.apk" > "b3x-$NEW.apk.sha256")
say "$OUT ($(du -h "$OUT" | cut -f1)), $(cut -c1-16 "$OUT.sha256")…"

# ------------------------------------------------------------ git
git add "$VFILE" CHANGELOG.md
git commit -q -m "Release $NEW"
git tag -a "$TAG" -m "b3x $NEW"
say "committed and tagged $TAG"

if [ "$PUBLISH" = 0 ]; then
    say "--no-publish: not pushed. Later: git push origin HEAD $TAG && gh release create $TAG $OUT $OUT.sha256 -F $NOTES"
    exit 0
fi

git push origin HEAD "$TAG"
{
    cat "$NOTES"
    printf '\n---\n\nInstall **b3x-%s.apk** (arm64, Android 11+, Vulkan). You need your own\n' "$NEW"
    printf 'Burnout 3: Takedown (Xbox, NTSC-U) disc image: open the app, *INSTALL FROM ISO…*.\n'
    printf 'No game data is included.\n\nSHA-256: `%s`\n' "$(cut -d' ' -f1 "$OUT.sha256")"
} > "$NOTES.md"
gh release create "$TAG" "$OUT" "$OUT.sha256" --title "b3x $NEW" -F "$NOTES.md" $DRAFT
say "published $TAG"
