# 吉利车机地图（com.geely.map）HUD 显示机制

分析对象：本车机 `/vendor/app/GeelyMap/GeelyMap.apk`（`com.geely.map`，版本 `1`，高德 AutoSDK + 吉利定制层）。
拉取与反编译步骤见 [`extracted/geely-map/README.md`](extracted/geely-map/README.md)。
文中代码路径都相对于 `carDocs/extracted/geely-map/out/full/sources/`。

## 一句话结论

地图 App **不新建窗口、不用 `Presentation`、也不往 HUD 的 Display 上投屏**。
它把高德地图引擎的一个**副引擎（engineId = 3）直接渲染进「车机侧 HUD 宿主 App 递过来的一块 `Surface`」**：
宿主（仪表 / HUD / OneOS 导航服务）负责在 HUD 上画出自己的 UI 并合成这块 Surface，
地图 App 只负责把地图（航向、路线、路口放大图、车模）画进这块 Surface。

仪表/副驾屏/桌面卡片走的是**同一条管线**，只是 `sourceType` 不同（0=Widget，1=HUD）；
那条（`sourceType=0`）在本地还可以走 `android.app.Presentation`（`ExternalScreenPresentation`，engineId 4）
把地图投到仪表那块 Display 上，由车机通过协议 30800 开关（见文末实测）。

## 调用链

```
HUD / 仪表 / 副驾屏 App
  │  ① 自备 Surface（SurfaceTexture / SurfaceControl / SurfaceView 均可）
  ├─ INaviServer.addSurfaceWithSourceType(surface, sourceType, type, h, w)   [AIDL TRANSACTION = 68]
  │     com.geely.lib.oneosapi.navi.NaviManager（OneOS 对外 API）
  ▼
com.geely.naviService  (NaviServer.addSurfaceWithSourceType)
  │  ② GeelyWidgetManager.startWidgetRender(surface, sourceType, type)
  ├─ bindService(com.geely.map / com.autonavi.amapauto.adapter.internal.widget.AutoSimilarWidgetService)
  │     action "com.similar.widget.view.service"，exported=true、无 permission 限制
  ▼
com.geely.map  AutoSimilarWidgetService.WidgetIbinder.addSurface(surface, sourceType, mode)
  │  ③ sourceType == 1 → HudManager      （HUD，engineId 3，Surface 直连，无窗口）
  │     sourceType == 0 → WidgetManager  （仪表/副驾屏/卡片，engineId 4，也可用 Presentation）
  ▼
HudManager.addSurface
  │  ④ 若 HudExtScreenManager 尚未初始化，等 notifyWidgetInitSuccess（最长 10 s，超时回调 11）
  ▼
HudExtScreenManager.ExternalState.startRender(surface, mode)
  ▼
AidlWidgetExtScreen.startRender(surface)
  ▼
WidgetMapView.surfaceCreated(surface, w, h)
     MapDevice.getWindowFromSurface(surface) → EGLSurfaceAttr{nativeWindow, w, h, isNativeWindowFromSurface=true}
     mMapDevice.attachSurfaceToDevice(EGLSurfaceAttr)
  ▼
高德 GBL 引擎把 engineId=3 的地图直接画进宿主的那块 Surface
```

关键文件：

| 环节 | 文件 |
| --- | --- |
| 地图侧的 Surface 服务 | `com/autonavi/amapauto/adapter/internal/widget/AutoSimilarWidgetService.java` |
| HUD 分支 | `com/autonavi/amapauto/adapter/internal/widget/HudManager.java` |
| Widget 分支 | `com/autonavi/amapauto/adapter/internal/widget/WidgetManager.java` |
| HUD 主控 | `com/geely/hud/HudExtScreenManager.java`、`com/geely/hud/business/ExternalScreenController.java` |
| HUD 渲染 | `com/geely/hud/model/AidlWidgetExtScreen.java`、`WidgetMapView.java` |
| ARouter 注册 | `com/alibaba/android/arouter/routes/ARouter$$Providers$$sdk_component_widget.java`（`/AutoSDKWIDGET/hud` → `ComponentHudServiceImpl`） |
| 车机侧转发 | 车机 `com.geely.naviService`：`NaviServer`、`GeelyWidgetManager`、`WidgetViewInteraction` |

## 初始化：地图 App 什么时候建 HUD 引擎

1. `com/geely/map/presenter/InitPresenter.initSDKMap()` 在建图完成后调用
   `ComponentManager.getInstance().initWidgetService(3, 7)`
   —— `multiScreenType = 3`，`multiScreenOverlayType = 7`。
2. `ComponentManager.initWidgetService` → `ModuleServiceUtils.getHudService()`；
   该方法在 `sdk_adapter_config.json` 的 `featureConfig.isOpenHud == false` 时**直接返回 null，HUD 整个不初始化**。
3. `ComponentHudServiceImpl.initService(3, 7)` → `HudExtScreenManager.initExScreen(MultiScreenInfo{3,7})`
   → `createExtScreen()`：只有 `multiScreenType == 3` 才建引擎
   - `SurfaceViewID.transform2EngineID(3)` → engineId `3`；`ExternalScreenController.initExtScreen(surfaceViewParam, deviceId, 3, 7)`
   - `surfaceViewParam.width/height` 取 `hudConfig.surfaceWidth/surfaceHeight`
   - 建完调 `AutoSimilarWidgetService.getHudStateCallback().notifyWidgetInitSuccess()` 通知宿主可以对 Surface 开始渲染
4. 渲染期间注册：日夜模式监听、TMC 观察者、地图层级/视角观察者。

## HUD 上到底画了什么

配置来自 APK 内 `assets/sdk_adapter_config.json`（`AdapterConfigManager` 读取）：

```json
"hudConfig": {
  "surfaceWidth": 200, "surfaceHeight": 185,      // 只有 200x185 的一小块地图
  "dayNightMode":      { "cruise": 1, "navi": 1 },
  "skyDayNightMode":   { "cruise": 1, "navi": 1 },
  "mapScaleConfig":    { "cruise": 17, "navi": 17 },
  "mapViewMode":       { "cruise": 1, "navi": 1 },
  "carPosition":       { "cruise": 0.64, "navi": 0.64 },
  "carPositionHoriz":  { "cruise": 0.5, "navi": 0.5 },
  "tmcConfig":         { "cruise": 2, "navi": 2 }, // 2 = 关闭路况
  "crossViewType":     { "cruise": 0, "navi": 0 },
  "mapTextScale":      { "cruise": 0.3, "navi": 0.3 }  // 文字压到 30%
}
```

`cruise` / `navi` 两套值由 `HudExtScreenManager.getIntConfig()` 按 `IModuleDriveService.isNaviFragment()` 选择
（巡航态用 `cruise`，导航态用 `navi`）。同名字段在 `widgetConfig` 里是 1920x720 的另一套值。

绘制行为：

- **车模小、贴车位置**：`AidlWidgetExtScreen.setFirstCarPosition()` → `updateCarStyle(2002)` +
  `setCarScaleByMapLevel(0.41 ×)`、`setCarMode(0,false)`；随后 `goToDefaultPosition(3,false)`。
- **导航路线**：`HudMapDeviceObserver.onDeviceRender(renderStatus == 5)`（首帧）→ `setRendering(true)` →
  `IModuleDriveService.exDrawPath(3)` 在 HUD 引擎上补画路线；`HudExtScreenManager.drawRoute()` 用
  `DrivingLayer(3)` 跟随模式 + `updateCarStyle(2001)`。
- **路口放大图**：`BaseExternalScreenBaseNaviHandler.onShowCrossImage()` → `NaviComponent.showCrossImage(3, info)`，
  并把地图可视区挪到 `setMapLeftTop(3, w*3/4, h*2/3)`，把左上角让给放大图；隐藏时恢复到 `(w/2, h*2/3)`。
- **导航结束/退出**：`ExternalScreenNaviHandler.stopNavi()` + `clearAllItems()/clearAllPaths()`，恢复默认级别。
- **速度/道路自适应缩放**：`HudExtScreenManager.notifyCurrentSpeed(speed, roadType, type, segRemainDis)`，
  数据来自 Amap 协议 `PROTOCOL_GUIDE_INFO = 30407`
  （`ProtocolDistributeManager.distribute`，仅当 `getHudService().isRendering()` 为真才处理）：

  | 场景 | 判断 | 动作 |
  | --- | --- | --- |
  | 巡航 (`type == 2`) | `speed < 45` | 关自动缩放，级别 17 |
  | | `45 ≤ speed ≤ 100` | 关自动缩放，级别 16 |
  | | `speed > 100` | 关自动缩放，级别 15 |
  | 导航在 0/6/7 类路 | 剩余 > 500 m | 级别 15 |
  | | 剩余 ≤ 500 m | 打开自动缩放 |
  | 导航在其他路 | 剩余 > 200 m | 级别 16 |
  | | 剩余 ≤ 200 m | 打开自动缩放 |

- **状态回报给宿主**：`ExternalInteractionManager`
  - `buildStatusDataAndDispatch(126 = HUD_FIRST_DRAW / 127 = HUD_STOP_RENDERING, -1)`
  - `buildExtStatusDataAndDispatch(1, 0/1/2, 1)` → 协议 `30801`（`PROTOCOL_NOTIFY_EXTSCREEN_STATUS`），
    载荷 `{screenMode, screenStatus, sourceType, screenWindow}`
  - 宿主可用 `IAutoWidgetStateProvider` 收到 `notifyWidgetInitSuccess / onWidgetFirstFrameDrawn / onWidgetRenderPause`、
    `onReceiveWidgetOperateResult(state)`（0 = 首帧成功、1 = Surface 为 null、2 = Surface 失效、3/4 = add/remove 连续调用、5/6 = 1 s 内重复 add/remove；11 = 等待初始化超时，只在地图内部产生、**不会**回传给宿主（回调带 `state <= 10` 限制））；
    用 `isMapRunning(sourceType)` 查询是否在渲染；用 `dispatchTouchEvent(ev, sourceType)` 把触摸转发进地图引擎。

## HUD 硬件侧（亮度 / 角度 / 位置）

HUD 是车上的独立硬件（AR-HUD），地图 App 只通过吉利 OneOS API 操作它：

`com/geely/lib/oneosapi/setting/SettingManager.java` →

- `isHudActive()`、`isHudAngelAdjustOn()`、`isHudBrightnessAndPositionAdjustOn()`
- `setHudBrightnessAdjust(keyStatus, keyCode)`、`setHudPositionAdjust(...)`、`setHudAngelAdjust(...)`

对应 AIDL `IMyCarService` 的 TRANSACTION 42~47；实现在车机 `com.geely.dimservice`（`OneOS-DIMService.apk`，实测进程存在），
仪表/AR-HUD 的车载（QNX/CAN）信号由 `com.autolink.diminteraction`（`DIMInteraction.apk`，含
`AR_HUD_*`、`VDHudSystemStatusResponse` 等）处理。
地图 App 里的 `setting_main_text_setting_hud`（"HUD导航"）字符串在代码中**没有被引用**，属于遗留/其他模块资源。

另外地图 App 内含高德的 AR-HUD 数据通道（`com/autonavi/gbl/lane/model/LaneArHud*`、
`ILaneArHudDataObserver`、`PROTOCOL_ARNAVI_ARHUDGUIDELINEOUTPUT/ARHUDROUTEINFOOUTPUT`）
与 HUD 皮肤资源（`res/drawable*/autosdk_hud_sou*`、`global_image_hud_sou*`、`global_image_landback_hud_*`），
本工程没有引用它们的布局（`res/layout` 下没有 hud 布局）。

## 本车实测现状

```sh
adb shell dumpsys display | grep -i displayDeviceInfo
#   displayId 0 : 2560x1600（中控主屏）
#   displayId 2 : 1920x720  EXTERNAL + FLAG_PRESENTATION  ← 仪表盘，不是 HUD
#       依据：OneOS `PresentationInfo.ScreenWindow.WINDOW_ID_INSTRUMENT = 1002`，
#       地图 `MainActivity.doStartMultiScreen` 把 screenId ≥ 1000 减去 1000 再 `getDisplay()` → display 2
adb shell dumpsys activity services com.geely.map | grep -A6 AutoSimilarWidgetService
#   Client AppBindRecord ... ProcessRecord{...:com.geely.naviService}   ← 宿主是 OneOS 导航服务
```

配置实测：`multiScreenType = 2`、`featureConfig.isOpenHud = false`、
`isWidgetServiceSupport = true`、`isExtScreenServiceSupport = false`。

仪表上的地图是**车机主动开关**的：`com.geely.naviService` 走 JSON 协议
`30800`（`switchMapPresentation`，载荷 `{screenMode:1, operateType, screenWindow:1002}`）请求地图 App 开关，
地图用协议 `30801` 回报 `{screenStatus, screenMode, screenWindow, sourceType}`；
`operateType`：`0=OPERATE_OPEN`、`1=OPERATE_CLOSE`、`2=OPERATE_CLOSE_ALL`，`screenMode`：`1=DEFAULT_MODE`。

### 车辆启动后的一次实测（2026-10-11 22:05）

```sh
adb logcat -d | grep -E "pavfhudactvsts|SETTING_FUNC_HUD_ACTIVE"
```

```text
22:05:06  SETTING_FUNC_HUD_ACTIVE(0x20110100) mValue=0            # 只上报能力，HUD 还没起来
22:05:12  onPAChanged propertyName pavfhudactvsts (0x21708f21)
          PA_VF_HUD_ActvSts{availability=Active, dataValue=On}   # ← HUD 硬件上线
22:05:12  SETTING_FUNC_HUD_ACTIVE mValue=0 → 1                    # HUD 激活
```

同一批日志里这些 HUD 功能都是 `active`（`mSupportValues=[1,0]`）：
`SETTING_FUNC_HUD_ACTIVE`(0x20110100)、`SETTING_FUNC_HUD_CALIBRATION`(0x20110200)、
`IHUD.SETTING_FUNC_HUD_BRIGHTNESS_ADJUST`(0x27010500)、`..._POSITION_ADJUST`(0x27010600)、
`..._ANGLE_ADJUST`(0x27010700)、`..._ANGLE_RESET`(0x27010800)、`..._SNOW_MODE`(0x27020100)、
`..._AR_ENGINE`(0x27020200)=1。

结论：
1. **HUD 硬件在线**（车辆信号 `HUD Active = On`、AR 引擎开），HUD 由车机/QNX 侧的 HUD ECU 驱动。
2. **地图 App 的 HUD `Surface` 通道没有启用**：`isOpenHud=false`，日志里没有
   `HudExtScreenManager` / `AidlWidgetExtScreen` / `addSurface(sourceType=1)` → 地图没有往 HUD 投图。
3. 同一时刻仪表（display 2）上的地图 Presentation 是开着的：22:06:13 车机发协议 30800
   `{screenMode:1, operateType:1(CLOSE), screenWindow:1002}` 让它关掉，地图回 30801
   `{screenStatus=1,...}`，22:06:15 `ExternalScreenPresentation.onDisplayRemoved`。仪表屏本体一直 `state ON`。

要走到 HUD 渲染路径（`sourceType=1`，200x185 的小地图进 HUD），需要车型打开 `isOpenHud`
并有一个 HUD 宿主 App 递 `Surface`；本车当前没有这一侧的应用。

## 想在第三方 App 里复用

1. `bindService` → `com.geely.map/com.autonavi.amapauto.adapter.internal.widget.AutoSimilarWidgetService`
   （exported、无权限），或使用 OneOS 的 `NaviManager.addSurfaceWithSourceType()`。
2. 自己准备一块 `Surface`，调 `IAutoSimilarWidgetViewService.addSurface(surface, sourceType=1, mode)`；
   地图会按 `hudConfig`（200x185）在引擎 3 上渲染，`mode` 传给 `startRender(surface, mode)`。
3. 用 `setWidgetStateControl(IAutoWidgetStateProvider, 1)` 收首帧/暂停回调；
   顺序错误会收到 `onReceiveWidgetOperateResult` 的 2~6；10 s 内没等到初始化会收到 11。
4. 记着 `removedSurface(surface, 1, mode)` 释放（地图侧会 `Surface.release()`，宿主不要再用）。
5. HUD 上的箭头/距离/速度等 UI 必须**由宿主自己画**：地图只提供那一小块地图（含路口放大图）。
