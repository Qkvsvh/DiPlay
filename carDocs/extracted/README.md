# extracted — 从车机拉取的应用（本地目录）

这个目录存放**从车机 adb 拉下来的原厂应用**，用于分析车机与 DiPlay 的交互方式。
内容不入库（见 `.gitignore`）：体积大、且属于厂商私有产物，只保留脚本与结论。

| 目录 | 应用 | 说明 |
| --- | --- | --- |
| [`geely-map/`](geely-map/README.md) | `com.geely.map`（GeelyMap.apk，`/vendor/app/GeelyMap/`） | 车机自带高德地图（AutoSDK）。HUD / 副驾屏地图渲染的来源。 |

相关结论文档：[`../吉利地图HUD显示机制.md`](../吉利地图HUD显示机制.md)
