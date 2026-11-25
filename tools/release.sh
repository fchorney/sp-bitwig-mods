#!/bin/sh
# Build a mod's release zip, tag it, push to origin and nas, and publish a GitHub release.
#
#   tools/release.sh <mod-id>
#
# Needs the same variables as build_mod.sh plus PATCHBAY (BUILDING.md), and gh logged in.
# The version comes from <mod-id>/mod.json; the tag is <mod-id>-<version>. A version with a
# pre-release part (0.1.0-dev.1) becomes a GitHub pre-release.
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
ID=${1:?usage: tools/release.sh <mod-id>}
cd "$ROOT"
[ -f "$ID/mod.json" ] || { echo "no mod folder $ID" >&2; exit 1; }

[ -z "$(git status --porcelain)" ] || { echo "working tree not clean: commit first" >&2; exit 1; }
[ "$(git rev-parse --abbrev-ref HEAD)" = main ] || { echo "not on main" >&2; exit 1; }

eval "$(python3 -I - "$ID/mod.json" <<'EOF'
import json, shlex, sys
m = json.load(open(sys.argv[1])); mod = m["mod"]; b = m["built_against"]
for k, v in {"VERSION": mod["version"], "NAME": mod["name"], "DESC": mod["description"],
             "BUILT": f'{b["bitwig_version"]} {b["platform"]} {b["architecture"]}'}.items():
    print(f"{k}={shlex.quote(v)}")
EOF
)"
TAG="$ID-$VERSION"

git fetch -q origin --tags
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && { echo "tag $TAG already exists: bump the version in $ID/mod.json" >&2; exit 1; }
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || git push -q origin main

INSTALL=0 RELEASE=1 "$ROOT/tools/build_mod.sh" "$ID"
ZIP="$ROOT/build/releases/$TAG.zip"

# The zip's own checksums must hold.
CHECK="$ROOT/build/check/$ID"
rm -rf "$CHECK"; mkdir -p "$CHECK"
(cd "$CHECK" && unzip -q "$ZIP" && cd "$ID" && shasum -a 256 -c --quiet SHA256SUMS.txt)
SUMS="$ROOT/build/releases/SHA256SUMS-$TAG.txt"
(cd "$ROOT/build/releases" && shasum -a 256 "$TAG.zip" > "$SUMS")

git tag -a "$TAG" -m "$NAME $VERSION"
git push -q origin "$TAG"
git push -q nas

PRE=""
case "$VERSION" in *-*) PRE="--prerelease" ;; esac
NOTES="$DESC

Built against Bitwig $BUILT. Needs [Patchbay](https://github.com/shaboogen-dev/Patchbay): in Patchbay, **Add Mod…** and choose the zip.

See [$ID/README.md](https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/blob/$TAG/$ID/README.md)."
gh release create "$TAG" "$ZIP" "$SUMS" --title "$NAME $VERSION" --notes "$NOTES" $PRE
