# USB 与「有线 CarPlay 为什么不行」

> 现象：DiPlay 在本车**无法用 USB 连接 iPhone**（只能无线）。
> 本文用提取物给出**系统侧证据**与结论，并列出可上车验证的点。
> 相关：[`车机兼容记录.md`](../车机兼容记录.md)（VPN 授权缺失、USB 安装）

## 一、DiPlay 的有线路径需要什么

`shared/.../transport/`（测试可得）显示 DiPlay 用：
`android.hardware.usb.UsbManager` → 枚举 `UsbDevice`（iPhone）→ `UsbDeviceConnection` → **NCM** 桥
（`NcmUsbBridge`）→ 隧道跑 iAP2/CarPlay，另需 `VpnService`（NCM 隧道）。

关键前提：**车机必须是 USB Host，iPhone 作为 USB Device 出现**，App 才能 `getDeviceList()` 拿到它。

## 二、车机 USB 侧证据（`getprop` + 权限 + 组件）

### 2.1 Android USB 处在「设备/配件」角色

```text
sys.usb.config            = carplay_carlife,adb     ← gadget(设备角色) 的功能列表
persist.sys.usb.config    = carplay_carlife,adb
sys.usb.controller        = a600000.dwc3            ← 高通 dwc3
sys.usb.configfs          = 1
sys.usb.ffs.ready         = 1
sys.usb.mtp.device_type   = 3
vendor.usb.use_ffs_mtp    = 1
vendor.usb.product_string = SA8155 V2- _SN:
init.svc.usbd             = stopped
init.svc.vendor.usb-hal-1-2 = running
```
- `sys.usb.config` 是 **Android gadget 功能表**，出现 `carplay_carlife` = **车厂自定义 USB 功能**
  （车机作为 USB 外设对外提供 CarPlay/CarLife 通道）。这是**设备角色**的配置。

### 2.2 只声明了「配件(accessory)」，**没有声明 USB Host**

`system/permissions/` 里唯一 USB 相关是 `com.android.future.usb.accessory.xml`：

```xml
<permissions>
  <library name="com.android.future.usb.accessory" file="/system/framework/com.android.future.usb.accessory.jar"/>
</permissions>
```

**没有 `android.hardware.usb.host.xml`** → 系统特性 `android.hardware.usb.host`（`FEATURE_USB_HOST`）
**预期为 false**（本车面向「手机当 host、车机当 accessory」设计）。

### 2.3 车机自己的有线互联怎么做的

| 包 | 类 | 说明 |
| --- | --- | --- |
| `com.geely.usbservice` | `service.UsbService`、`common.libs.usb.service.IUsbService/IUsbListener`、`manager.UsbManagerImpl`、`database.UsbScanManager`、`player.UsbMediaPlayer`、`psdscreenupgrade.*` | USB 只做**媒体扫描/播放 + 副驾屏升级**，不做手机互联 |
| `com.geely.hicar` | `service.UsbDelegate(Impl)`、`broadcast.UsbBroadcastReceiver`、`ConnectUsbLayoutBinding` | HiCar **有线 USB** |
| `com.geely.carlink` | `broadcast.UsbBroadcastReceiver` | CarLink 有线 USB |
| `com.upuphone.starrynet`（无界互联） | `com.ucar.connect.aoa.{AOAAccessoryReceiver, AOAPreambleBuilder, UsbNative}`、`com.upuphone.usbtrans.engine.UsbNative` | **AOA（Android Open Accessory）**：手机 host、车机 accessory |
| `desaysv-phonelinkadaptor` | `PhoneLinkAdaptorLibManager.setUSBControl()` → `PhoneLinkVehicleHal.setUSBControl()` | 通过**车辆 HAL** 请求切换 USB 模式 |
| `fw-autolink.adapterservice` / `CarService` / `DIMInteraction` | `android.car.AoapService` | AOSP Car 的 **AOAP** 服务（配件协议） |

⇒ 车机的有线手机互联是 **accessory/AOA 方向（车机当外设）**，且 **USB 模式由 native/车辆 HAL 管理**
（`PhoneLinkVehicleHal.setUSBControl`），不经过 Android 应用层。

## 三、结论：为什么 DiPlay 有线不通

1. **角色相反**：CarPlay 有线要求**车机当 USB Host**，而本车 USB 配置是
   `carplay_carlife` gadget（设备角色）+ 只声明 accessory。
2. **Host 特性未声明**：没有 `android.hardware.usb.host` → `UsbManager` 主机路径不可用/不可靠。
3. **USB 口被 vendor gadget 占用**：`sys.usb.config=carplay_carlife,adb`，Android 侧不是 host，
   `UsbManager.getDeviceList()` 看不到 iPhone → DiPlay 的 `NcmUsbBridge` 拿不到 `UsbDevice`。
4. **厂商 USB 由 native/QNX 管**：`PhoneLinkVehicleHal.setUSBControl()`；原厂 CarPlay 走的就是这条路，
   第三方 App 插不进去。
5. 另外缺 `com.android.vpndialogs`（`VpnService` 授权弹窗），即便有 host 也要先
   `appops set <pkg> ACTIVATE_VPN allow`（见兼容记录第一节）。

⇒ **本机「有线 CarPlay」在应用层基本不可行；无线（Wi-Fi Direct）是既定正确路径。**

## 四、上车可验证（用 `samples/carprobe` 或 adb）

```sh
# 特性：host / accessory
adb shell pm list features | grep -i usb
# USB 状态与角色
adb shell dumpsys usb
adb shell ls /sys/class/udc 2>/dev/null            # 有内容=设备角色可用
adb shell 'cat /sys/class/usb_role/*/role 2>/dev/null'
# gadget 功能
adb shell getprop sys.usb.config
# 现有手机互联的 USB 日志
adb logcat -s UsbService:* UsbDelegate:* AOAAccessoryReceiver:* UsbNative:*
# 从 carprobe 里试
#   UsbManager.getDeviceList() 是否能看到 iPhone（host 用例）
#   UsbManager.getAccessoryList()（accessory 用例）
```

若 `pm list features` 里**有 `usb.host`** 且 `dumpsys usb` 显示 host 连接，则还有戏；
否则要从 vendor 侧（`setUSBControl` / 内核 role 切换）想办法，属提权范围。

## 五、一句话

**本车 USB 是「车机当外设（accessory/AOA）+ 厂商自定义 `carplay_carlife` gadget」，USB Host 特性未声明，
且 USB 由 native/车辆 HAL 掌管**——这正是 DiPlay 无法走有线 CarPlay 的系统原因；
原厂有线 CarPlay 走的是 native/QNX，不经 Android 应用层。所以本机继续用**无线**即可。
