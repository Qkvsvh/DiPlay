# geely-map — 车机自带高德地图（com.geely.map）

本车机（`geely_fx11` / `CS11_A4`，Android 11，DesaySV）自带的导航应用。
HUD 分析结论见 [`../../吉利地图HUD显示机制.md`](../../吉利地图HUD显示机制.md)。

## 拉取与还原

```sh
# 1) 拉取（334 MB，约 8 s）
adb shell pm path com.geely.map            # => /vendor/app/GeelyMap/GeelyMap.apk
mkdir -p carDocs/extracted/geely-map
adb pull /vendor/app/GeelyMap/GeelyMap.apk carDocs/extracted/geely-map/

# 2) 取出 dex（classes.dex ~ classes6.dex）
cd carDocs/extracted/geely-map
mkdir -p dex && unzip -o -q GeelyMap.apk 'classes*.dex' -d dex

# 3) 反编译（jadx 1.5.6，全量约 5~10 min，输出 ~470 MB）
jadx -d out/full -j 8 GeelyMap.apk
```

```sh
# 只要单个类
jadx --single-class com.geely.hud.HudExtScreenManager --single-class-output /tmp/hud GeelyMap.apk
```

反编译产物（不入库）：

- `out/full/sources/` — Java 源码（含 `com/geely/hud`、`com/geely/widget`、`com/geely/extscreen`）
- `out/full/resources/` — `AndroidManifest.xml`（已解码）、`res/`、`assets/`（关键：`assets/sdk_adapter_config.json`）

## 应用概况

| 项 | 值 |
| --- | --- |
| 包名 / 路径 | `com.geely.map` / `/vendor/app/GeelyMap/GeelyMap.apk` |
| versionName | `1`（versionCode `99999999`），minSdk 21 / targetSdk 29 |
| 引擎 | 高德 AutoSDK（`com.autonavi.gbl.*`、`com.autosdk.*`）+ ECARX `ecarx.xui.adaptapi` |
| 车厂定制包 | `com.geely.hud`（HUD）、`com.geely.widget`（副驾屏/卡片）、`com.geely.extscreen`（外接屏 Presentation）、`com.geely.noa`、`com.geely.lib.oneosapi`（吉利 OneOS API） |
| 跨应用服务 | `com.autonavi.amapauto.protocol.service.JsonProtocolService`（Amap 车机协议）、`com.autonavi.amapauto.adapter.internal.widget.AutoSimilarWidgetService`（`com.similar.widget.view.service`，**HUD/副驾屏 Surface 入口**） |

## 在车机上核对（实测命令）

```sh
# 谁在用地图的 Surface 服务
adb shell dumpsys activity services com.geely.map | grep -A5 AutoSimilarWidgetService
# => Client AppBindRecord ... ProcessRecord{...:com.geely.naviService}

# 副驾/HUD 外接屏
adb shell dumpsys display | grep -i "DisplayDeviceInfo\|mDisplayId"

# HUD 设置接口的实现方
adb shell dumpsys activity services com.geely.dimservice
```
