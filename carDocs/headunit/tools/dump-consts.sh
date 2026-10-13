#!/usr/bin/env bash
# Dump static-field constants (values included) for selected classes using the
# SDK's dexdump. Framework jars are DEX-in-JAR, so it unzips first.
# Output: carDocs/headunit/evidence/consts/<name>.txt
#
# This is an ordinary-tool observation (dexdump), not REA Evidence.
set -uo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$HERE/evidence/consts"
SYS="$(cd "$HERE/../extracted/system" && pwd)"
DEXDUMP="${DEXDUMP:-/Users/machete/Library/Android/sdk/build-tools/37.0.0/dexdump}"
WORK=/tmp/headunit-dex
mkdir -p "$OUT" "$WORK"

dump() { # out-name dex-dir descriptor
  local name="$1" dir="$2" desc="$3"
  local f="$OUT/$name.txt"
  [ -s "$f" ] && { echo "skip $name"; return; }
  : > "$f"
  for d in "$dir"/classes*.dex; do
    [ -e "$d" ] || continue
    "$DEXDUMP" "$d" 2>/dev/null | \
      awk -v d="$desc" 'index($0,"Class descriptor")&&index($0,d){p=1} p{print} p&&/^  Direct methods/{exit}' >> "$f"
  done
  echo "$name -> $(wc -l < "$f") lines"
}

# prepare unchanged dex trees
rm -rf "$WORK/fw"; mkdir -p "$WORK/fw"
for j in ecarx.car ecarx.adaptapi desay.projection desaysv-multi-display autolink.adapterservice; do
  mkdir -p "$WORK/fw/$j"; (cd "$WORK/fw/$j" && unzip -o -q "$SYS/framework/$j.jar" 'classes*.dex' 2>/dev/null)
done
rm -rf "$WORK/navi"; mkdir -p "$WORK/navi"; (cd "$WORK/navi" && unzip -o -q "$SYS/system-priv-app/OneOS-NaviService/OneOS-NaviService.apk" 'classes*.dex' 2>/dev/null)

dump keycode.KeyCodeForFW            "$WORK/fw/ecarx.car"          "Lecarx/car/KeyCodeForFW;"
dump keycode.adaptapi.KeyCode        "$WORK/fw/ecarx.adaptapi"     "Lcom/ecarx/xui/adaptapi/input/KeyCode;"
dump ecarx.IHardKey_consts           "$WORK/fw/ecarx.car"          "Lecarx/car/IHardKey;"
dump ecarx.ECarXCarHardKeyManager    "$WORK/fw/ecarx.car"          "Lecarx/car/ECarXCarHardKeyManager;"
dump dim.EcarxNaviInteraction        "$WORK/fw/autolink.adapterservice" "Lecarx/fw/api/diminteraction/EcarxNaviInteraction;"
dump hud.IHUD                        "$WORK/fw/ecarx.adaptapi"     "Lcom/ecarx/xui/adaptapi/car/vehicle/IHUD;"
dump hud.HUDInteraction              "$WORK/fw/ecarx.adaptapi"     "Lcom/ecarx/xui/adaptapi/hudinteraction/HUDInteraction;"
dump multidisplay.IEcarxMultidisplay  "$WORK/fw/ecarx.adaptapi"     "Lecarx/multidisplay/IEcarxMultidisplay;"
dump splitscreen.EcarxSplitScreen     "$WORK/fw/ecarx.adaptapi"     "Lecarx/splitscreen/EcarxSplitScreen;"
dump projection.DesaySVProjectManager "$WORK/fw/desay.projection"  "Lcom/desaysv/ivi/vds/projection/DesaySVProjectManager;"
dump projection.IProject              "$WORK/fw/desay.projection"  "Lcom/desaysv/ivi/projection/IProject;"
dump navi.GeelyAutoProtocolID         "$WORK/navi"                  "Lcom/geely/map/mapsdk/geelyprotocol/GeelyAutoProtocolID;"
dump navi.ProtocolID                  "$WORK/navi"                  "Lcom/geely/map/jsonsdk/ProtocolID;"
dump navi.ScreenStatusTypes           "$WORK/navi"                  "Lcom/geely/lib/oneosapi/navi/model/base/ScreenStatusTypes;"

echo "done -> $OUT"
