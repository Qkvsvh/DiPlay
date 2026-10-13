# Launcher（`com.android.launcher3` / OneOS-Launcher3）功能全貌

> 分析对象：`/system/priv-app/OneOS-Launcher3/OneOS-Launcher3.apk`
> （`com.android.launcher3`，版本 `CS11A4_DEV_V_2511261207_2ffca37_N`，30719 个类，504 个 layout）。
> 来源：REA 类清单（`evidence/OneOS-Launcher3/`）+ 关键类成员（`evidence/inspect/launcher.*`）
> + `aapt2`/资源清单（普通工具）。
>
> ⚠️ 这是**深度定制的 Flyme Auto 桌面**，不是 AOSP Launcher3。包很大是因为内嵌了支付宝（17349 类）、
> BouncyCastle、SensorAnalytics、BlockCanary 等。

## 一、页面模型（顶层功能分区）

| 页面 | 关键类 | 作用 |
| --- | --- | --- |
| 首页（卡片式桌面） | `com.geely.view.mainpage.MainPage` / `BaseMainPage` / `MainHelper` | 首页卡片流 + 小组件区 |
| 应用页/应用中心 | `com.geely.view.apppage.*`（`AppPage`、`AppPageAllAppsView`、`AppPageServiceView`、`DesktopPage`） | 「我的应用 / 小程序 / 无界互联」列表 |
| 零页（生态卡片） | `com.geely.view.zeropage.ZeroMainPage` + `ecology.*` | 左侧「零页」卡片（场景/推荐/日程/视频…） |
| 全局搜索 | `com.geely.view.globalsearch.GlobalSearchActivity` | 应用/文件/设置/联想词搜索 |
| 副驾屏 Launcher | `com.geely.view.psd.*`（`PsdActivity`、`PsdMainPage`、`ClassifyActivity`、`AppCenterActivity`、`offsidepage.*`） | 副驾屏独立桌面 |
| Flyme 桌面 | `com.geely.view.flymelink.*`（`FlymeDesktopPage`、`FlymeCastSink`） | Flyme Link 应用流转/投屏 |
| 壁纸编辑 | `com.geely.ui.wallpaper.WallpaperEditActivity` | 主题/壁纸 |
| 手机互联状态 | `com.geely.view.carlink.CarLinkStatusActivity` | CarLink/HiCar/Flyme 连接页 |

**Activity（manifest，`aapt` 观测）**：`com.android.launcher3.Launcher`(HOME)、
`CarLinkStatusActivity`、`CsdAppSearchActivity`、`PsdActivity`、`AppCenterActivity`、`ClassifyActivity`、
`PsdAppSearchActivity`、`WallpaperEditActivity`、`com.flyme.auto.core.launcherflow.CastSinkActivity`(投屏接收)、
`GlobalSearchActivity`。
**Service**：`com.geely.BootService`、腾讯 `FusionSdkService`、`IPCRouterService`、
星纪魅族 `LifecycleService`、`androidx.room.MultiInstanceInvalidationService`。
**Provider**：`LauncherProvider`(`com.android.launcher3.settings`)、
`InteractiveContentProvider`(`com.android.launcher3.interactive`)、`SensorsDataContentProvider`。
**Receiver**：`com.geely.recommend.AppRecommendProvider`、`UnInstallResultReceiver`。

## 二、首页（MainPage）——卡片 + 小组件

- 卡片/小组件容器：`WidgetContainerView`、`WidgetRecycleView`、`WidgetPreviewView`、`WidgetBlurLayout`、
  `WidgetWeatherLayout`、`WidgetFoldView`（**折叠组件**）。
- 组件尺寸 holder：`Car1x1Holder` / `Car2x1Holder` / `Car2x2Holder` / `Car3x1Holder`（1x1/2x1/2x2/3x1）。
- 组件操作弹窗：`WidgetOperatePup`、`popup_widget_operate_layout`、`WidgetFoldRemoveDialog`、
  `ComponentRemoveDialog`、`WallPaperDisableDialog`。
- 悬浮窗：`FloatWindow` / `FloatWidgetManager` / `DragFloatViewHelper`（可拖拽的小组件浮窗）。
- 天气：`WeatherAdapter`、`WidgetWeatherLayout`。
- 特殊弹窗：`HvacMassagePup`（**座椅按摩**）、`MediaSourceListPop`（**音源列表**）、`WallpaperPop`。
- 拖拽：`CustomDragController` / `CustomDragLayer` / `CustomDropTarget`（自研拖拽层）。

## 三、应用页 / 应用中心（AppPage）

- 三个标签（实测已有）：**我的应用 / 小程序 / 无界互联**（`AppPageAllAppsView`、`AppPageServiceView`）。
- 列表与索引：`AppPagedView`、`SlidIndexBar`（**右侧字母快速索引**）、`AppPageIndicatorDots`、
  `AppAdapterManager`、`FolderAdapter`（**应用文件夹**）、`SearchAppListAdapter`、`SearchHiCarAdapter`。
- 交互：`CustomDeepShortcutView` / `ShortcutPop`（**长按快捷方式**）、`AppDeleteDialog`、
  `RecycleViewTouchHelper`（拖拽排序）、`AppletsDragCallback`。
- 桌面设备/多屏：`DesktopDevicePopWindow`、`DesktopListAdapter`、`Item_desktop_device_layout`。
- 图标：`DesktopAppIcon`、`PsdAppIcon`、`BubbleTextView`。

## 四、全局搜索

`com.geely.view.globalsearch.GlobalSearchActivity` +
`com.geely.view.mainpage.adapter.search.{GlobalSearchAppAdapter, GlobalSearchFileAdapter,
GlobalSearchSettingAdapter, LinkWordAdapter}` +
`com.geely.model.globalsearch.GlobalSearchRepository` +
`SearchHistoryDao`（搜索历史，`ClearSearchHistoryDialog`）。
→ 可搜 **应用 / 文件 / 设置 / 联想词**，并保留历史。

## 五、应用白名单与「应用建议」

- **白名单资产**（决定「我的应用」里显示什么）：
  - `assets/system_app_config_a1.json`（49 个：系统应用）
  - `assets/system_app_config_a2.json`（差异化：多 `com.geely.energymanagement`/`com.geely.smartpilot`）
  - `assets/system_app_config_vp.json`
  - 结构：`[{typeName, appList:[{appName, packageName, isSystemApp, itemType}]}]`，`itemType` 1=预置。
  解析：`com.geely.model.config.ResXmlParser`、`com.geely.model.db.repository.ShortcutDbRepository`。
- **应用建议**：`com.geely.recommend.AppRecommendProvider` / `AppRecommendMng` /
  `DefaultAppIconConf` / `DefaultAppTitleConf`；`AppRecommendMng` 的方法：
  `getRecentAppPackages()`（最近任务）、`getDefaultPackages()`、`getOrderedAppPackages()`、
  `checkRecentApps()`（周期性任务）、`isCarLinkConnceted()`/`isHiCarConnnected()`/`isFlymeConnected()`、
  `isConnected(int)`。
- 商店 provider（可写，见 [`车机桌面集成结论.md`](../车机桌面集成结论.md)）：
  `content://com.geely.appstore.AppstoreProvider/appInfo`；桌面侧
  `com.geely.view.appstore.repository.AppStoreHelper` + `AppStoreDao`。

### a1 白名单内容（节选）

系统应用：应用商城 `com.geely.appstore`、多媒体 `com.tencent.wecarflow`、MyCar `com.geely.settings`、
用户 `com.geely.membercenter`、日程、主题、AI语音助手 `com.baidu.dueros.assistant`、天气、智能生活、
自动泊车 `com.ecarx.pas.apa`、360全景 `com.ecarx.parking`、**全局搜索 `com.android.launcher3`**、
情景广场 `com.geely.scene.mode`、电话、图库、行车记录仪、用户手册、Flyme Link、HUAWEI HiCar、
**ICCOA Carlink**、记忆泊车、百度地图、高德地图、吉利地图、AI实验室、浏览器、QQ音乐、网易云音乐、
火山车娱、腾讯视频、B站、喜马拉雅、爱奇艺、帆书、**雷石KTV `com.thunder.carplay`**、今日电台、
咪咕快游、百度网盘、咪咕视频、酷狗/酷我、嘻哈酷跑、马力车游、宝宝巴士、Babyfirst、优酷。

> ⚠️ 白名单里互联包名是 `com.desay.carlink` / `com.desay.hicar` / `com.desay.flyme`，
> 而**实际安装**是 `com.geely.carlink` / `com.geely.hicar` / `com.geely.flymelink`。
> 说明白名单是跨车型通用资产，或存在包名别名 —— 集成时以实际安装包名为准。

## 六、小程序 / 服务生态

- 腾讯：「小程序」→ `com.geely.view.common.applets.tencent.FusionAppletsClient`（`MossBody/MossInfo`），
  `FusionSdkService`。
- 斑马（`ze`bra/ebamana）：`com.geely.view.common.applets.zebra.ZeBraAppletsClient`
  （`ZEBRA_PACKAGENAME`/`ZEBRA_SERVICE_NAME`/`ZEBRA_APPCENTER_CODE`，`parseAppletsJsonData`）。
- 数据库：`AppletsDao` / `AppletsDbRepository`（`AppletsEntity`）。
- 云服务客户端（`com.geely.lib.cloud.*`）：`usercenter`、`appstore`、`engine`、`maintain`、`payment`、
  `membercenter`、`schedule`、`weather`、`themestore`。

## 七、零页生态卡片（ZeroPage）

`com.geely.view.zeropage.ZeroMainPage` + `ecology.*` 卡片类：
`AlipayCard`（支付宝）、`CleanMemoryCard`（一键清理）、`OtaCard`（OTA）、`RadioOneCard`/`RadioTwoCard`
（电台）、`RecommendCard`、`FeedbackCard2`、`FragranceCard`（**香氛**）、`NoaDriveCard`（**NOA 智驾**）、
`ScheduleTodoCard`/`ScheduleImportantCard`（日程）、`SceneCard`/`SceneCardFloating`（场景）、
`VideoTypeOneCard`/`VideoTypeTwoCard`（视频）、`ZebraCard`/`ZebraBannerCard`/`ZebraRecommendView`
（斑马推荐）。
数据模型：`CardConfigBean`、`ZeroCardId`、`CardType`、`CardsDao`；`OnGlyCarSpeedListener`（用车速驱动卡片）。

## 八、副驾屏 Launcher（PSD）

- 主入口：`PsdActivity`、`PsdMainPage`、`ClassifyActivity`（分类）、`AppCenterActivity`（应用中心）、
  `PsdAppSearchActivity`。
- 页面：`OffSideAppPage`、`OffsideDesktopPage`、`PsdOffsidePage`、`PsdOffsideA3Page`、
  `PsdOffsideScreenProjectionCard`（**副驾投屏卡片**）。
- 游戏区：`PsdOffsideGameCard` / `PsdOffsideGameListCard` / `PsdOffsideGameItemAdapter` /
  `AppStoreGameData`（**游戏中心**，从应用商店拉取）。
- 内容卡片：`item_psd_zero_food`、`item_psd_zero_hot_movie`、`item_psd_scene_spot`、
  `bm_*_vice_screen_{attractions,food_hotel,movie}`（美食/酒店/景点/电影）。
- 视频：`VideoAdapter` / `VideoResourceManager` / `VideoConstant`（副驾视频内容）。
- 壁纸/主题独立：`PsdWallpaperWindow`、`OneOsWallpaperManager` 的 `psd*` 系列。
- 交互：`PsdMainPageTouchController` / `OffSideAppDragCallBack`。

## 九、壁纸 / 主题

`com.geely.ui.wallpaper.*`（`WallpaperEditActivity`、`WallpaperContentView`、`ThemeWallpaperAdapter`、
`WallpaperRepository`）+ `com.geely.view.common.wallpaper.OneOsWallpaperManager`：
- 支持**静态/动态壁纸**、**云壁纸**（`CloudWallpaperData`）、主题包；
- **CSD（中控）与 PSD（副驾）各自独立的壁纸/状态栏主题**：
  `changeWallpaper(boolean,int)`、`getPsdWallpaper()`、`getCsdWallpaperUiMode()`、
  `registerPsd/CsdWallpaperColorsChanged`、`isLiveWallpaper()`；
- 播放器：`WallpaperVideoPlayer` / `STIjkExo2MediaPlayer` / `STExo2PlayerManager`（**动态壁纸视频**）。
- 主题资产在 `com.geely.theme`（[`07`](07-启动界面与品牌.md)）。

## 十、无界互联 / 投屏（Flyme + CarLink + HiCar）

- `CarLinkStatusActivity`：显示 **CarLink / HiCar / Flyme Link** 连接状态与帮助弹窗
  （`CarLinkHelpDialog`、`dialog_hicar_help`、`dialog_fly_me_link_help`），
  可 `sendBroadcastTostartHicar()`、`startMergeDesk()`（合并桌面）。
- `com.geely.view.flymelink.*`：`FlymeDesktopPage`、`FlymeLinkHelper`、
  `FlymeRecommendAppListAdapter`（推荐应用）、`FlymeLinkManagePopWindow`、`IFlymeDeviceConnectStatusListener`。
- **投屏接收**：`com.flyme.auto.core.launcherflow.CastSinkActivity` +
  `com.geely.view.flymelink.FlymeCastSink`（`CastSinkView`、`startCast`/`releaseCastSink`/`resize`）
  → 车机把手机投屏画面嵌进桌面（`floating_window_cast_sink`、`activity_cast_sink`）。
- HiCar 打开：`SearchHiCarAdapter` + `ISearchHiCarClickListener`。

## 十一、启动与初始化

- `com.geely.BootService`（`onCreate/onBind`）。
- `com.geely.model.starttask.*`：`SdkInitTaskManager` 串起
  `OneOsApiTask`、`CloudServiceTask`、`RecommendationTask`、`AppletTask`、`VehicleTask`、`SensorsTask`。
- 数据库：Room `LauncherDatabase`（+`OldLauncherDatabase` 迁移），DAO：
  `AppStoreDao`、`AppWidgetDao`、`AppletDao`、`SearchHistoryDao`、`ShortcutDao`、`VehicleDao`；
  仓储：`ShortcutDbRepository`、`AppStoreDbRepository`、`AppletsDbRepository`、
  `SearchHistoryDbRepository`、`VehicleDbRepository`、`WidgetDbRepository`。
- OneOS 桥：`com.geely.model.oneosapi.OneOsApiHelper` / `OneOsApiEventCode`。
- 车辆数据：`VehicleDao`/`VehicleMapper`/`VehicleEntity`（车速等 → 驱动卡片）。

## 十二、HMI 组件库与埋点

- **`com.geely.toolchain.hmi.*`**：一整套自定义控件与对话框
  （`HmiButton`、`HmiIconButton`、`HmiRadioButton`、`HmiCircleCheckBox`、`HmiSearchView`、
  `HmiDampScrollView`、`HmiRollTextDialog`、`HmiVolumeDialog`、`HmiVerificationAndRegistrationDialog`、
  `BlurView`/`LayerBlurView`（模糊）…）。DiPlay 若要在桌面风格下做弹窗，可对齐这套。
- 埋点：`SensorsData`（`SensorsDataContentProvider`，`com.android.launcher3.SensorsDataContentProvider`）。
- 卡顿检测：`BlockCanary`（`DisplayActivity`、`block_canary_*`）→ 说明有性能监控。

## 十三、桌面小组件策略（资源）

- `res/xml/widget_whitelist.xml`：白名单小组件，例如
  「自动泊车(kx11) `com.ecarx.xcapa`」「360全景(kx11) `com.ecarx.parking`」
  「自动泊车(ex11) `com.ecarx.pas.apa`」「360全景(ex11) `com.ecarx.pas.avm`」「车载微信 `com.tencent.mm`」；
  以及 `psd` 段（副屏小组件，`displayType=1`）：副屏天气 `com.geely.weather`、
  快捷应用/爱奇艺-副屏/今日电台-副屏 `com.geely.pushwidget` 等。
- `res/xml/widget_blacklist.xml`：屏蔽「主屏 pushwidget `com.geely.pushwidget`」、
  「旧地图导航 widget `com.geely.naviserviceui.NaviGuideInfoWidget`」、「多媒体/天气-副屏」等。
- `custom_widgets.xml`、`widget_profiles.xml`、`widget_blur.xml`、`device_profiles.xml`、
  `default_workspace_3x3/4x4/5x5/5x6.xml`（不同屏型的工作区布局）。
- `WidgetServiceHelper`：绑定组件服务 + 注册缩放监听。

## 十四、交互/动效/其它

- 拖拽/编辑：`com.geely.view.common.drag.*`、`mainpage.listener.helper.*`（`RcvDragTouchHelper`）。
- 侧滑返回：`com.geely.view.common.swipback.*`（`SwipeBackHelper`）。
- 转场动画：`common.remoteanim.*`（`LauncherAnimationRunner`、`AppLaunchAnimationRunner`、
  `AppCloseAnimationRunner`、`LauncherAppTransitionManagerImpl`）、`common.anim.*`。
- 模糊：`CustomBlurLayout` / `CustomPsdBlurLayout` / `ShapeBlurView`。
- 手势：`common.touch.*`（`MainPageTouchController`/`AppPageTouchController`/`ZeroPageTouchController`/
  `PsdMainPageTouchController`）。
- 应用删除/卸载：`UnInstallResultReceiver`、`AppDeleteDialog`。

## 十五、对 DiPlay 的意义

1. **进入「我的应用」的正确方式**已经确定：写 `com.geely.appstore` provider（`APPSTORE_PROVIDER` normal 权限）
   → 桌面 `AppStoreHelper`/`ShortcutDbRepository` 收录为 `itemType=2`（见桌面集成结论）。
2. **`debuggable` 被过滤**：底栏「添加图标」忽略 debug 包，务必装 release。
3. **桌面能画的东西**：首页卡片/小组件（1x1~3x1、折叠）、零页生态卡片、应用文件夹、快捷方式长按菜单。
   DiPlay 若想提供「CarPlay 状态/导航」卡片，形态应对齐 `WidgetContainerView` 这类组件或 pushwidget。
4. **互联入口**：桌面内置 CarLink/HiCar/Flyme 入口与「投屏接收 CastSink」；
   DiPlay 走自建 CarPlay，需注意与这些入口/状态显示并存（`AppRecommendMng.isCarLinkConnceted()` 等会读连接态）。
5. **副驾屏**是独立 Launcher（`PsdActivity` + 游戏/视频卡片），与主屏壁纸/主题分离。

> 复现：REA 类清单 `evidence/OneOS-Launcher3/classes.json`；成员 `evidence/inspect/launcher.*.json`；
> 白名单/组件策略用 `unzip` + `aapt2` 从 APK 直接取。

## 十六、小组件「白名单」能否被写入？——**不能（普通 App）**

### 16.1 白名单/清单的来源（全部是 APK 内编译资源）

小组件显示与否由 Launcher APK 里的这几个 **编译资源 XML** 决定（`com.geely.model.config.ResXmlParser` 解析）：

| 资源 | 作用 | 解析方法 |
| --- | --- | --- |
| `res/xml/widget_profiles.xml` | **主清单**：每个组件的 `displayType`/`container`/`sortId`/`rank`，并按车型能力过滤 | `parseAllWidget()` → `allWidgetList` |
| `res/xml/widget_whitelist.xml` | 白名单（`packageName` + `widgetClassName`，含 `<psd>` 副屏段） | `parseWidgetWhiteList()` → `allWidgetWhiteList` |
| `res/xml/widget_blacklist.xml` | 黑名单 | `parseWidgetBlackList()` → `allWidgetBlackList` |
| `res/xml/widget_blur.xml` | 需要模糊的组件 | `parseWidgetBlurList()` |
| `res/xml/custom_widgets.xml` | 自定义组件 | — |

`parseAllWidget()` 还会读车型能力（`DeviceImp.getVehicleType()` + `VehicleManager.isSupport{SoundEffect,AtmosphereLamp,Avm,SeatWidget}`）
决定「音效/氛围灯/全景/座椅」组件是否出现。

### 16.2 运行期数据（只在 Launcher 私有目录）

- `WidgetRepository.resolveWidgetList()`：枚举已安装的 `AppWidgetProviderInfo`，用白/黑名单过滤，产出 `WidgetEntity`。
- `BaseWidgetVm.initXmlDataToDb()` / `saveWidgetList()` / `getWidgetWhiteBlackList()` / `isNeedWidget()`：
  把 XML 清单落到 **Room 数据库** `LauncherDatabase` 的 `AppWidgetDao`（表里存「已摆到桌面的组件」）。
- 该数据库在 **`/data/data/com.android.launcher3/databases/`**，**只有 Launcher 自己（同 uid）能写**。
- `WidgetFilterBean` 的唯一引用是 Launcher 自己的 `com.geely.viewmodel.widget.BaseWidgetVm`
  （`trace-android-references` 结果），**没有对外 provider**。

### 16.3 与「应用列表白名单」的关键区别

| | 应用列表白名单 | **小组件白名单** |
| --- | --- | --- |
| 存储 | APK 内 `assets/system_app_config_*.json`（只读）+ **应用商店 provider** | APK 内 `res/xml/widget_*.xml`（只读） |
| 可写入口 | ✅ `content://com.geely.appstore.AppstoreProvider/appInfo`（权限 `APPSTORE_PROVIDER` **normal**） | ❌ **无** provider/DB/settings 入口 |
| 第三方能否加自己 | ✅ 写一行即出现在「我的应用」 | ❌ 加不进去 |

> 也就是说：**「能把 App 塞进桌面应用列表」的那条路，不适用于小组件。**

### 16.4 真要改，只能走提权/覆盖

1. **RRO 资源覆盖**（最现实的非 root 路径）：做一个 overlay 包 `targetPackage="com.android.launcher3"`，
   覆盖 `res/xml/widget_whitelist.xml` / `widget_profiles.xml`（含 `<psd>` 段）。Android 11 支持
   **fabricated overlay**（不需要目标签名），但**启用**需要 `CHANGE_OVERLAY_PACKAGES`（signature|privileged）——
   普通 App 没有，只能 `adb shell cmd overlay enable <pkg>`（shell 有该权限）。
2. **root + remount /system**：直接改/替换 APK 里的 XML。本机 `ro.boot.veritymode=enforcing`、
   `verifiedbootstate=green`、bootloader `flash.locked=1` → 正常不可行。
3. **Launcher 自身的编辑入口**：只能摆放**已在清单里**的组件（桌面长按/编辑），不能新增第三方组件。

### 16.5 对 DiPlay 的结论

- 想让 **DiPlay 自己的 AppWidget 出现在桌面组件选择器里 → 默认做不到**（除非 overlay/root）。
- 可行替代：
  1. 走**应用列表**那条（`AppstoreProvider`，release 包）让 DiPlay 出现在「我的应用」；
  2. 页面/卡片信息用 DiPlay **自己的 UI**（或车机内置组件形态，如 `WidgetContainerView` 风格的自绘卡片）；
  3. 若要「车机原生小组件」，只能借**已在白名单里的宿主**（如 `com.geely.pushwidget`）——
     其 provider 已在 `widget_whitelist.xml` 内，但需要系统权限，第三方拿不到。

### 16.6 能不能「冒名顶替」？——**能，因为匹配只看名字，不看签名**

关键代码（`com.geely.viewmodel.widget.BaseWidgetVm`，REA 反编译）：

```java
// filterOptionWidget(): 遍历所有已安装的 AppWidgetProviderInfo
String packageName = componentName.getPackageName();
String className   = componentName.getClassName();
if (isNeedWidget(z, bean, packageName, className)) { arrayList.add(...); }

// isNeedWidget(z, bean, pkg, cls):
if (appBlackList.contains(pkg)) return false;                 // 按【包名】
if (widgetBlackList.contains(cls)) return false;              // 按【类名】
if (z && isNeedFilterWidget(cls)) return false;               // 车型能力门控
return appWhiteList.contains(pkg)                             // ① 包名在白名单
    || ListUtils.exists(widgetWhiteList,
          e -> cls.equals(((WidgetEntity) e).className))      // ② 类名在白名单（只比类名！）
    || (z && pkg.startsWith(PkgConst.PKG_GEELY));             // ③ com.geely 前缀
```

- 白名单来自 `parseAppWhiteList()`（读 `R.xml.widget_whitelist` 的 `<app packageName>`）与
  `parseWidgetWhiteList()`（读同文件的 `<appwidget widgetClassName>`）；
- **全是对字符串做 `contains`/`equals`，没有校验 uid、签名或是不是系统应用。**

因此有三条「冒名」路径：

| 路径 | 条件 | 例子 |
| --- | --- | --- |
| ① **借包名** | 白名单里的包**没被安装**（包名唯一性只管「已安装」，未安装的名字可自由使用） | `com.ecarx.xcapa`、`com.ecarx.pas.avm`、`com.tencent.mm` —— **本机实测均未安装** |
| ② **借类名** | 让 provider 的**全限定类名**等于白名单里的 `widgetClassName`（APK 内 Java 包名与 manifest 包名无关） | `com.geely.pushwidget.widget.subscreen.SubAppWidget` / `SubAiqiyiVideoWidget` / `SubJrdtWidget` |
| ③ **蹭前缀** | `z==true`（displayType 0）时，包名以 `com.geely` 开头即通过 | 用一个**未安装**的 `com.geely.*` 名，如 `com.geely.dvr`、`com.geely.ailab` |

**做不到的**：顶替**已安装**的包名（如 `com.geely.pushwidget`、`com.ecarx.parking`）——
`pm install` 会因签名不一致被拒；系统应用还带平台签名。

**注意事项 / 限制**
- 优先被拒的两条：`appBlackList`（按包名，含 `com.geely.pushwidget` 主屏项）、
  `widgetBlackList`（按类名，含 `WeatherPSDWidgetProvider`、`PsdSourceProvider`、`PsdListProvider`、
  旧 `NaviGuideInfoWidget`）——冒名要避开这些名字。
- `isNeedFilterWidget` 会按车型能力（座椅按摩等）过滤：`z==true` 时挑无关的车控类名（如副屏类名
  在 `displayType!=0` 分支 `z=false`，不做车型门控）。
- 组件仍需是**合法 AppWidgetProvider**（receiver + `APPWIDGET_UPDATE` + meta-data）；桌面摆放仍需人工添加。
- 主清单 `widget_profiles.xml`（`parseAllWidget` → `allWidgetList`）走的是「显式 ComponentName + 车型能力」，
  与上面的「可选组件」过滤是两条路；冒名主要作用在**组件选择器**这条。

> 一句话：**小组件白名单是硬编码的资源没错，但它按「包名/类名」字符串匹配，所以能用「未安装的白名单包名」
> 或「同名类」冒名；不能顶替已安装包（签名校验）。** 相比之下，应用列表白名单是**官方可写**（`AppstoreProvider`），
> 不需要冒名。


