# 车机框架逆向（framework JAR / VDBus / ECARX-DesaySV API）

> 数据来源：`carDocs/extracted/system/framework/*.jar`（本机 framework 是 **DEX-in-JAR**，即 jar 里只有
> `classes.dex`）。因 REA 的 Android provider 只接受 stand-alone APK，这里把每个 `classes.dex` 包一层
> 空 APK 后再用 REA（`jadx-headless-mcp 0.7.1`）分析，脚本见 [`tools/collect-rea.sh`](tools/collect-rea.sh)。
> 类清单证据在 `evidence/fw-*/classes.json`，成员清单在 `evidence/inspect/*.json`。
>
> 凡 `dexdump`/`aapt2`/`strings` 得出的观测都会标注「普通工具」。

## 一、框架 JAR 一览（谁定义了什么）

| JAR | 类数 | 定义 | DiPlay 相关度 |
| --- | --- | --- | --- |
| `framework.jar` | 30 MB | AOSP framework + 车厂改写 | 高（含 display/window/audio 的定制） |
| `services.jar` | 15 MB | system_server | 高（服务实现） |
| `autolink.adapterservice.jar` | 3.9 MB | **`ecarx.fw.api.*`（ECARX 框架 API）+ VDBus 客户端/服务端** | 高 |
| `ecarx.car.jar` | 3.65 MB | **ECARX Car 扩展：硬按键、车辆信号、多屏、分屏** | 高 |
| `car-frameworks-service.jar` | 3.3 MB | AOSP CarService 内部（CarLaunchParamsModifier 等） | 中 |
| `ecarx.adaptapi.jar` | 2.4 MB | **`com.ecarx.xui.adaptapi.*`（ECARX 适配 API：HUD/输入/Wi-Fi/多窗/车辆/策略）** | 高 |
| `desaysv-phonelinkadaptor.jar` | 1.6 MB | 德赛手机互联适配（CarPlay/HiCar/CarLife/AA 能力、车况适配) | 高 |
| `desaysv-phonelinklib.jar` | 128 KB | **`com.desaysv.ivi.phonelinkmanager.*`：CarPlay/手机互联状态与引导信息 AIDL** | 高 |
| `desaysv-multi-display.jar` | 14 KB | `DSVMultiDisplayManagerService`（多屏/多用户配置） | 高 |
| `desay.projection.jar` | 10 KB | **投屏服务 `IProject` + `DesaySVProjectManager`** | 高 |
| `dsv-system-gesture.jar` | 22 KB | 多指系统手势（`CarSystemGesturesManager`） | 中 |
| `desaysv-car-frameworks-service-extension.jar` | 6 KB | `com.android.server.SystemServerBolt` | 低 |
| `ecarx-carservice-starter.jar` | 1.8 KB | `EcarxCarServiceStarter` | 低 |
| `tcmiface.jar` | 814 B | `com.quicinc.tcmiface.DpmTcmIface` | 低 |
| `android.car.jar` | 714 KB | AOSP Car API | 中 |

## 二、`ecarx.car.jar` —— ECARX Car 扩展（直接绑定 CarService 插件）

顶层（REA，`evidence/fw-ecarx.car`）：

- `ecarx.car.ECarXCar` / `IECarXCar` / `ECarXCarManagerBase` / `ECarXCarInfo`：ECARX 的 Car 客户端。
- **`ecarx.car.ECarXCarHardKeyManager` + `IHardKey` + `IKeyEventListener` + `KeyCodeForFW`**：
  车机硬按键（方向盘、面板键）的**第二条通道**。
  ```java
  // inspect-android-class 观测（evidence/inspect/ecarx.ECarXCarHardKeyManager.json）
  int[] requestHardKeyEvent(int[] keyCodes, ecarx.car.IKeyEventListener cb, int cookie);
  void  abandonHardKeyEvent(ecarx.car.IKeyEventListener cb, int keyCode);
  int   get3rdAppCookie(String packageName);
  // 常量：COOKIE_3rdAPP / COOKIE_GKUI / COOKIE_HICAR / COOKIE_NONE；ECARX_HARDKEY_MAP / ECARX_HICAR_MAP
  ```
  → 与 DiPlay 现在用的 `com.geely.inputservice.IInputManager.registerListener` 是**并行**的
  ECARX 官方通道；`COOKIE_HICAR` 说明 HiCar 也是用它抢键（可与麦克风键被 HiCar 抢占的现象对上）。
- `ecarx.car.hardware.property.*` + `ecarx.car.hardware.vehicle.*`：**上百个车辆信号管理器**
  （`ECarXCarVfhudManager`=HUD、`ECarXCarVfnaviManager`=导航、`ECarXCarApwindoorManager`=门窗、
  `ECarXCarClimateManager`=空调、`ECarXCarSensorManager`、`ECarXCarTravelManager`、
  `ECarXCarSetManager`、`ECarXCarProfileManager`…）。
- `ecarx.car.hardware.annotation.*`：**车控信号 JSON 字典**（`VendorSignalPropertyRX/TX`、`HudDispMod`、
  `HudErgoAdjmt`、`HudIllmnreq`、`HudStsForHmi`、`SpdUnit1`、`GearLvlIndcn2`… 数百个）。可当信号枚举表用。
- **`ecarx.multidisplay.IEcarxMultidisplay` / `EcarxMultidisplayManager`**、**`ecarx.splitscreen.*`**：
  多屏与分屏（本次 REA `inspect-android-class` 失败，见「待补」）。
- `ecarx.os.Environment` / `LocalConfig` / `PartNumber`、`ecarx.provider.Settings`（ECARX 设置 provider）、
  `ecarx.power.BrightnessManager`、`ecarx.psd.PSDBluetoothManager`、`ecarx.dtcnl.DtcnlManager`。

## 三、`ecarx.adaptapi.jar` —— `com.ecarx.xui.adaptapi.*` 适配 API

关键接口（成员清单见 `evidence/inspect/`）：

### HUD —— `com.ecarx.xui.adaptapi.hudinteraction.HUDInteraction`
```java
HUDInteraction create(Context);
int  requestHUDMode();                         // HUD_MODE_AR / _SIMPLE / _SMART_DRIVING / _SMART_GUIDE
void requestADASSyncInfo(); int requestADASOpenStatus();
int  requestCarFollowingGAPLevel();
int  requestCalibrationMode();
void requestHUDHeight();
HUDCalibrationParam getHUDCalibrationParam();
boolean registerCallback(IInteractionCallback);
```
> 与之前用 OneOS（`getService(41)` 返回 null）拿不到 HUD 状态形成对照：**adaptapi 提供了 HUD 模式的读写入口**，
> 值得上车试（`requestHUDMode` / `requestADASOpenStatus`）。

### 输入 —— `com.ecarx.xui.adaptapi.input.Input` / `KeyCode`
```java
int[] requestKeysInterception(int[] keyCodes, IKeyCallback cb);   // 申请拦截按键
boolean abandonKeysInterception(IKeyCallback cb);
Input create(Context);
```
> **按键拦截**能力。之前结论是「转向盘按键只走厂商私有栈、`IInputManager.interceptKeyCode` 只对 HOME 生效」，
> 这条 `requestKeysInterception` 是另一条可能的拦截路径（能否拦麦克风键待实测）。

### Wi-Fi / 热点 —— `com.ecarx.xui.adaptapi.wifiap.ExternalWifiManager`
```java
ExternalWifiManager create(Context[, int]);
boolean setWifiEnabled(boolean);   boolean isWifiEnabled();
int getWifiState();  List<ScanResult> getScanResults();  List<WifiConfiguration> getConfiguredNetworks();
int addNetwork(WifiConfiguration); boolean enableNetwork(int, boolean); boolean disconnect();
void connect(WifiConfiguration, ActionListener);
void registerWifiStateListener(...);  registerScanResultListener(...);  registerConnectionStateListener(...);
```
> 车机把 Wi-Fi 开关包成自己的 API。之前 DiPlay 只能靠 adbd 开 Wi-Fi（`cmd wifi set-wifi-enabled`）；
> **如果有权限，这里可能可以直接 `setWifiEnabled(true)`**。同理 `IWifiApHost` / `WifiAp` 管热点。

### 多窗口 / 分屏 —— `com.ecarx.xui.adaptapi.uiinteraction.MultiWindowImpl`（+ `ecarx.fw.api.uiinteraction.MultiWindow`）
```java
int  moveActivityBetweenDisplay(int);           // 把一个 Activity 移到别的 display
boolean isSplitScreenModeSupported(); boolean isInSplitScreenWindowingMode();
boolean isPackageSupportedSplitScreen(String);  boolean isActivitySupportedSplitScreen(String,String);
void closeSplitScreenMode([int]);  void swapDockedAndFullscreenStack();
String getScreenStackPackageName(int);
// 常量 DOCKSIDE_LEFT / DOCKSIDE_RIGHT
```
> 「把 CarPlay Activity 移到仪表屏」或「控制分屏」的官方入口；可与现有 `Presentation` 方案互补。

### 策略 —— `com.ecarx.xui.adaptapi.policy.Policy`
`getAudioPolicy()` / `getAudioAttributes()` / **`getPermissionPolicy()`** / `getStoragePolicy()` /
`getVoiceAssistantPolicy()` / `getWindowManagerPolicy()`；含 `IMicResourceCallback`（麦克风资源）。
> 与「第三方麦克风被车机权限服务拒绝」「麦克风资源被语音助手独占」的问题直接相关。

### 其它顶层
`com.ecarx.xui.adaptapi.navigation.*`（`Navigation`、`VCUNavigation`、`Speed`、EHP GPS/轨迹）、
`dvr.*`（行车记录）、`evs.*`（环视）、`peripherals.wear.IntelligentKeyImp`（智能钥匙）、
`tpms.*`（胎压）、`tbox.*`（T-Box/远程）、`ota.*`、`oncall.*`（紧急呼叫）、`trackball.*`、
`wpc.*`（无线充）、`lighting.*`、`vehicle.*`（`VehicleDoor/Window/Seat/Mirror/Massage/Vent`）。

## 四、`autolink.adapterservice.jar` —— `ecarx.fw.api.*` + VDBus

### VDBus（德赛「车机总线」）
```java
// com.desaysv.ivi.vdb.IVDBus  (evidence/inspect/vdbus.IVDBus.json)
VDEvent get(VDEvent);
void    set(VDEvent);
void    subscribe(int[] eventIds, int, String, IVDBusCallback);
void    subscribeCustomizedEvent(VDEvent, IVDBusNotify);
void    unsubscribeCustomizedEvent(VDEvent, IVDBusNotify);
```
事件空间（`com.desaysv.ivi.vdb.event.id.*`，节选）：

| 域 | 代表事件/Bean |
| --- | --- |
| `navi` | `VDEventNavi`、`VDNaviTotalInfo`、`VDNaviRoadInfo`、`VDNaviLaneInfo`、`VDNaviCompass`、`VDNaviDisplayCluster`、`VDNaviDisplayArea`、`VDDistNaviContent` |
| `carlan`（车信号） | `VDHudSystemStatusResponse`、`VDARHudSettingsInfo`、`VDNaviTotalInfo2`、`VDSoundAndroidToQnxFocusStatus`、`VDSoundQnxToAndroidFocusStatus`、`VDVehicleDiagnose` |
| `phonelink` | `VDEventPhoneLink` / `VDValuePhoneLink`（手机互联状态） |
| `projection` | `VDEventProjection`（投屏） |
| `media` / `bt` / `tuner` | `VDMediaPlayActionInfo`、`VDBTMusicInfo`、`VDFmAmPlay`… |
| `rvc` / `cabin` | `VDCLRVCCameraOPMsg`、`VDCLDisplayRequestInfo` |
| `vehicle` / `carstate` | `VDEventVehicleHal`、`VDEventCarState` |
| `permission` | `VDEventPermission`（权限事件） |
| `vr` | `VDAsr`、`VDTTS`、`VDVRPipeLine` |

> VDBus 是**通用车机事件总线**：导航/HUD/投屏/手机互联/音频焦点/权限都有事件。
> 第三方能否 `bindService` 到 `IVDBus` 取决于权限（待补：服务名与权限）。

### `ecarx.fw.api.*`（框架级便捷 API，boot classpath）
| 类 | 关键方法 | 用途 |
| --- | --- | --- |
| **`diminteraction.EcarxNaviInteraction`** | `updateETA/Distance/Lane/NextGuidancePointName`、`notifyNavigationStatus`、`updateNaviInfo/NaviStatus`、`updateHighwayExitInfo`、`updateServiceAreaInfo`、`updateRoadCameraInfo`、`registerNavigationInteractionCallback`；常量 `ACTION_DIM_DISPLAY_AR/FULL/SIMPLIFY/OFF`、`EXT_KEY_DIM_DISPLAY_MODE` | **仪表导航（框架层版）**：与 DiPlay 现用的 `DimNaviService` binder 是同一套能力的另一入口 |
| `diminteraction.EcarxMediaInteraction / EcarxPhoneCallInteraction / EcarxClimateInteraction / EcarxContactsInteraction / EcarxVrInteraction / EcarxVendorInteraction` | 媒体/通话/空调/联系人/语音/厂商 → 仪表 |
| `uiinteraction.MultiWindow / WindowManager / FreeFormWindow / TouchManager` | 分屏/窗口/自由窗/触控注入 |
| `wifi.TcamWifi / SapClient / TcamWifiApCallback` | Wi-Fi + SoftAP（`startSoftAp/stopSoftAp/getWifiApConfiguration`） |
| `audio.ECarXAudio / GeelyExternalSpeakers / XCallStatusCallback` | 音频与「外置扬声器」、XCall |
| `bluetooth.EcarxBluetooth / GeelyA2dpExtension / MultiBtEcarxBluetooth` | 蓝牙 A2DP 扩展、多蓝牙 |
| `copilotscreen.GeelyCopilotScreen` | `getWebUrl()`、`registerCopilotScreenCallback()`（副驾屏，Web 承载） |
| `business.hardkey.IEcarXHardKey / KeyCodeForFW` | 框架层硬按键 |
| `navigation.speed.Speed` | 车速/限速 |
| `console/dock.GeelyDock`、`trackball.GeelyTrackBall`、`tripdata.ECarXTripData`、`rfdm.RFDM`、`csdm.CSDM`、`tcam.TCAM`、`ota.ApiOTA` | 其它车控能力 |
| `os.EcarxVoiceAssistantPolicy`、`policy.IWindowManagerPolicy` | 语音助手/窗口策略 |

> 这些类名大多对应 **ECARX 官方 SDK**（第三方 App 可用），实现由 `adapterservice` 进程提供。
> 是否对普通 uid 开放需按方法实测（见「待补」）。

## 五、手机互联 —— `desaysv-phonelinklib.jar` + `desaysv-phonelinkadaptor.jar`

### `com.desaysv.ivi.phonelinkmanager.*`（phonelinklib）

- **`carplay.ICarplayManager`**（`evidence/inspect/carplay.ICarplayManager.json`）：
  ```java
  void connectWirelessCarplayItem(BluetoothDevice);
  int  getWirelessCarplayConnectStatus();
  BluetoothDevice getCurrentWirelessCarplayDevice();
  List getWirelessCarplayDevicesList();
  void performWirelessCarplayScan();  void performWirelessCarplayStopScan();
  void addCarplayCallback(ICarplayCallBack);
  ```
- **`carplay.CarPlayRouteGuidanceInfo`**：字段 `currentRoadName`、`destinationName`、`distanceRemaining`
  （+ `DisplayString/Units`）、`distanceRemainingToNextManeuver`、`estimatedTimeOfArrival`、
  `maneuverState`、`routeGuidanceManeuverCurrentList(int[])`、`laneGuidance*`、`routeGuidanceState`…
  → 这就是**车机侧 CarPlay 的导航引导数据结构**（原厂 CarPlay 用）。
- `carplay.CarPlayLaneGuidanceInfo` / `CarPlayPhoneInfo` / `CarPlayTrackInfo` / `CarPlayTrackArtwork` /
  `WirelessCarPlayInfo` / `WirelessCarPlaySetupUUID` / `SupportedLocationInformation`。
- `ISvPhoneLinkDeviceService`：`checkCarPlayEnable()`、`getActivitingLinkDeviceType()`、
  `getLinkDeviceContextAll()`、`getLinkDeviceTypeListAll()`、`getWireLessLinkReconnectBtAddr(int)`。
- `SvPhoneLinkDeviceManager` / `SvPhoneLinkStateManager` / `SvPhoneLinkDeviceType` / `SvPhoneLinkCommonType`。

### `com.desaysv.ivi.phoneLinkplatformadaptorlib.PhoneLinkAdaptorLibManager`（phonelinkadaptor）

```java
boolean isSupportCarPlay();  boolean isSupportCarPlayWireless();  boolean getCarPlayConfig();  boolean getCarPlayITU();  boolean getCarPlayCertification();
boolean isSupportHiCar();    boolean isSupportHiCarWireless();     boolean isHicarCertificated(); boolean getHiCarConfig();
boolean isSupportCarLife();  boolean isSupportCarLifeWireless();
boolean isSupportAndroidAuto(); boolean isSupportAndroidAutoWireless(); boolean getAndroidAutoConfig();
int getCarGear();  int getPowerMode();  int getDayMode();  int getNaviStatus();  int getRvcStatus();  boolean getScreenStatus();
void onVehicleSpeedChange(double); void onKeyEventChange(int,int); void registerPhoneLinkAdaptorListener(...);
```
> 这是**车机对手机互联的能力与状态机**：明确报告本车支持 CarPlay（含无线）、HiCar、CarLife、AndroidAuto，
> 并暴露档位/电源/昼夜/导航/倒车/屏幕状态给互联 App。第三方可借此判断环境（是否在导航、是否倒车…）。

## 六、投屏 —— `desay.projection.jar`

### `com.desaysv.ivi.projection.IProject`（AIDL，`evidence/inspect/projection.IProject.json`）
```java
void startProjectToDisplay(int, int);
void startProjectToDisplayWithSurface(android.view.Surface, int, int);   // 传自己的 Surface！
void startProjectToDisplays(int, int[]);
void stopProjectForDisplay(int);  void stopProjectForDisplays(int[]);
void setAntiControllEnabled(int, boolean);
```
### `com.desaysv.ivi.vds.projection.DesaySVProjectManager`
- `getProjectManager(Context, Handler[, long, LifecycleListener])`；
- 常量字段（值需再取）：`DESAY_PROJECT_SERVICE_PACKAGE` / `_CLASS` / `_INTERFACE_NAME` / `_SERVICE_NAME`。
- 生命周期监听 `DesaySVProjectServiceLifecycleListener`。

> **这是与 DIM `Presentation` 平行的「德赛投屏服务」**：把一块 `Surface` 投到指定 display。
> 若第三方可绑，可作为「CarPlay 画面投仪表」的另一条路（可能比 `Presentation` 更稳/更被系统认可）。

## 七、多屏 —— `desaysv-multi-display.jar`

`com.android.server.display.DSVMultiDisplayManagerService`（`evidence/inspect/multidisplay.DSVMultiDisplay.json`）：
`isDSVMultiDisplayEnabled()`、`isDSVOverlayMultiDisplayEnabled()`、`isDSVOverlayDisplayDevice(int)`、
`getDSVDisplayIdForDisplay(String)`、`getDSVUserIdForDisplayId(int)`、`isDualUserProject()`、
`getAllowPackages(Context)` / `getDisAllowPackages(Context)` / `getSpecialDisAllowPackages(Context)`、
`MULTI_DISPLAY_CONFIG_FILE` / `MULTI_DISPLAY_DUAL_CONFIG_FILE` / `OVERLAY_DISPLAY_CONFIG_FILE`、
`DISPLAY_TYPE_PHYSCIAL / _NORMAL_VIRTUAL / _OVERLAY_VIRTUAL`。

> 说明本机是**多屏 + 多用户（副驾独立用户）**设计；display 由 XML 配置（文件路径待补）。
> 「哪些包允许上哪些屏」由 `getAllowPackages` 白名单控制 —— 与 DiPlay 的 `Presentation` 能否上仪表直接相关。

## 八、系统手势 —— `dsv-system-gesture.jar`

`com.android.server.wm.CarSystemGesturesManager`：
`onMultiPointerTap`、`onTwoPointerLongPress`、`mutiFingerScaleProcess`、`mutiFingerSlipProcess`、
`sendSystemGestureBroadcast(...)`，常量 `CAR_SYSTEM_GESTURE_MODE_ACTION`，以及
`MSG_TWO/FOUR/FIVE_POINTER_{TAP,PINCH,SWIP_TO_UP/DOWN/LEFT/RIGHT,LONG_PRESS}`。
`CarGestureDetector` / `CarMultiFingerScale` / `CarMultiFingerSplit`。

> 三/五指向下滑 = 截屏、双指长按 = 分屏之类的系统手势；广播 action 值得记录，避免与 DiPlay 手势冲突。

## 九、DiPlay 可借鉴/待实测清单

| 能力 | 入口 | 现状 | 待实测 |
| --- | --- | --- | --- |
| 开/关 Wi-Fi、热点 | `ecarx.fw.api.wifi.TcamWifi`、`adaptapi.wifiap.ExternalWifiManager` | 现在靠 adbd | 第三方 uid 能否 `create()`/`setWifiEnabled`；权限 |
| 仪表导航 | `ecarx.fw.api.diminteraction.EcarxNaviInteraction` | 用 `DimNaviService` binder | 这个框架 API 是否受同等限制；`ACTION_DIM_DISPLAY_AR` 是否真能开导航区 |
| 画面投仪表 | `com.desaysv.ivi.projection.IProject.startProjectToDisplayWithSurface` | 用 `Presentation` | 服务名/包名/权限；能否第三方绑定 |
| 方向盘键（含麦克风键） | `ecarx.car.ECarXCarHardKeyManager.requestHardKeyEvent`、`adaptapi.input.Input.requestKeysInterception` | 用 `com.geely.inputservice` | 新通道能否拦 200231（麦克风键），替代「停用 HiCar」 |
| 麦克风授权 | `adaptapi.policy.Policy.getPermissionPolicy()` / `IMicResourceCallback` | 用 `settings put secure app_permission_mic_*` | 能否代码内申请 |
| HUD 状态/模式 | `adaptapi.hudinteraction.HUDInteraction.requestHUDMode` | OneOS 拿不到 | 本机能否读 HUD 模式 |
| 把 Activity 移屏 | `adaptapi.uiinteraction.MultiWindowImpl.moveActivityBetweenDisplay` | 未用 | 把 CarPlay 移到 display 2 |
| 车况（档位/倒车/昼夜/车速） | `PhoneLinkAdaptorLibManager`、`ecarx.fw.api.navigation.speed.Speed`、VDBus `carlan` | 未用 | 用真实车况做 UI/行为 |

## 十、待补（下一轮）

1. `ecarx.multidisplay.*` / `ecarx.splitscreen.*` 的 `inspect-android-class` 这次失败，需重跑（`inspect-classes.sh`）。
2. 关键服务的**包名/类名/接口名/权限**：`DesaySVProjectManager` 常量值、`IVDBus` 服务名、`EcarxNaviInteraction` 的绑定与权限检查。
3. `KeyCodeForFW`（17.7 KB 常量）与 `adaptapi.input.KeyCode`（17.9 KB）的**完整键码表**。
4. `DSVMultiDisplayManagerService` 的 XML 配置文件路径与 `getAllowPackages` 结果。

> 复现：`carDocs/headunit/tools/collect-rea.sh` 与 `tools/inspect-classes.sh`（需 `REA_JADX_MCP_JAR`）。
