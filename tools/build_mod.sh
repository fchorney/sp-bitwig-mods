#!/bin/sh
# Build one Patchbay (BML) mod from this repository.
#
#   tools/build_mod.sh <mod-id>            build and install into $BML/mods/<mod-id>
#   INSTALL=0 tools/build_mod.sh <mod-id>  build only
#   RELEASE=1 tools/build_mod.sh <mod-id>  also write build/releases/<mod-id>-<version>.zip
#
# Follows Patchbay's docs/sdk/building.md (manual route). See BUILDING.md for the variables.
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
ID=${1:?usage: tools/build_mod.sh <mod-id>}
MOD="$ROOT/$ID"
[ -f "$MOD/mod.json" ] || { echo "no mod folder $MOD" >&2; exit 1; }

: "${BML:?set BML to the Patchbay install folder (the one holding modkit/ and mods/)}"
: "${BITWIG_JAR:?set BITWIG_JAR to the bitwig.jar of the build in mod.json built_against}"
: "${LIBS_JAR:?set LIBS_JAR to the libs.jar next to it}"
KIT="$BML/modkit"
BUILD="$ROOT/build"
CACHE="$BUILD/cache"
WORK="$BUILD/work/$ID"
OUT="$BUILD/out/$ID"
JAR_SHA=$(shasum -a 256 "$BITWIG_JAR" | cut -c1-16)
COMPILE="$CACHE/compile-$JAR_SHA.jar"

rm -rf "$WORK" "$OUT"
mkdir -p "$CACHE" "$WORK/classes" "$OUT"
[ -f "$COMPILE" ] || java -jar "$KIT/bml-modkit.jar" compile-jar "$BITWIG_JAR" "$COMPILE"

# classpath_mods would need framework helper jars; none of our mods use one yet.
python3 -I -c 'import json,sys; d=json.load(open(sys.argv[1])); sys.exit("classpath_mods not supported by this script" if d.get("classpath_mods") else 0)' "$MOD/build.json"
RELEASE_LEVEL=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["release"])' "$MOD/build.json")

find "$MOD/src" -name '*.java' > "$WORK/sources.txt"
javac --release "$RELEASE_LEVEL" -proc:none -encoding UTF-8 -implicit:none -sourcepath "$MOD/src" \
  -cp "$COMPILE:$LIBS_JAR:$KIT/bml-api.jar" -d "$WORK/classes" @"$WORK/sources.txt"
# non-Java files under src/ go into helper.jar unchanged
(cd "$MOD/src" && find . -type f ! -name '*.java' -exec sh -c 'mkdir -p "$0/$(dirname "$1")" && cp "$1" "$0/$1"' "$WORK/classes" {} \;)

java -jar "$KIT/bml-modkit.jar" finish "$WORK/classes" "$WORK/hooks.json" "$BITWIG_JAR" "$LIBS_JAR"
jar --create --file "$OUT/helper.jar" -C "$WORK/classes" .

# The shipped mod.json: the source manifest plus the hooks, targets, helper and opens from hooks.json.
python3 -I - "$MOD/mod.json" "$WORK/hooks.json" "$OUT/mod.json" <<'EOF'
import json, re, sys
src, hooks_path, dst = sys.argv[1:]
m = json.load(open(src)); h = json.load(open(hooks_path))
targets = m.setdefault("targets", {}); m.setdefault("hooks", [])
def tid(obj):
    for k, v in targets.items():
        if v == obj: return k
    base = obj["class"] if obj["kind"] == "class" else obj["owner"] + "." + obj["name"]
    base = re.sub(r"[^A-Za-z0-9_.\-]", "", base.replace("/", ".").replace("$", "_"))
    k, n = base, 2
    while k in targets: k = f"{base}-{n}"; n += 1
    targets[k] = obj; return k
for hk in h["hooks"]:
    e = {"id": hk["id"], "target": tid(hk["target"]), "transform": hk["transform"]}
    if "returns" in hk: e["returns"] = hk["returns"]
    if "method_from" in hk:
        e["method"] = {"from_target": tid(hk["method_from"])}; e["body"] = {"template": hk["body"]}
    if "match" in hk:
        mt = hk["match"]
        e["match"] = ({"opcode": mt["opcode"], "target": tid(mt["target"]), "occurrence": mt["occurrence"]} if "target" in mt
                      else {k: mt[k] for k in ("opcode", "owner", "name", "descriptor", "occurrence") if k in mt})
    e["bridge"] = hk["bridge"]
    m["hooks"].append(e)
if h.get("hook_classes"): m["helper"] = {"jar": "helper.jar", "class": h["hook_classes"][0]}
if h.get("fields"): raise SystemExit("fields not handled by this script")
if h.get("access"): m["opens"] = sorted(tid(a) for a in h["access"])
json.dump(m, open(dst, "w"), indent=2); print("mod.json written:", len(m["hooks"]), "hooks,", len(m.get("opens", [])), "opens")
EOF

cp "$MOD/README.md" "$OUT/"
[ -d "$MOD/images" ] && cp -R "$MOD/images" "$OUT/"
[ -d "$MOD/package" ] && cp -R "$MOD/package/." "$OUT/"

if [ "${INSTALL:-1}" = 1 ]; then
  rm -rf "$BML/mods/$ID"
  cp -R "$OUT" "$BML/mods/$ID"
  echo "installed to $BML/mods/$ID"
fi

if [ "${RELEASE:-0}" = 1 ]; then
  # A release also ships the source, build.json, uses.tsv and SHA256SUMS.txt (Patchbay docs/sdk/mods.md).
  : "${PATCHBAY:?set PATCHBAY to a clone of the Patchbay repository (for tools/mod_uses.py)}"
  python3 -I "$PATCHBAY/tools/mod_uses.py" list "$OUT/helper.jar" "$BITWIG_JAR" > "$OUT/uses.tsv"
  cp -R "$MOD/src" "$OUT/src"; cp "$MOD/build.json" "$OUT/"
  (cd "$OUT" && find . -type f ! -name SHA256SUMS.txt | sed 's|^\./||' | LC_ALL=C sort | xargs shasum -a 256 > SHA256SUMS.txt)
  VERSION=$(python3 -I -c 'import json,sys; print(json.load(open(sys.argv[1]))["mod"]["version"])' "$MOD/mod.json")
  mkdir -p "$BUILD/releases"
  ZIP="$BUILD/releases/$ID-$VERSION.zip"
  rm -f "$ZIP"
  (cd "$BUILD/out" && zip -qrX "$ZIP" "$ID")
  echo "release: $ZIP"
fi
