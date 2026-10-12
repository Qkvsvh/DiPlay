# oneos-dimservice — 车机的 OneOS DIM 服务（`com.geely.dimservice`）

从车机拉取的系统应用（`/system/app/OneOS-DIMService/OneOS-DIMService.apk`），用于逆向
**谁来开关仪表的导航区**。APK 与反编译源码**不入库**（见 [`../.gitignore`](../.gitignore)）。

| 路径 | 说明 |
| --- | --- |
| `OneOS-DIMService.apk` | 原厂 APK |
| `out/` | jadx 反编译源码（`--show-bad-code`；`com.ecarx.xui.adaptapi.*` 有 4 个类反编译失败，不影响结论） |

## 关键类

- [`out/com/geely/dimservice/impl/INaviEventListenerImpl.java`](out/com/geely/dimservice/impl/INaviEventListenerImpl.java)
  - 监听 OneOS navi 事件，按 `RspMapStatus`（protocolID 1001）调用 `INaviInteraction`：
    `GUIDE_START(16)` / `SIMULATE_GUIDE_START(33)` → `notifyNavigationStatus(2)` **开导航区**；
    `GUIDE_STOP(17)` → `3` 关；`ROUTE_SUCCESS(14)` → `1`。
  - `RspGuideInfo(3407)` 的转向/距离/ETA/车道在这里转给 `INaviInteraction`。
- [`out/com/geely/dimservice/service/DimService.java`](out/com/geely/dimservice/service/DimService.java)
  - `onBind()` **返回 null**（第三方绑不上）；绑定入口受权限 `geely.oneos.permission.SERVICE` 保护（级别 normal，但拿不到 binder）。
  - `mINaviInteraction = DIMInteractionImpl.create(ctx).getNaviInteraction()`。

## 结论出处

- [`../../吉利仪表与HUD接入调研.md`](../../吉利仪表与HUD接入调研.md) 第九节：**导航区只能由原厂导航打开**，第三方只能写内容。

## 复现

```sh
adb pull /system/app/OneOS-DIMService/OneOS-DIMService.apk .
jadx --show-bad-code --no-res -d out OneOS-DIMService.apk
```
