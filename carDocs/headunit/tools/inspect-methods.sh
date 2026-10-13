#!/usr/bin/env bash
# Decompile the specific methods that reveal service names, permissions and
# gating for the capabilities DiPlay might use. Output: evidence/method/<name>.json
set -uo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$HERE/evidence/method"
WRAP="${WRAP_DIR:-/tmp/headunit-wrap}"
REA="npx -y rea-agents@6.4.0"
export REA_JADX_MCP_JAR="${REA_JADX_MCP_JAR:-$HOME/.cache/rea/jadx-headless-mcp-0.7.1-all.jar}"
mkdir -p "$OUT"

method() { # out-name apk class method [overload]
  local name="$1"; local apk="$2"; local class="$3"; local m="$4"; local ov="${5:-}"
  local f="$OUT/$name.json"
  [ -s "$f" ] && [ "$(wc -c < "$f")" -gt 8 ] && { echo "skip $name"; return; }
  local args=(inspect-android-method "$apk" "$class" "$m" --format json --filter-output normalized_result)
  [ -n "$ov" ] && args+=(--overload-index "$ov")
  $REA "${args[@]}" > "$f" 2>"$OUT/$name.err"
  local n=$(wc -c < "$f" | tr -d ' ')
  [ "$n" -gt 8 ] && echo "ok   $name ($n)" || echo "FAIL $name: $(head -c 160 "$OUT/$name.err")"
}

method project.startProjectService        "$WRAP/desay.projection.apk" com.desaysv.ivi.vds.projection.DesaySVProjectManager startProjectService
method project.clinit                     "$WRAP/desay.projection.apk" com.desaysv.ivi.vds.projection.DesaySVProjectManager "<clinit>"
method wifi.ExternalWifiManagerImpl_create "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.wifiap.ExternalWifiManagerImpl create 0
method wifi.ExternalWifiManager_create     "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.wifiap.ExternalWifiManager create 0
method input.InputImpl_requestKeysInterception "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.input.InputImpl requestKeysInterception
method input.Input_create                  "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.input.Input create 0
method hardkey.requestHardKeyEvent         "$WRAP/ecarx.car.apk" ecarx.car.ECarXCarHardKeyManager requestHardKeyEvent
method hardkey.checkHardKeyService         "$WRAP/ecarx.car.apk" ecarx.car.ECarXCarHardKeyManager checkHardKeyService
method multidisplay.create                 "$WRAP/ecarx.adaptapi.apk" ecarx.multidisplay.EcarxMultidisplayManager create 0
method hudimpl.create                      "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.hudinteraction.HUDInteractionImpl create 0
method hud.requestHUDMode                  "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.hudinteraction.HUDInteractionImpl requestHUDMode
method policy.PolicyImpl_create            "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.policy.PolicyImp create 0method phonelinkadaptor.getInstance        "$WRAP/desaysv-phonelinkadaptor.apk" com.desaysv.ivi.phoneLinkplatformadaptorlib.PhoneLinkAdaptorLibManager getInstance
method carplay.addCarplayCallback          "$WRAP/desaysv-phonelinklib.apk" com.desaysv.ivi.phonelinkmanager.carplay.ICarplayManager addCarplayCallback
method ecareNavi.interfaceCall             "$WRAP/autolink.adapterservice.apk" ecarx.fw.api.diminteraction.EcarxNaviInteraction updateETA

echo "done -> $OUT"
