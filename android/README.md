# Beacon Inspector

一个用于授权测试环境的 Android BLE 测试信标。应用启动并获得附近设备权限后，会自动广播独立的测试 UUID、Major 和 Minor。首次默认 UUID 为 `7E57D004-2B97-4E7A-B45F-5387367791CD`，Major 和 Minor 均为 `1`。修改参数后重新开始广播即可保存，后续启动会恢复上次广播参数。

开启“每日定时广播”后，可设置每天的开始和结束时间，默认时段为 09:00 至 18:00，支持跨午夜时段。计划会在后台运行并在设备重启后恢复。Android 12 及以上需要允许精确闹钟访问，Android 13 及以上需要允许通知权限。每日计划默认关闭，关闭时应用打开会直接开始广播。

## 在 Android Studio 中运行

1. 使用 Android Studio 打开此目录。
2. 安装 Android SDK Platform 35。
3. 等待 Gradle 同步完成后，在 Android 6.0 及以上、支持 BLE 的设备上运行。

设备需要支持 BLE 广播。信标采用 Apple Manufacturer ID 的 iBeacon 数据格式，仅用于本地测试。
