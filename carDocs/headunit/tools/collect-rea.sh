#!/usr/bin/env bash
# Collect REA static evidence from the pulled head-unit artifacts into
# carDocs/headunit/evidence/<name>/{package,classes}.json
#
# Requires REA's separately-supplied JADX engine (audited release, SHA-256
# 6e5eacf500b64292bfb73c49797c1958f6ee44646e43e868039ae7feb573ff75):
#   curl -L -o ~/.cache/rea/jadx-headless-mcp-0.7.1-all.jar \
#     https://github.com/1013503897/jadx-headless-mcp/releases/download/v0.7.1/jadx-headless-mcp-0.7.1-all.jar
#
# Framework jars on this head unit are DEX-in-JAR; wrap each classes.dex into a
# throwaway .apk so REA's Android provider can load it (see wrap_framework).
set -uo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"          # carDocs/headunit
ROOT="$(cd "$HERE/../extracted/system" && pwd)"   # carDocs/extracted/system
OUT="$HERE/evidence"
WRAP="${WRAP_DIR:-/tmp/headunit-wrap}"
REA="npx -y rea-agents@6.4.0"
export REA_JADX_MCP_JAR="${REA_JADX_MCP_JAR:-$HOME/.cache/rea/jadx-headless-mcp-0.7.1-all.jar}"

mkdir -p "$OUT" "$WRAP"

nonempty() { [ -s "$1" ] && [ "$(wc -c < "$1" | tr -d ' ')" -gt 8 ]; }

collect() { # name apk-path
  local name="$1"
  local apk="$2"
  local dir="$OUT/$name"
  mkdir -p "$dir"
  if ! nonempty "$dir/package.json"; then
    $REA inspect-android-package "$apk" --format json --filter-output normalized_result > "$dir/package.json" 2>"$dir/package.err"
    nonempty "$dir/package.json" || echo "FAIL pkg  $name: $(head -c 200 "$dir/package.err")"
  fi
  if ! nonempty "$dir/classes.json"; then
    $REA search-android-classes "$apk" "" --format json --filter-output normalized_result > "$dir/classes.json" 2>"$dir/classes.err"
    nonempty "$dir/classes.json" || echo "FAIL cls  $name: $(head -c 200 "$dir/classes.err")"
  fi
  echo "ok  $name ($(wc -c < "$dir/classes.json" 2>/dev/null | tr -d ' ') bytes)"
}

wrap_framework() { # jar-basename  -> $WRAP/<basename>.apk
  local j="$1" d="$WRAP/$j"
  rm -rf "$d"; mkdir -p "$d"
  ( cd "$d" && unzip -o -q "$ROOT/framework/$j.jar" 'classes*.dex' )
  ( cd "$d" && zip -q -X "$WRAP/$j.apk" classes*.dex )
}

APPS=(
  system-priv-app/DIMInteraction/DIMInteraction.apk
  system-app/OneOS-DIMService/OneOS-DIMService.apk
  system-app/OneOS-CarLink/OneOS-CarLink.apk
  system-priv-app/OneOS-Hicar/OneOS-Hicar.apk
  system-priv-app/FlymeLink/FlymeLink.apk
  system-priv-app/Starrynet/Starrynet.apk
  system-app/OneOS-InputService/OneOS-InputService.apk
  system-priv-app/AudioExtService/AudioExtService.apk
  system-priv-app/TsMicphoneService/TsMicphoneService.apk
  system-priv-app/OneOSPermissionService/OneOSPermissionService.apk
  system-priv-app/OneOS-NaviService/OneOS-NaviService.apk
  system-priv-app/ECarXCarService/ECarXCarService.apk
  system-priv-app/MultiWindowService/MultiWindowService.apk
  system-priv-app/MultiWindowAnimator/MultiWindowAnimator.apk
  system-priv-app/CarService/CarService.apk
  system-app/CarProxyService/CarProxyService.apk
  vendor-app/GeelyMap/GeelyMap.apk
  vendor-app/ThunderCarplay/ThunderCarplay.apk
  system-app/OneOS-BtService/OneOS-BtService.apk
  system-app/PSDBluetoothService/PSDBluetoothService.apk
  system-app/OneOS-ApiService/OneOS-ApiService.apk
  system-app/OneOS-Settings/OneOS-Settings.apk
  system-priv-app/OneOS-Launcher3/OneOS-Launcher3.apk
  system-priv-app/OneOS-SystemUIPlugin/OneOS-SystemUIPlugin.apk
  system-app/OneOS-UsbService/OneOS-UsbService.apk
)

for a in "${APPS[@]}"; do
  name="$(basename "$a" .apk)"
  collect "$name" "$ROOT/$a"
done

for j in ecarx.car ecarx.adaptapi desaysv-multi-display desay.projection \
         desaysv-phonelinklib desaysv-phonelinkadaptor autolink.adapterservice \
         car-frameworks-service dsv-system-gesture ecarx-carservice-starter \
         tcmiface desaysv-car-frameworks-service-extension; do
  wrap_framework "$j"
  collect "fw-$j" "$WRAP/$j.apk"
done

echo "done -> $OUT"
