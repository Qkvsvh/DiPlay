# diminteraction — 车机的 DIM 服务（`com.autolink.diminteraction`）

从车机拉取的系统应用，用于逆向**仪表/HUD 的导航与投影通道**。
APK 与反编译源码**不入库**（见 [`../.gitignore`](../.gitignore)），这里只保留说明与结论索引。

| 路径 | 说明 |
| --- | --- |
| `DIMInteraction.apk` | 原厂 APK（`/system/priv-app/DIMInteraction/DIMInteraction.apk`） |
| `out/` | jadx 反编译源码（`jadx --show-bad-code`） |

## 关键类

- [`out/com/autolink/diminteraction/service/DimNaviService.java`](out/com/autolink/diminteraction/service/DimNaviService.java)
  - 注册 binder `DimNaviService`（描述符 `ecarx.dimprotocol.custom.service.IDimNaviService`）。
  - `notifyDimProjectScreenSwitch`（**事务码 9**）→ `SomeIPUtils.Navi.sendScreenAreaAndResultInfo(naviDisplayArea=1/0)`：
    **让车机把 Android 的 display 2（第三方 `Presentation`）投到仪表**。DiPlay 用它做「CarPlay 画面投仪表」。
  - `updateETAFun` 才是真正 `sendIntersectionInfo` / `sendTotalNaviInfo` 的地方——
    **只推转向/距离而不推 ETA，内容不会发到仪表**。
  - `notifyNavigationStatusFun` → `convertNaviStatus`（`NAVI_START(2)` 的映射依赖 `mIsNaviInfoSend`）。

## 结论出处

- [`../../吉利仪表与HUD接入调研.md`](../../吉利仪表与HUD接入调研.md) 第八、九、十节。

## 复现

```sh
adb pull /system/priv-app/DIMInteraction/DIMInteraction.apk .
jadx --show-bad-code -d out DIMInteraction.apk
```
