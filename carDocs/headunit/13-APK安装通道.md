# APK 安装通道：能不能不用 adb 直接装

> 问题：现在装 DiPlay 要「手动开 ADB + push + 拆分关键字 `pm install`」，有没有**不用 adb 直接装**的能力。
> 结论：**有。** 分「用户确认的系统安装器」（DiPlay 已在用）和「厂商商店的静默安装服务」两条，
> 另还有手机互联/OTA/PKI 等通道。证据：REA + `aapt2` + `dexdump`（`evidence/OneOS-AppStore`、`evidence/inspect/st.*`）。

## 一、当前为什么必须 adb

- `pm install` 的静默安装能力来自 **`com.android.shell` 持有 `android.permission.INSTALL_PACKAGES`**
  （`permissions/privapp-permissions-platform.xml` 白名单）。
- 本机 adbd **不吃 TCP**（见 [`车机兼容记录.md`](../车机兼容记录.md) 第七节），所以只能 USB + 关键字拆分。
- `INSTALL_PACKAGES` 是 **`signature|privileged`**，普通第三方 App 拿不到 →
  **「静默安装」本身不是应用能有的权限**，但车厂有几个持有它的系统应用。

## 二、通道 A：系统安装器（用户点确认，无需 adb）✅ DiPlay 已在用

- 组件：`com.android.packageinstaller`（system-priv-app，持 `INSTALL_PACKAGES`）。
- 用法：`Intent(ACTION_VIEW)` + `setDataAndType(uri, "application/vnd.android.package-archive")`，
  或 `PackageInstaller` session API。需 `android.permission.REQUEST_INSTALL_PACKAGES` + 目标来源被允许「未知来源」。
- **DiPlay 现状**：`common/.../update/UpdateApkExport.kt`（导出到 MediaStore/FileProvider）+
  `DiPlayActivity.startInstaller()`（`ACTION_VIEW` 打开系统安装器）——**自更新完全不依赖 adb**，只是要用户点一下。
- 车机设置里若默认禁止未知来源，需要一次性授权该来源。

## 三、通道 B：应用商店的**静默安装**服务（车厂已暴露）⚠️ 需实测校验

`com.geely.appstore` 持有 `android.permission.INSTALL_PACKAGES`，并对外暴露了任务/更新服务：

| 服务 | action | exported | 权限 |
| --- | --- | --- | --- |
| **`com.geely.appstore.task.TaskService`** | **`com.geely.intent.action.TASK_SERVICE_API`** | ✔ true | **无**（内部 `geely.intent.action.APPSTORE_TASK_SERVICE_START`） |
| `com.geely.appstore.appupdate.service.AppUpdateService` | `geely.intent.action.APPSTORE_MANAGE_SERVICE` | ✔ true | `geely.permission.APPSTORE_MANAGE`（**未声明 protectionLevel → 默认 normal**） |
| `com.geely.appstore.remoteupdate.RemoteUpdateService` | — | ✔ true | 无 |
| `com.geely.appstore.selfupdate.SelfUpdateService` | — | ✔ true | 无 |
| `com.geely.appstore.update.UpdateService` | `geely.intent.action.APPSTORE_UPDATE_SERVICE` | ✔ true | `geely.permission.APPSTORE_UPDATE` |

**协议（`com.geely.lib.taskaidl`）**：

```java
interface ITaskService {                     // 绑定 action = com.geely.intent.action.TASK_SERVICE_API
  boolean addTask(ITaskInfo info);           // ← 加任务：下载 / 下载+安装 / 安装
  ITaskInfo getTask(String id);
  List<ITaskInfo> getTaskList();
  boolean pauseDownload/resumeDownload/downloadCanceled/removeTask(String);
  boolean registerTaskCallback(ITaskCallback);
  boolean registerArrangeCallback(IArrangeCallback);
}
// ITaskInfo(Parcelable) 字段：
//   id, type, url, urlMd5, zipUrl, appMd5, packageName, path, hash, expand,
//   status, soFar, total, installProgress
// TaskType: DOWNLOAD_INSTALL=0, DOWNLOAD=1, INSTALL=2
// TaskStatus: PENDING=1, CONNECTED=2, PROGRESS=3, STARTED=6, COMPLETED=-3, PAUSED=-2, ERROR=-1
```

内部实现（可反推校验强度）：
`com.geely.appstore.task.install.{InstallCenter, FwkInstaller, OverlayInstaller, InstallManager, InstallPackageReceiver}`、
`task.utils.{PackageInstallerHelper, PackageManagerHelper, ShellUtils}`、
`task.uninstall.UninstallSilentManager`。
`FwkInstaller`：`install(String, PackagesBean)`、`preVerify(...)`、`verify(...)`、`unzip(...)`、
`backupApk/rollback`、`verifySystem()`、`savePackagesFile/parsePackagesFile` —— 即有个
**「包清单 + md5/签名校验」的安装流程**（`PackagesBean`/`PackageBean`）。

> ⇒ 也就是说：**绑定 `TaskService`（无权限）→ `addTask(type=0/2, url, appMd5/appMd5, packageName)`，
> 商店会自己下载并安装**。这是车厂给「应用分发」用的正规入口，第三方理论上可调用；
> 但是否放行**非商店来源的 APK**取决于 `FwkInstaller` 的 `preVerify/verify`（md5/签名/白名单），
> 需要上车用我们自己的包做只读 PoC 才能确认。

## 四、其它直装通道

| 通道 | 组件 | 说明 |
| --- | --- | --- |
| **手机互联安装** | `com.geely.appstore.transmit.impl.AppStoreInstallListener`（传输）、Flyme Link `com.flyme.link.callback.LinkInstallListener` + `com.meizu.common.widget.InstallProgressBar`、Starrynet `com.upuphone.starrynetsdk.InstallListener` | **手机把 APK 推到车机安装**（无界互联/传输），不用 adb |
| **OTA/升级** | `ecarx.ota.IOtaService`、`com.autolink.otaservice`、`com.geely.upgrade`、`com.desaysv.ivi.vds.upgrade` | 系统/应用升级（多为厂商签名/权限保护） |
| **PKI 自动安装** | `ecarx.pki.autoinstall`：receiver `.PKCSInstall`、service `.PKIAutoInstall`，用 `com.android.certinstaller.INSTALL_AS_USER` | 带 PKI 签名/证书的包自动安装（产线/OTA） |
| **USB / 文件管理器** | `com.geely.usbservice`（OneOS-UsbService）+ 任意文件管理器 | 点 APK 走通道 A |
| **adb（现状）** | `com.android.shell` 的 `INSTALL_PACKAGES` | USB + 拆分关键字 |

## 五、谁有「静默安装」能力

- 仅持 `android.permission.INSTALL_PACKAGES`（signature|privileged）的应用：
  `com.android.packageinstaller`、`com.geely.appstore`、`com.android.shell`、以及 OTA/系统升级件。
- **普通第三方 App 无法获得该权限** → DiPlay 自身不能静默安装；能做的只有：
  1. 走通道 A（用户点确认）——已实现；
  2. 借商店的 `TaskService`/`AppUpdateService`（通道 B）；
  3. 走手机互联/文件管理器。

## 六、给 DiPlay 的建议

1. **自更新**：保持通道 A（`UpdateApkExport` + 系统安装器）。它**与 ADB 无关**，不受关键字过滤影响；
   只需要目标允许未知来源、用户点一次「安装」。
2. **想免用户点按**（装 helper/自更新无感）：唯一现实路径是通道 B——
   在 `samples/carprobe` 里做**只读 PoC**：`bindService(com.geely.intent.action.TASK_SERVICE_API)`，
   `getTaskList()` 读现状，再试 `addTask(ITaskInfo{type=2, packageName=…, path=…})` 看是否被 `FwkInstaller` 接受。
   ⚠️ 这是厂商服务，误用可能触发商店状态/被校验拒绝；只在自己的车机上试。
3. **不要依赖 adb 作为产品能力**：`LynkHeadUnit` 的「adbd 开 Wi-Fi」在本机已证明不可用。

## 七、验证命令

```sh
# 商店服务与任务
adb shell dumpsys package com.geely.appstore | grep -iE 'TaskService|AppUpdateService|INSTALL_PACKAGES'
adb shell dumpsys activity services com.geely.appstore
# 未知来源开关（不同实现位置）
adb shell settings get secure install_non_market_apps
adb shell settings get global install_non_market_apps
# 现有安装器
adb shell pm query-activities -a android.intent.action.VIEW -t application/vnd.android.package-archive
```

> 一句话：**有直接安装能力。**
> 不用 adb 的「用户确认安装」DiPlay 已经具备（系统安装器）；车厂商店还开着**无权限保护的
> `TaskService`（action `com.geely.intent.action.TASK_SERVICE_API`）**与 `APPSTORE_MANAGE`（normal）的更新服务，
> 可作为「静默安装」候选，但要过 `FwkInstaller` 的 md5/签名校验，需上车实测。

## 八、U 盘装 APK 行不行？（第一次安装能否不用电脑）

**本车默认 UI 里没有「U 盘装 APK」的入口**：

- 车机**没有文件管理器**（提取物里无 `DocumentsUI`、也没有「文件管理」应用），
  无法「浏览 U 盘 → 点 APK → 安装」。
- App Store 的确有「外部应用更新」能力
  （`ExternalAppUpdateHomeActivity`、action `com.geely.appstore.external`、`apkPath`/`getApkPath`、
  `application/vnd.android.package-archive`、`APP_INSTALL_EXTERNAL`、`KEY_CLEAR_APKPATH_FILTER`），
  但该 Activity **`exported=false`**，dex 里也找不到「U盘/本地安装」文案 → **不能直接调起**。
- 车机真正的 U 盘通道是**升级**，不是装 APK：
  - `SVVDSUpgrade`（VDS-Upgrade）：`请插入U盘`、`从U盘copy文件到temp目录失败`、`无效的升级包`、
    按配置字升级 `APA/AVM/IDCU/RVC`；
  - `OneOS-OTA`（系统升级）：`com.geely.upgrade.manager.ExternalManager`（外部/离线升级）。
  这两条**只接受厂签名的升级包**，第三方自签 APK 进不去。
- `OneOS-UsbService`（`com.geely.usbservice`）只做 USB 与**副驾屏升级**
  （`UsbService`、`psdscreenupgrade.PsdScreenUpgradeActivity`），不装 APK。

**所以「第一次安装」不用电脑的可行顺序：**

1. **手机互联安装**（最省事）：Flyme Link / 无界互联 / 传输
   （`com.geely.appstore.transmit.impl.AppStoreInstallListener`、`com.flyme.link.callback.LinkInstallListener`、
   `com.upuphone.starrynetsdk.InstallListener`）——手机把 APK 推给车机装。
2. **借商店 TaskService**（前面 §3）：`bindService(com.geely.intent.action.TASK_SERVICE_API)` + `addTask(type=0/2,…)`。
3. **U 盘 + 商店「外部应用更新」入口**：若上车后在 App Store/设置里能找到「本地/外部应用」安装页，
   则纯 U 盘可行；否则没有入口（本车提取物里该页不导出）。
4. **先装一个第三方文件管理器**再 U 盘点装——但装文件管理器本身又要走 1/2，属于鸡生蛋。

> 结论：**不一定要电脑，但「U 盘裸装 APK」在本车默认没有 UI 入口。**
> 纯 U 盘只对厂签名的升级包有效；装 DiPlay 这类第三方 APK，现实是**手机互联**或**借商店通道**。
> 另外：车机 adbd 只过滤 `adb shell` 命令里的 `install` 关键词，**不影响车机自身的包安装器**，
> 所以只要能让车机安装器拿到 APK（文件/传输/商店），就能装，不需要 adb。
