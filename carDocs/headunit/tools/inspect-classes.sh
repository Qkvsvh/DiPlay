#!/usr/bin/env bash
# Inspect member inventories for the high-value classes discovered by
# collect-rea.sh. Output: carDocs/headunit/evidence/inspect/<name>.json
set -uo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$HERE/evidence/inspect"
WRAP="${WRAP_DIR:-/tmp/headunit-wrap}"
REA="npx -y rea-agents@6.4.0"
export REA_JADX_MCP_JAR="${REA_JADX_MCP_JAR:-$HOME/.cache/rea/jadx-headless-mcp-0.7.1-all.jar}"
mkdir -p "$OUT"

inspect() { # out-name apk class
  local name="$1"
  local apk="$2"
  local class="$3"
  local f="$OUT/$name.json"
  [ -s "$f" ] && [ "$(wc -c < "$f")" -gt 8 ] && { echo "skip $name"; return; }
  $REA inspect-android-class "$apk" "$class" --format json --filter-output normalized_result > "$f" 2>"$OUT/$name.err"
  local n=$(wc -c < "$f" | tr -d ' ')
  [ "$n" -gt 8 ] && echo "ok   $name ($n)" || echo "FAIL $name: $(head -c 160 "$OUT/$name.err")"
}

# ---- framework targets ----
inspect carplay.ICarplayManager        "$WRAP/desaysv-phonelinklib.apk" com.desaysv.ivi.phonelinkmanager.carplay.ICarplayManager
inspect carplay.RouteGuidance          "$WRAP/desaysv-phonelinklib.apk" com.desaysv.ivi.phonelinkmanager.carplay.CarPlayRouteGuidanceInfo
inspect phonelink.ISvPhoneLinkDevice   "$WRAP/desaysv-phonelinklib.apk" com.desaysv.ivi.phonelinkmanager.ISvPhoneLinkDeviceService
inspect projection.DesaySVProjectManager "$WRAP/desay.projection.apk" com.desaysv.ivi.vds.projection.DesaySVProjectManager
inspect projection.IProject            "$WRAP/desay.projection.apk" com.desaysv.ivi.projection.IProject
inspect multidisplay.DSVMultiDisplay   "$WRAP/desaysv-multi-display.apk" com.android.server.display.DSVMultiDisplayManagerService
inspect ecarx.EcarxMultidisplayManager "$WRAP/ecarx.car.apk" ecarx.multidisplay.EcarxMultidisplayManager
inspect ecarx.IEcarxMultidisplay       "$WRAP/ecarx.car.apk" ecarx.multidisplay.IEcarxMultidisplay
inspect ecarx.ECarXCarHardKeyManager   "$WRAP/ecarx.car.apk" ecarx.car.ECarXCarHardKeyManager
inspect ecarx.IHardKey                 "$WRAP/ecarx.car.apk" ecarx.car.IHardKey
inspect ecarx.KeyCodeForFW             "$WRAP/ecarx.car.apk" ecarx.car.KeyCodeForFW
inspect ecarx.EcarxSplitScreenManager  "$WRAP/ecarx.car.apk" ecarx.splitscreen.EcarxSplitScreenManager
inspect adaptapi.HUDInteraction        "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.hudinteraction.HUDInteraction
inspect adaptapi.ExternalWifiManager   "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.wifiap.ExternalWifiManager
inspect adaptapi.Input                 "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.input.Input
inspect adaptapi.KeyCode               "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.input.KeyCode
inspect adaptapi.Policy               "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.policy.Policy
inspect adaptapi.MultiWindowImpl       "$WRAP/ecarx.adaptapi.apk" com.ecarx.xui.adaptapi.uiinteraction.MultiWindowImpl
inspect fw.GeelyCopilotScreen          "$WRAP/autolink.adapterservice.apk" ecarx.fw.api.copilotscreen.GeelyCopilotScreen
inspect fw.EcarxNaviInteraction        "$WRAP/autolink.adapterservice.apk" ecarx.fw.api.diminteraction.EcarxNaviInteraction
inspect fw.MultiWindow                 "$WRAP/autolink.adapterservice.apk" ecarx.fw.api.uiinteraction.MultiWindow
inspect fw.TcamWifi                    "$WRAP/autolink.adapterservice.apk" ecarx.fw.api.wifi.TcamWifi
inspect fw.ECarXAudio                  "$WRAP/autolink.adapterservice.apk" ecarx.fw.api.audio.ECarXAudio
inspect vdbus.IVDBus                   "$WRAP/autolink.adapterservice.apk" com.desaysv.ivi.vdb.IVDBus
inspect gesture.CarSystemGestures      "$WRAP/dsv-system-gesture.apk" com.android.server.wm.CarSystemGesturesManager
inspect phonelinkadaptor.Manager       "$WRAP/desaysv-phonelinkadaptor.apk" com.desaysv.ivi.phoneLinkplatformadaptorlib.PhoneLinkAdaptorLibManager

echo "done -> $OUT"
