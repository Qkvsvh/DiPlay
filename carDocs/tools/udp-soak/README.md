# udp-soak — minimal UDP receiver for the realtime-audio loss diagnostic

A ~100-line standalone Android app used by the diagnostic method
[`诊断法-实时音频UDP丢包.md`](../../诊断法-实时音频UDP丢包.md) /
[`DIAGNOSTIC-UDP-AUDIO-LOSS.md`](../../DIAGNOSTIC-UDP-AUDIO-LOSS.md).

It answers one question on the head unit: **"can this unit receive a steady, audio-shaped UDP
stream?"** If not, realtime CarPlay audio (UDP, no retransmission) cannot work there, no matter
what the app does.

## What it does

- Binds a UDP socket on `[::]:5001` (all interfaces, IPv4 + IPv6) and counts arrivals.
- Shows, live: `packets`, `bytes`, `sinceLastMs` (time since the last packet — this is what reveals
  "stops after a few seconds"), `gaps > 200 ms`, `maxGapMs`, plus the unit's interface addresses.
- Writes one line per second to logcat **and** to
  `Android/data/com.diplay.udptest/files/udptest.log` (pull it with `adb pull` afterwards).
- Can create the same kind of Wi-Fi P2P group (or LocalOnlyHotspot) that DiPlay uses, and show its
  **SSID/passphrase**, so a PC can join the same link with no external AP.
- Can send a short 50-packet burst to a typed `ip:port` for the reverse direction.

## Build (local, standalone)

This folder is a self-contained Gradle project that is **not** part of the DiPlay build. Build it
with the repo's wrapper:

```bash
cd carDocs/tools/udp-soak
cp ../../../gradlew .
cp -r ../../../gradle .
echo "sdk.dir=$HOME/Android/Sdk" > local.properties      # or your SDK path
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Install

The target head units often filter the literal `install` in adb shell commands. Use the repo script:

```bash
./carDocs/install-apk.sh carDocs/tools/udp-soak/app/build/outputs/apk/debug/app-debug.apk
```

## Use

1. Launch **UDP Test** on the head unit (there may be no launcher icon — start it if needed with
   `adb shell am start -n com.diplay.udptest/.MainActivity`).
2. Either:
   - tap **① 创建 Wi-Fi P2P 组** / **② LocalOnlyHotspot** and note the **SSID / PASSPHRASE** shown, then
     join that network from the PC; or
   - put the head unit and the PC on any common AP (see the diagnostic doc for the caveat about not
     running Wi-Fi Direct at the same time).
3. From the PC, send the audio-shaped stream (`iperf -c <unit-ip> -u -b 230k -t 60 -i 1`, or the
   python script in Appendix B of the diagnostic doc) targeting **`<unit-ip>:5001`**.
4. Watch the counters on screen, or read the per-second log:
   ```bash
   adb shell cat /sdcard/Android/data/com.diplay.udptest/files/udptest.log | tail -60
   ```

## Reading the result

- **< 5% lost** → the unit's Wi-Fi receive is fine; look elsewhere (app stats / protocol / focus).
- **> 10% lost, or the count stops climbing after a few seconds while the PC keeps sending** →
  the head unit's Wi-Fi receive path is dropping it. Not an app-side bug.

> Requires `ACCESS_FINE_LOCATION` for the P2P/LocalOnlyHotspot buttons; grant it with
> `adb shell pm grant com.diplay.udptest android.permission.ACCESS_FINE_LOCATION`
> (the receiver itself needs only `INTERNET`).
