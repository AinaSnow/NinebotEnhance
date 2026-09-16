# 电池数据读取调查

检查对象为九号出行 6.10.10、测试手机缓存的 `BatteryInfo.bundle` / `NBBatteryInfo.bundle`（Hermes 字节码版本 94），以及手机上缓存的车型动态配置。目标车辆为 M5 P，SN `2LDDH2604J0108`，车型代码 **14103**（`device_list_cache.json` 与 `device-db` 一致）。以下结论来自静态代码与配置，实机回复仍需在车辆连接后核对模块日志中的 `BATTERY frame` 行。

## 读取路径

- `NbBluetoothClient.INSTANCE.getConnectedDevice()` 返回当前连接的 `DynamicDevice`。`DynamicDevice.onResponse(NbFrame)` 是所有蓝牙读取回复的必经点：它按 `Command.getTag()`（即配置中的指令名）缓存一份数据副本。模块在这里被动观察，不改变原逻辑。
- 主动读取：`DynamicDevice.hasCommand(name)` 依据车型配置判断指令是否存在；`createCommandQueue(CommandQueueConfig(0, names, null), timeoutMs)` 创建一次性队列，`setListener` 后由 `start(queue)` 发送，`stop(queue)` 释放。这正是 RN 侧 `BleModule.sendReadCommand` 的实现，超时为每条指令 2 秒。
- `NbFrame.getData()` 为寄存器载荷，`FormatUtilsKt.getShortAt` 按小端解析，每个寄存器 2 字节。

## M5 P（14103）指令表结论

动态配置 `device_commands` 共 659 条指令，与电池相关的结论：

| 数据 | 指令 | 模块 / 寄存器 | 长度 | 结论 |
| --- | --- | --- | --- | --- |
| 电量 % | `rBattery` | dis / 181 | 2 字节 | 仪表页（`device_ui_dashboard`、`device_ui_dash_navi`）和详情页都用 `toInt, 0, 2` 显示，投屏时的巡航页会读取 |
| 电池电压 | `rVrlaVoltage` 优先，`rVoltage` / `rVoltage2` / `rVoltage3` 备用 | dis / 177 与 bms1-3 / 26 | 2 字节 | 实测本车电池在第 3 位（`rBool` = 0x9c03），`rVoltage3` 恒为 7200 即标称 72.00 V，`rVrlaVoltage` = 7931 即 79.31 V，与仪表电量 87% 相符；RN 电池页按 readLiBatInStatus 选电池位寄存器、按 smart_vrla 走仪表板寄存器，都按 10 mV 小端解析 |
| 循环次数 | `rBmsCycleCount` | bms1 / 27 | 2 字节 | RN 电池页 `rBatteryMoreInfoLT` 用 `convertToNumber` 直接显示；可主动读取 |
| 电池温度 | `rBmsTmp` 系列 | 无 | 无 | 14103 指令表没有任何电池温度指令；名称含 Temp 的只有座椅加热和胎温 |
| 第二 / 第三块电池 | `rVoltage2`、`rBms2CycleCount` 等 | bms2 / bms3 | 2 字节 | 配置存在，模块当前只展示电池 1 |

RN 电池页的 `getBatteryInfo` 请求 `/v6/vehicle/battery-info`，服务器返回 `bat_temp`、`bms_cycle` 等字段。这些只在用户打开电池页时请求，不能证明车辆做了新采样，因此模块不把它们当作实时温度。

## 模块实现

- `VehicleHooks` 观察 `DynamicDevice.onResponse`，只接受所选车辆的电压寄存器（`rVrlaVoltage`、`rVoltage` / `rVoltage2` / `rVoltage3`）、`rBool` 与 `rTirePressureRealTimeInfo` 帧，在模块内解码后写入 `BatteryTelemetry` / `TireTelemetry`；胎压实时帧布局来自 14103 的 `tire_pressure` 特性配置。
- 实测发现车辆投屏期间九号自己不再发送任何指令（总线统计只剩模块的读取），详情页的胎压轮询也随之停止。模块改用 `sendCommand(addToFirst=true, timeoutRetry=0)`（当时每 20 秒各读一次；现在胎压默认 30 秒、电压默认 1 秒，均可设置）后，`rTirePressureRealTimeInfo` 在投屏期间每次都有回复（45–1100 ms），`rVoltage` 则 5 次全部超时且未被拦截器拒绝，说明该车的 bms1 寄存器不应答。模块因此同时探测 `rVrlaVoltage`，连续两次无回复的指令在会话内静音。
- 电量、循环次数虽然可读，但当前卡片按需求只显示电压；温度不展示。

## 本地复现材料

分析工作区 `analysis/battery-config/` 保存两份 bundle 的反汇编与伪代码，`analysis/battery-config/14103/dec/` 为解密后的车型配置。配置由 `libnativesecrets.so` 提供的 AES 密钥解密，密钥、IV 和解密脚本只保留在分析工作区，不进入模块源码、APK 或本仓库。分析工具和第三方反编译输出同样不进入项目依赖。

可用符号定位证据：`getBatteryInfo`、`readVoltageData`、`rBatteryInfoLT`、`rBatteryMoreInfoLT`、`rBmsCycleCount`；Android 原生入口为 `cn.ninebot.react.modules.BleModule.sendReadCommand` 与 `cn.ninebot.library.bluetooth.dynamic.DynamicDevice`。
