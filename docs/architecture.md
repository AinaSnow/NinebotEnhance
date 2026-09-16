# 架构与统计

## 进程边界

```mermaid
flowchart LR
    U[九号设置 / 预览] --> C[FrameClient]
    C -->|校验 UID 的 Binder| S[模块 RootSession]
    S --> R[自动检查并恢复的 Root shell 连接]
    S --> Z[Shizuku / Sui UserService]
    R --> D[shell 虚拟屏辅助进程]
    Z --> D
    D -->|RGBA Surface| C
    C --> E[九号原捕获 / 编码 / 发送器]
    E --> V[车辆仪表]
    E -.只读元数据.-> T[会话统计]
```

Hook 和 UI 在九号进程；授权 API 仅在模块进程使用。模块持有会话与服务连接，shell 辅助进程创建并持有 VirtualDisplay。ADB Shizuku 直接启动 shell 子进程；UID 0 的 Shizuku / Sui 与 Root 模式在 app_process 启动前使用 `su 2000`，保持包名 `com.android.shell` 与实际 Binder UID 一致。

UserService 仅接受拥有者模块 UID，启动类和 APK 路径来自自己的安装信息；调用者不能传入命令或任意 APK。一次服务仅启动一个会话，使用独立 tag，避免旧连接清理移除后来会话。停止、Binder 死亡和超时均有回收路径。UserService 使用 API 13+ 的 Context 构造器。

`HookCatalog` 是模块依赖的 6.10.10 目标的唯一清单（类、带参数模式的方法、布局与控件 id），`VehicleHooks` / `TirePressureHooks` 的类名常量都引用它。Application 附加后在后台线程按所有类加载器解析一遍，结果写入日志 `HOOK CHECK`、模块摘要和设置页；版本门禁仍只放行 6.10.10，但升级后的缺失项会被具体列出。`RegisterProbe`（core）是调试用的寄存器表：候选名单、每个寄存器最近回复的字节与小端值、回复/变化时间、是否有读取在途、上次是否无回复；`VehicleHooks` 在发出、回复、超时时更新它，`FrameClient` 保存要探测的名单（`probe_registers`，默认全部）并在每次 HUD 脉冲把快照交给 `DashboardHud`，HUD 在开关（`WidgetSettings.REGISTER_PROBE`，默认关、迁移不开启，只在“关于”解锁调试模式后出现）打开时于画面左侧画出两列表格：在途读取琥珀色、3 秒内变化绿色、无回复灰色，每秒重新编码一次以刷新“几秒前”，不拦截触摸。读取按 250 ms 一个轮流进行且同一时刻只有一条探测读取在途，只在值变化时另记一条 `PROBE` 日志；无回复的寄存器沿用两次静默即静音的规则。“全索引扫描”通过反射向 `DynamicCommandFactory.commandConfigMap` 加入 `xdis_1a` 这类合成的 `CommandConfig(name, module, "read", index, "2", …)` 条目，让九号自己的 `hasCommand` / `sendCommand` 路径读取配置里没有命名的寄存器；注入只改本进程内存中的表，条目均为两字节只读。表格超出 58 行时按 `RegisterProbe.prioritized` 取最近变化、其次最近回复的行。`RideState`（core）保存按间隔主动读取的 `rSpeed`（0.1 km/h）与 `rPower`（总线上是无符号 16 位，按有符号解释，动能回收为负；显示条件按绝对值比较）：两者都在 3 秒内、速度不超过 `holdSpeedMax`（km/h，默认 0）且功率原始值在 (`holdPowerMin`, `holdPowerMax`]（默认 100–300）之内，并由 `HillHoldDetector` 确认连续满足 `holdSeconds`（默认 1 秒）后才判定坡道驻车，条件连续不满足（含读数过期）同样达到 `holdSeconds` 才解除；速度与功率按各自的读取间隔（`speedIntervalMs` / `powerIntervalMs`，0.5–15 秒，默认 1 秒）读取，只要速度、功率卡片或驻车避让需要就会读。速度、功率卡片与电压卡片共用 `drawMetric`（一行数值加可选曲线，尺寸相同），在栈中位于电压之上；`SidebarLayout.arrange(…, dodge=true)` 先按含通知抬升的正常布局算出每张卡片的位置，凡与提示框 `HILL_HOLD_TOAST`(574,372)–(848,462) 纵向相交的卡片保持高度、右边缘移到提示框左侧 6 px，其余卡片按原顺序从提示框上沿向上重新堆叠；通知卡片存在时必然盖住提示框，渲染与触摸判定同样左移 `dodgeShift()`；手机状态的内容以卡片自身右边缘为锚，随卡片一起移动，卡片动画照常，`DashboardOcclusion` 在本地模拟里同步画出提示示意。开关 `WidgetSettings.HILL_HOLD_DODGE` 由偏好版本 3 引入，默认开。`VehicleHooks` 还从九号自行轮询的 `rBool` 第 0 位得到开机状态并写入日志。

核心计算类放在 `core/`，不依赖 Android UI；`client/` 管理独立的帧、控制及设置工作线程；`ui/` 只组织界面。跨包的公开方法是工程内部调用边界，不是稳定的外部 SDK。

## 服务绑定与授权

客户端使用 `FrameBridgeService.class.getName()` 生成绑定目标，避免包结构调整后仍拼接过期路径。主机测试核对该目标与 Manifest 的导出服务一致。模块进程没有常驻守护：它只在九号 `bindService(BIND_AUTO_CREATE)` 时被启动，九号退出即解绑并可被系统回收；手机、音乐、音量采样都只在九号请求快照时进行，进程内没有周期任务，唯一能让它在九号之外存活的是系统为通知监听器保持的绑定。`BindingState` 统计从未连上的绑定代数；`ServiceBridge` 在 `bindService` 返回 false 或连续超时未回调时，把 HyperOS / MIUI 的自启动提示附在状态文本里（按 `ro.mi.os.version.name` / `ro.miui.ui.version.name` 或小米系品牌判定），设置页在这类系统上多一个“自启动设置”按钮，依次尝试 MIUI 应用权限编辑器、自启动管理页和系统应用详情页。

遵循 [官方 API 接入指南](https://github.com/RikkaApps/Shizuku-API#guide)，Manifest 中的 `ShizukuProvider` 在模块进程初始化 Sui；不在九号进程请求 Sui Binder，也不重复调用 `Sui.init`。模块服务创建时注册进程级 Binder received/dead 和 permission result 监听，不持有 Activity。收到 ready 回调后才启用授权请求，检查已授权状态与 `shouldShowRequestPermissionRationale`，仅由显式按钮操作调用 `requestPermission`。

请求期间用原子状态阻止重复申请；结果、错误或 Binder 断开会更新状态。授权窗口以 1.5 秒间隔读取状态，保留未保存的授权模式选择；关闭窗口时移除刷新回调。自动读取期间收到授权 / 保存点击时排队一次，不重复提交。状态读取不触发系统授权弹窗。

## 通知与手机状态

`MirrorNotificationListener` 在模块进程通过系统通知使用权读取新增事件，只接纳用户勾选的包；跳过分组摘要和无文本通知。`NotificationContent` 优先解析 [MessagingStyle 消息列表](https://developer.android.com/reference/android/app/Notification.MessagingStyle.Message#getMessagesFromBundleArray(android.os.Parcelable[]))，不读取 historic messages。`NotificationDeduplicator` 按会话记录消息时间、发送者、正文及相同消息出现次数，只输出新增项。首次遇到多条未读消息时记住整个快照，仅展示最新一条；后续快照可同时输出多条新增消息。相同内容、相同时间的多条消息按出现次数区分。普通通知退回 `Notification.when`、标题和正文。系统重新发布时间 `getPostTime()` 不作为新消息依据，因为 [Telegram 会重新发布各会话通知](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/NotificationsController.java)，并保留旧消息自身的时间。

不导入 `getActiveNotifications()` 历史。通知正文仅限内存，标题和正文分别限长 120/240 字符，不进入诊断。去重记录最多保留 128 个会话，每个会话最多 64 种消息。`NotificationHub` 保留最多 32 个事件，每次经过验证 UID 的 HUD_SNAPSHOT 最多携带 8 个事件及 32×32 图标。九号使用独立工作线程每 200 ms 读取，电话和网络查询不占用采集、控制或设置线程；进程、会话或设置修订变化重置游标，不重放上一会话，旧会话迟到结果被丢弃。

`NotificationSettingsActivity` 是不导出的模块 Activity，由九号设置按钮经 Binder 获得不可变 PendingIntent 后打开。系统授权授予模块 UID；应用列表使用模块 `QUERY_ALL_PACKAGES` 查询当前用户所有已安装应用。列表在工作线程读取，已勾选项优先，各组内按应用名排序。设置由模块私有偏好保存，总开关默认关闭、包列表默认空。时长使用 5–30 秒整数滑块，默认 15 秒，旧配置超出范围时夹到边界。电话状态权限由该 Activity 单独请求。

启动应用选择使用独立且不导出的 `LaunchAppPickerActivity`，不依赖通知使用权。九号通过校验 UID 的 Binder 获取一次性不可变 PendingIntent，在模块 Activity 获得焦点后执行已安装应用查询，再读取 MAIN/LAUNCHER 入口，使 OEM 应用列表授权可以在选择启动应用时处理。授权弹窗返回焦点后最多自动重读一次，也可在应用权限页授权后返回刷新。选中的组件通过 ResultReceiver 返回，九号仅修改当前应用选择，保留未保存的显示参数；关闭选择器不保存任何设置。`QUERY_ALL_PACKAGES` 是 Manifest 可见性声明，不传入 Android `requestPermissions()`。

`PhoneStatus` 最多每秒采样一次电量、充电状态、Wi-Fi 连接以及活动卡槽的信号等级，不读取电话标识、号码、SSID 或位置。没有 Wi-Fi 且当前网络是蜂窝数据时还报告默认数据卡的网络代际（`getDataNetworkType`，并用 `TelephonyDisplayInfo` 的覆盖类型识别 NSA 5G），HUD 以 13 px 粗体把它画在 Wi-Fi 图标的槽位（22 px）里。手机状态卡片以右侧的电量百分比为锚：电池图标紧贴其左侧 6 px，再往左是 Wi-Fi/网络类型槽位与 SIM 信号，宽度随之变化。缺少电话状态权限时明确使用未知信号；系统读取失败不编造满格数据。

九号 `DashboardHud` 按 848×480 参考坐标等比绘制。`SidebarLayout` 为绘制和触摸共用的几何定义：最新通知靠右下方 (right=838, bottom=468)，旧通知向上排列；手机状态位于通知上方，再往上依次是音乐、胎压、电压；间距 6。卡片进入时上推整个控件列；退出时每个槽位都移动到下边界，整组移除也不会突然留下高度差。`NotificationTimeline` 使用 elapsedRealtime：440 ms 滑入、340 ms 队列移动、240 ms 滑出，离场卡片占用名额直到退出；同时显示条数来自通知设置（1–5，默认 3），调低时多出的旧卡片立即开始退出，等待中的卡片在名额空出后进入。卡片高 60、槽距 66，因此三条通知把上方控件抬高 198。消费者挂起后过期卡片直接清除；清空操作同时移除等待队列。每条新增消息独立生成卡片与计时，source key 仅用于手机通知被移除时清除对应卡片；HUD 按事件序号忽略重复读取，避免传输重试生成重复卡片。新通知卡片缓存为不可变 Bitmap，动画不依赖源应用继续重绘。本地预览使用同一绘制器，编码输出的去重标记包含 HUD 修订及动画时点；调试图绕过所有 HUD。本地模拟的预览还在 HUD 之上绘制 `DashboardOcclusion`：按实拍标定的车机右上角仪表卡片示意 (640,44)–(842,254)，只出现在手机预览，车辆会话不绘制。

`MusicStatus` 使用模块通知监听器 ComponentName 调用官方 [getActiveSessions](https://developer.android.com/reference/android/media/session/MediaSessionManager#getActiveSessions(android.content.ComponentName))，在单独线程每秒最多采样一次，优先播放中的会话。没有授权时不访问媒体会话；没有元数据时保留未知字段。专辑图缩至 64×64，变化时增加 revision，只有客户端尚未持有对应 revision 时才通过 Binder 传输。不读取远程 URI，不保存或记录媒体内容。

右侧控件最宽 190：手机状态双卡时占满 190（两张卡从左边缘排起，多余空间留在第二张卡与 Wi-Fi/网络槽位之间），单卡或无卡时按内容宽度并保持右边缘 838 对齐；音乐、胎压和开了曲线图的电压固定占满 190，关闭曲线图的电压按 `电压 888.8 V` 模板取窄宽度，宽度只随设置变化，不随实际数值抖动；未播放时不显示音乐卡片，胎压一行若超出宽度则横向压缩而不裁切。宽度由 `DashboardHud` 测量文字得到，`SidebarLayout` 只做钳制与右对齐，触摸判定使用同一组已到位的矩形。自下而上为手机状态、音乐、胎压、电压；手机状态高 28、音乐有曲目时高 84、胎压 28、电压开曲线图 84 否则 28；全部打开、音乐展开且曲线图打开时，音乐顶边 366、胎压顶边 332、电压顶边 242。无会话、已停止或错误状态下不绘制音乐卡片；打开“自动隐藏”时音乐只在曲目、会话或播放/暂停变化后展开 `musicHideSeconds`（3–15 秒，默认 5），到时收起，上方控件下移补位。每张卡片由 `CardMotion` 驱动：布局目标变化时位置与尺寸在 300 ms 内缓动，出现时淡入并从下方 10 px 升入，消失时淡出并下沉 220 ms，会话的首个布局直接就位；通知抬升量直接叠加在静止布局之上，卡片随通知动画即时跟随。动画期间 HUD 修订号按 50 ms 递进。电压曲线取最近 `chartSeconds`（10–60 秒，默认 30）内每次收到的读数（每个接收时间一个样本，最多保留 60 秒、600 个，跨会话保留），在卡片下半部分按时间横向、按自动缩放的电压区间纵向绘制折线与淡色填充，并标出区间最高、最低值；有两个以上样本时每秒重新编码一次。音量条位于左下角 (14,150)–(46,376)，在车机自己的音量图标上方：`VolumeStatus` 在模块进程监听 `android.media.VOLUME_CHANGED_ACTION`，并在每次快照时轮询媒体音量兜底，快照带序号、等级和最大值；HUD 在序号变化时让音量条从左侧滑入（260 ms），停留 2.2 秒后向左滑出；停留期间再有变化只延长停留并在 150 ms 内平滑改变填充高度，滑出途中的变化就地折返；会话首个快照只记录不显示，音量条不拦截触摸。上推后的控件可能进入原车 Overlay 区域；模块无法遮住车辆在接收帧之后叠加的信息。音乐按 PlaybackState 的 position、speed 和单调时钟时间推算进度，暂停不推进、未知位置不伪造，显示不超出 duration。暂停图标叠加在专辑图中央，不显示文字播放状态和播放控制按钮。不读取或显示媒体会话音量。音乐只读取会话快照，不发送媒体控制命令；预览仅在当前可见 HUD 矩形内消耗触摸，关闭的控件和空隙不拦截。

`WidgetSettings` 另保存卡片顺序 `order`（`widget_order` 键：分界线 `COLUMN_DIVIDER`=0 之前是右列自下而上的控件标志，之后是左列自下而上的标志；通知块只能在右列，规范化时会被移回右列底部）和每个控件的显示条件 `WidgetCondition`（`condition_<flag>` 键，编码为 20 个整数：模式、触发位、显示秒数、检查位和八组区间，即速度、功率、电压、音量、前后胎压（0.1 bar）、前后胎温；处于滑块端点的界限视为无界，旧的 12 整数编码仍可读取）。条件有三种模式：始终显示；条件变动时（音量变动、歌曲变更、播放状态变更触发后显示若干秒，`DashboardHud.fire` 为每个等待该触发的控件开一段窗口）；满足条件时（速度、功率、电压、音量、胎压、胎温区间与“正在播放”全部成立，缺少或过期的读数视为不成立）。`DashboardHud.visibleMask` 综合开关、数据是否存在与条件得到可见集合，交给 `SidebarLayout.arrange(settings, visible, …)` 按 `order` 自下而上排列：右列在 648–838，左列在 443–633（`LEFT_COLUMN_LEFT/RIGHT`）；通知块占据它在右列顺序中的位置，只有排在它上方的右列卡片被顶起，而左列整体从通知块上沿、驻车提示框上沿和所有被避让挤到左边的卡片上沿之上开始堆叠；右列（含避让重排后）顶部伸进 `SidebarLayout.INSTRUMENT`(640,44)–(842,254) 的卡片改入左列，按其原中心高度插到左列比它低的卡片之上，右列变矮后自动回去（顺序变动的卡片以抬升后的位置作为动画目标），渲染时按“抬升布局与静止布局之差”逐卡片位移，通知卡片按 `Stack.notificationBottom` 整体平移。音乐旧有的自动隐藏开关只作为音乐卡片没有显式条件时的默认条件保留。

`WidgetSettings` 保存在九号私有 SharedPreferences（版本 5；版本 5 之前保存的驻车最短持续重置为新默认值）：六个控件开关（手机状态、胎压、电压、音乐、通知、音量），胎压的前后胎，电压的曲线图，音乐的自动隐藏，以及胎压、电压、速度、功率四个读取间隔（胎压 5–60 秒，其余 0.5–15 秒，默认 30 秒与 1 秒）、音乐展开时长（3–15 秒，默认 5 秒）以及电压、速度、功率各自的曲线时长（10–60 秒，默认 30 秒）。版本 1 保存的掩码在读取时自动补上版本 2 新增的开关。由 `WidgetSettingsDialog` 编辑，胎压、电压、音乐子项共用 `WidgetOptionsDialog`，通知一行的“设置”打开模块的通知设置界面；保存后即时应用到 `DashboardHud`，无须跨进程读取。禁用的控件没有布局占位。禁用通知清空动画队列，但仍推进收件游标，恢复展示不会重播期间的消息；原有模块端通知总开关、权限及包过滤仍同时生效。隐藏音乐、胎压和电压只关闭绘制，不改变数据观察流程；只要胎压或电压卡片开启，就在车辆投屏或本地模拟期间通过九号读取接口刷新；旧掩码里的 `TYRE_READ` / `VOLTAGE_READ` 位保留但不再起作用。

`TirePressureHooks` 只观察 6.10.10 的 `TirePressureStateParser` 和 `DeviceManager`。解析器 init 绑定车辆身份；蓝牙 onResponse 期间使用 ThreadLocal 收集 parseExtraFloat 实际命中的四个字段，原调用完成后发布，不将其他车辆报文或无关报文视为更新。压力按原生逻辑乘 0.02、温度减 40。网络路径仅查询成功 Response 已展开的 tp_list 缓存，按 tp_position=1/2 确认前/后轮，且在所选车辆蓝牙已连接时跳过；同一 Response 使用弱身份表去重。无新增网络、BLE 命令或传感器绑定操作。

`TireTelemetry` 在九号进程内保存最多八辆车的不可变快照，投屏开始时固定车辆，不跨 Binder 传送车辆身份。每个字段独立保存接收的墙钟时间和单调时钟时间；卡片不显示接收时间；接收时间超过 2 倍胎压读取间隔的字段显示为 `--`，重复云端值不推进时间，过时回复不能覆盖更新的蓝牙值。未知值保留为空；过期状态进入 HUD 帧修订，源应用不重绘时也能更新显示。日志只报告数据来源和年龄，不记录车辆标识或完整网络响应。

`VehicleHooks` 只观察 6.10.10 的 `DynamicDevice.onResponse(NbFrame)`，这是九号所有蓝牙读取回复的必经点。仅处理指令标签为 `rVoltage` / `rVoltage2` / `rVoltage3`、`rVrlaVoltage`、`rTirePressureRealTimeInfo` 以及九号自己轮询的 `rBool` 且设备 SN 等于当前所选车辆的帧；`rBool` 的 256 / 512 / 1024 位表示三个锂电位是否有电池，与九号电池页 readLiBatInStatus 的判断一致，模块据此选择要读的 BMS 电压寄存器；仪表板的 `rVrlaVoltage` 是实测总线电压，90 秒内有回复时优先上卡片，BMS 寄存器只作交叉核对（实测锂电 M5 P 的 `rVoltage3` 恒为标称 7200）；各电压寄存器都按 10 mV 小端解码，超出 10–200 V 的载荷被忽略；胎压实时帧按车型 `tire_pressure` 特性的布局解码（寄存器 1 低字节后胎压、高字节前胎压，寄存器 2 同序为温度，再乘 0.02 与减 40），原始字节为 0 视为无传感器样本。每种标签的首帧原始字节写入日志以便实机核对。主动读取复用九号自己的 `DynamicDevice.sendCommand(name, null, addToFirst=true, timeoutRetry=0, callback)`：在车辆投屏或本地模拟会话中、所选车辆已连接且胎压或电压卡片连同各自的“主动读取”打开时进行，指令先经 `hasCommand` 过滤，胎压按胎压间隔（默认 30 秒）、电压按电压间隔（默认 1 秒）分别调度，只读取已开启的卡片，每条指令未回复前不再重复发送，连续两次无回复的指令在本次会话内静音；仪表板电压有回复时，BMS 电压寄存器每 60 秒只读一次作交叉核对；实测 M5 P 千万台纪念版对 bms1 的 `rVoltage` 从不回复，铅酸版本的电压在仪表板 `rVrlaVoltage`，与九号电池页按 smart_vrla 选择的路径一致。另有 6 秒看门狗清理从未回调的读取。插队与不重试保证每条读取最多占用九号指令分发器一个配置超时（1 秒），不会排在投屏期间可能堆积的其他指令后面。之所以要主动读取，是因为九号详情页的 3 秒胎压轮询在巡航页覆盖期间停止；日志中的 `VEHICLE no reply` 表示车辆在该超时内没有回复，诊断摘要里有发送、回复和未回复计数。它不发送写入指令，也不改变九号自身的轮询。为了不依赖 ADB 就能诊断投屏期间的蓝牙状况，模块日志记录每条指令前 2 次发出、前 3 次回复以及每会话前 20 次未回复和耗时（50 ms 内失败表示指令未构建或被拦截，1 秒左右表示九号指令超时），并在会话期间每 60 秒写一行总线统计：九号分发的指令数、收到的回复数和被 `DynamicDevice.intercept` 拒绝的指令数，按指令名计数；`onResponse` 观察到的自有标签帧每种最多每 30 秒记录一条，SOC 探测帧总是记录。

`BatteryTelemetry` 与胎压快照使用相同的合并规则：按车辆隔离、投屏期间固定车辆、保留接收时间、过时或重复的值不能覆盖更新的蓝牙值。卡片不显示接收时间；胎压字段超过 2 倍胎压间隔、电压超过 5 倍电压间隔没有新数据时显示 `--`。HUD 修订号只包含各字段的过期标志，静止画面只在数值或过期状态变化时重新编码。M5 P（车型 14103）的动态配置没有电池温度指令，卡片不显示温度；温度只存在于九号电池页的服务器接口中，见 [电池数据读取调查](battery-data-research.md)。

通知宽度和同时显示条数保存于模块的通知设置，宽度默认 312、范围 180–600（848×480 参考坐标），条数默认 3、范围 1–5，都用拖动条调整，随九号的每次快照传给 HUD；卡片内容、右对齐位置、动画起点和进度条均跟随宽度。卡片高 60（图标 36，标题 17 粗体，正文 15，App 名 13），在音乐之后绘制；虚拟屏预览的“模拟通知”按钮经 `FrameClient.simulateNotification` 向同一时间线加入一张模块生成的卡片，时长取通知设置的显示时长，不经过通知监听器或收件箱，收件箱关闭时也保留到该卡片退出。手机状态栏始终显示完整电量和网络状态，省去左侧装饰手机图标。

## 统计采样位置

- RGBA：`FrameClient.readImage` 成功复制帧后计数。预览重绘不计入。
- 供帧：原捕获入口成功接收模块画面时计数。可能涉及多个捕获调用，UI 因此标为次数。
- 编码：限定在 `cn.ninebot.capture.encoder.EncodeListener` 实现的带字节载荷回调，若提供 `MediaCodec.BufferInfo` 则按 offset / size 读取大小并忽略 codec-config。没有提供 BufferInfo 时只计参数实际可见范围，不猜测整数参数含义。
- RTP：限定九号的 `BluetoothRtpSender` / `NBBluetoothRtpSender` 发送方法，读取 RTP v2 头和载荷参数范围；排除 RTCP、截断头和错误 padding。没有消费 ByteBuffer 或修改 payload。

编码与发送分别固定本会话首次实际命中的方法，忽略嵌套的其他层，避免同一数据在多层调用被重复累计。原调用异常或明确返回 false 不计入；协程返回只代表方法提交，不能据此证明无线发送成功。RTP 帧按 SSRC / timestamp 的不同结束标记计算，缓存 128 个最近标记；重复提交仍计入包数和字节数。

该 APK 的业务代码有保护，当前静态文件能确认类名，但统计方法的具体签名需在运行时反射匹配。遇到不兼容的回调形态或不暴露原始 RTP 的发送器时显示未命中，不将采集帧或估算数据冒充发送统计。

`StreamStats` 记录每次会话累计值，使用 200 ms 桶计算最近约两秒速率；终止后冻结累计时长、清零实时速率。Hook 在进入原方法时捕获会话统计对象，迟到回调不能混入新会话。日志只记录方法签名及计数，不保存载荷。

## 采集节奏

`FramePacer.TARGET_FPS=20` 同时用于 VirtualDisplay 的 `setRequestedRefreshRate`、Surface 的 `setFrameRate` 和模块 50 ms 读取间隔。刷新请求 / 提示不当作实际采集统计，原九号编码器参数仍从原会话读取。

ImageReader 的回调仅安排读取：首帧立即处理；下一时点前的多个通知合并为一个延迟任务，任务到期才 `acquireLatestImage`，获取并复制当时最新的画面。队列等待期间不持有已取得的 Image，读取后由 try-with-resources 关闭。该方式避免把 49 ms 到达的 20 FPS 帧直接丢掉；旧式“距离上次不足 50 ms 就丢帧”在 49/51 ms 抖动下可能退化到约 10 FPS。

成功复制后按本次读取开始时间安排下一时点，不把复制耗时再次叠加到 50 ms 间隔；队列为空不更新期限、不产生虚假帧。每次仅有一个待处理任务，关闭 reader 时移除回调并重置 ticket，旧任务不能消费新会话的 ticket。所有调度状态都在帧工作线程中访问，长时间阻塞后只读取最新帧，不补发积压次数。系统、渲染或工作线程阻塞仍会使实测值低于 20 FPS，需要用设备日志确认。

## 原会话编码日志

`EncodingHooks` 在九号进程只读监听 MediaCodec 和 `cn.ninebot.capture` 初始化入口。`DirectCastController` 在调用原巡航按钮前启动 `EncodingDiagnostics` 会话，虚拟屏开始供帧时沿用它，避免漏掉更早的编码器配置。框架 Hook 只采纳来自九号捕获调用链、捕获上下文或已登记实例的视频编码器，不把其他播放解码器当成投屏编码器。

原始 `VideoConfig` 只读取有界的实例数值字段，其他捕获类只读尺寸 / 帧率 / 码率等相关字段；不执行 getter、目标对象的 `toString` / `hashCode` / `equals`。重载签名及整数参数位置原样保留，未验证字段不冒充最终编码尺寸。FFmpeg 等非 MediaCodec 路径只记录可见配置，不能从这些日志推断其全部原生编码行为。

编码器接受 `configure` 后记录请求格式，`start` 后安全读取输出格式，并观察应用自身的 `getOutputFormat`、同步 `INFO_OUTPUT_FORMAT_CHANGED` 和异步 `onOutputFormatChanged`。请求 FPS 支持 Integer / Float，输出格式缺少 FPS 不覆盖它。输出尺寸及有效 crop 单独记录，编码器缓冲区对齐尺寸不直接等同可见区域。`setParameters` 只记录数值白名单作为运行中参数事件，不覆盖最初 configure 的请求值。API 语义参见 [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec) 和 [MediaFormat](https://developer.android.com/reference/android/media/MediaFormat)。

同步出队和异步输出回调只读取 BufferInfo。忽略配置包、空 EOS，分片字节累计但完成时才计一帧，以最近 128 个完成 PTS 排除重复，允许时间戳乱序；不读取 / 消费编码载荷。`RATE` 按本次日志间隔计算，`STREAM` 沿用约两秒的采集 / 回调 / RTP 窗口。每约 10 秒记录一次、结束时补最终累计值；尚未观察到接口时速率为“未读取”。这些数字仍不是仪表接收或显示帧率。

元数据以请求标记和弱引用对象身份隔离；调用前保存标记，停止或换会话后不接纳旧结果。同一会话重新配置会清除旧输出格式；旧会话编码器不自动归入新会话。上限为每会话 16 个编码器、32 个原配置来源、128 个弱编码器身份关联；保留完整配置副本到下一次启动供日志分享。部分受保护配置、未覆盖的异步调用或其他进程 / 原生编码路径可能无法关联，保持“未读取”，不从默认虚拟屏参数推测。

## 日志文件与关于页面

设置底部提供“关于、日志、关闭、保存”。摘要直接使用九号进程中的本地状态及模块摘要缓存，服务失联时也能打开。查看日志不自动修改剪贴板。

`LogExporter` 在单独的工作线程导出，8 秒后 UI 超时返回；迟到结果不会弹出分享窗口，卡住的请求不再创建额外工作线程。`FrameBridgeService` 先验证调用方 UID，然后由模块将当前模块日志写入私有缓存草稿，向九号返回追加写入的 `ParcelFileDescriptor`。九号追加原会话配置、连接状态和本地日志，关闭描述符后请求完成。日志正文不经过 Bundle 字符串传输，不使用旧的 96,000 字符尾部裁切。

完成请求必须匹配原 UID 和一次性 token。模块复制生成不可变的 TXT 快照，再授予九号该 URI 的读取权限。`LogShareProvider` 不导出，只允许通过 URI 授权读取指定文件；拒绝写入、草稿、任意路径和过期文件。系统分享 Intent 同时携带 `EXTRA_STREAM`、`ClipData` 和只读权限，接收方由用户在系统窗口选择。方案遵循 [Android 文件分享与 URI 授权](https://developer.android.com/training/secure-file-sharing/share-file)。

文件为 UTF-8。每个进程原有环形日志仍保留最多 240 条、每条正文最多 1,600 字符；“完整”指完整导出当前保留内容，不能恢复已淘汰事件。单文件超过 4 MiB 时明确失败，不静默截断。草稿一分钟失效，缓存保留最近 16 个文件，文件一天后拒绝读取，后续导出时清理并撤销已删除文件的 URI 授权。模块不可连接时仍可查看摘要，完整文件需重连后再分享。

关于页面通过 LSPosed 提供的模块 APK 路径读取打包的许可证和声明，不依赖模块服务，也不读取九号自身同名的 META-INF 资源。

## 覆盖层标定

“关于”中的版本号累计点击五次后显示“调试模式”复选框，不增加提示或说明标签。解锁与勾选状态保存在九号进程的模块专用私有偏好中，重开页面或进程后保留。勾选实时切换供帧，不修改原应用、显示设置或授权后端；取消勾选恢复实时画面。

`CalibrationPattern` 生成不透明、静止的坐标图：10 像素细网格、50 像素刻度、100 像素坐标分区及四边不同颜色的裁切标记。`FrameClient` 在最终供帧时只绘制缓存标定图，覆盖整个合成画布；原应用、背景、音乐和通知不叠加到标定图上。虚拟屏预览使用同一张图，并停止将预览触控送入被遮住的应用。系统录屏与虚拟显示器沿用原会话及授权流程，不绕过车辆开机检查；后台采集生命周期不变。

尺寸优先取当前编码器的有效输出宽高，其次为接受的配置宽高；没有编码元数据时使用 848×480，并在日志中明确标为 fallback。左上角为坐标原点，图中 X/Y 是编码画面的像素坐标，W/H 是标定图宽高；若仪表进一步缩放，需按实际显示比例换算。图案缓存只在尺寸或模式改变时重建，编码器收到的 Bitmap 保持不可变；模式修订号防止去重机制把切换前的旧帧继续当作当前供帧。

## 整帧与虚拟屏布局

`DisplaySettings.width` / `height` 是合成整帧，默认 848×480；`virtualWidth` / `virtualHeight` 是实际应用缓冲区，默认 640×440 / 160 DPI，原点为 `(0, height - virtualHeight)`。顶部标定预留 40 px，右上 Overlay 范围为 (644,44)–(842,257)，默认虚拟屏右边界为 x=640，保留 4 px 间隔。虚拟屏尺寸必须小于或等于整帧，任何越界输入在创建缓冲区之前拒绝，不做裁剪或应用缩放。

`ImageReader` / `VirtualDisplay` 使用虚拟屏尺寸；`PixelPacking.compose` 写入整帧顶部和每行右侧的不透明背景，再完整复制所有应用像素。两种 ByteBuffer 字节序、源行 padding、非连续像素和缺少最后一行 padding 均有检查。最终 Bitmap 固定为整帧尺寸，只分配一张合成图；原编码参数和车辆协议不变。手机预览与最终供帧使用相同 FIT 与 HUD 坐标转换。

`layout_version=2` 随 Binder、模块设置和九号缓存一起读写，背景色保存为 `background_color`。旧版配置把旧应用高度与 `top_inset` 相加得到原整帧高度，奇数高度向上补为偶数，然后按标定比例生成新的虚拟屏大小；旧颜色保留为背景色。保存后删除旧高度/颜色键。默认旧配置 848×440+40 转换为整帧 848×480、虚拟屏 640×440。新版 IPC 描述符为 v6，防止旧模块进程把整帧尺寸当成源 Surface 尺寸。

设置分为整帧宽高、虚拟屏宽高、DPI 与背景颜色三行。颜色字段取消不修改输入，确认只更新表单；应用选择器返回保留尚未保存的显示参数。`PreviewTransform` 同时提供整帧和应用坐标矩阵：先检测 HUD，再处理虚拟屏区域。背景起始触摸不发送，任何触点或历史采样越出应用区域取消整个手势；横屏预览使用相同逆变换。

## 显示与作用域

静态作用域只有九号。副屏使用可信独立显示和原始 RGBA，支持息屏保持，不显示虚拟启动器。手机预览旋转不改副屏方向，模块不暂停地图渲染。默认整帧 848×480、虚拟屏 640×440 / 160 DPI。背景在供帧时合成，原编码参数及蓝牙传输协议保持原样。系统录屏模式不使用这些虚拟屏设置。

## 系统录屏模式

`PrivilegeMode.NONE` 对应“无（投屏）”，预检选择 `MEDIA_PROJECTION`，不调用 Root 验证或启动 Shizuku / Sui UserService。车辆仍须通过原开机检查并进入巡航，才向模块提交录屏会话；本地模拟不允许使用此方式。设置仅隐藏虚拟屏相关控件，原应用和显示参数继续保存，切回虚拟屏模式后恢复。

`ProjectionSession` 接收九号 UID、进程存活 Binder 和 RGBA Surface，创建一次性不可变 PendingIntent。私有的 `ScreenCaptureConsentActivity` 核对会话令牌后调用系统 `createScreenCaptureIntent()`，由用户选择应用或整个屏幕。`ProjectionGrant` 区分首次打开、Activity 重建、结果消费和停止；取消、超时、进程死亡及旧授权结果不能恢复已结束的会话。PendingIntent 的创建端与发送端按 [Android Activity 启动规则](https://developer.android.com/guide/components/activities/secure-bal) 显式配置启动选项，Android 16 的发送端限定可见时启动。

用户确认后，私有 `ScreenCaptureService` 先启动 `mediaProjection` 类型前台服务，再获取 MediaProjection、注册 Callback 并创建一次采集显示。服务不自动重启，也不保存或重用录屏授权 Intent。系统录屏指示和通知可结束会话；`onStop`、服务销毁及九号结束都会回收显示与 Surface。锁屏时遵循系统终止录屏的行为，不沿用独立副屏的息屏保持。实现参见 [MediaProjection](https://developer.android.com/media/grow/media-projection) 与 [前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types#media-projection)。

初始尺寸取手机窗口范围，之后通过 `onCapturedContentResize` 跟随应用窗口、旋转和折叠变化。`CaptureSize` 按原比例缩小到不超过 1280 像素长边及 1280×720 总像素，尺寸取偶数；不读取用户设置的宽高、DPI 或顶部色边。九号创建新 ImageReader，并携带尺寸修订号交给模块；模块仅在会话、UID 和修订号仍匹配时更新同一个 VirtualDisplay 的尺寸与 Surface，成功后才确认并关闭旧接收面。过期更新被拒绝，异常或超时结束会话，不重用授权创建第二个显示器。

录屏帧沿用 20 FPS 最新帧读取和原九号编码 / 发送路径，不录音、不保存视频文件。录屏模式的巡航页显示操作提示，不把实时录制画面再次放进被录制页面，避免全屏分享时出现循环画面；也不提供无权限的触控或键盘注入。采集来源和实际尺寸写入诊断，发送统计语义保持一致。

## 创建前授权检查

车辆和本地入口先发送一次 `prepare_start`，随后只读轮询 `start_allowed` / `start_pending`。`StartPermission` 根据所选模式与当前授权连接决定后端；Root 连接丢失时先重新检查实际权限，不直接弹出拒绝提示。等待期间保持会话空闲，不分配帧缓冲区、不启动应用、不调用巡航。检查成功继续本次启动，失败提示去设置。`StartPermission.Check` 管理同次请求、50 秒等待上限和取消；Activity 暂停及进入设置均撤销该次检查，迟到或重复结果不再启动。模块 `RootSession.begin` 在获取会话租约前再次检查，后续启动锁定已检查的后端，不静默更换授权来源。

Shizuku / Sui 继续使用官方状态查询。Root 通过 `su 2000` 建立 shell 连接，以随机标记及 `id -u` 验证 UID 2000，不将 `su` 文件存在或历史成功标志视为当前授权。Root 模式或自动模式没有可用 Shizuku / Sui 时，启动准备会自动执行此检查。`AuthorizedShell` 持有已验证连接，按任务分离输出和退出状态；连接仍可用时直接复用。EOF、身份不符或模块服务销毁会关闭连接，下次投屏自动重建；晚到的旧任务清理不能关闭新任务。状态表示已有连接可用，不读取管理器授权数据库；管理器撤权不一定终止已经运行的 shell，需关闭连接或重启模块服务。身份切换仍在 Android Runtime 初始化前完成，参见 [KernelSU su 实现](https://github.com/tiann/KernelSU/blob/main/userspace/ksud/src/su.rs)。

自动 Root 检查最多等待 8 秒，设置中的显式申请最多 45 秒；检查本身不创建 VirtualDisplay。已有检查仍在进行时复用它，后续状态轮询只读，避免重复启动 `su`。Root 管理器是否显示自身授权提示由其策略决定；模块不会自动申请 Shizuku / Sui 权限。用户取消投屏检查后，即使 Root 验证随后成功，也只保留连接，不恢复已取消的投屏。主机测试通过模拟进程覆盖流转与清理，不能代替 KernelSU / Magisk 实机验证。

## 创建前车辆检查

车辆会话先进入 `CHECKING_VEHICLE`，调用当前卡片已绑定的 `ivCruise` 原监听。此时尚未调用 `FrameClient.startDirect`，不分配 RGBA 接收面、不创建副屏、不启动所选应用。只读观察 `DashNaviDataMessenger` / `$Companion` 的 `isPowerOn` Boolean 结果；只有本次结果为 true，且本次创建的巡航 Activity 已到前台并获得焦点，才能进入 `CONSENT` 并创建虚拟屏。之后等待帧和编码替换，不再次调用巡航监听。false 立即结束；未知结果、页面不就绪最多等待 90 秒，超时结束。原生逻辑继续负责首次提示、蓝牙与车辆能力检查。

每个原查询在进入时捕获当前等待的请求 ID；结果投递主线程后再次核对请求与阶段。取消、超时、重试及本地模拟忽略旧请求，已拒绝结果不被并发 true 覆盖，准备显示的转换只能执行一次。没有全局缓存“上次车辆已开机”，也不把按钮可见、蓝牙连接或 Activity 存在单独当作开机证明。模块不主动调用电源方法，不改原返回值或车辆指令。

静态依据是目标 APK SHA-256 `225500de847424ef76cdb4a96bd8ada4394b06d870cce4e1c2b3b0c1438a7aa7` 的载荷符号：外层 `classes.dex` 的 `0x2dd0a6b` 包含 `DashNaviDataMessenger$Companion$isPowerOn$1`，`0x2ec2951` 为 `isPowerOn` 方法名。业务方法体仍受保护；这些符号不能证明完整签名或该固件的具体调用链。运行时仅安装签名匹配的 Boolean 返回方法，或末参数为 `kotlin.coroutines.Continuation`、返回 Object 的挂起方法，并记录 `SIGNATURE POWER` / `HIT POWER`。接口未命中时保持未知、禁止创建车辆虚拟屏；需要真机确认成功路径。本地模拟只检查模块授权，不受该观察器影响。

协程按 [Kotlin 挂起返回约定](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.coroutines.intrinsics/suspend-coroutine-unintercepted-or-return.html) 处理：同步 Boolean 直接观察；异步结果通过代理转发原 Continuation 的 `getContext` 与 `resumeWith` 后观察，只接受 Boolean。挂起标志、失败和未知对象不表示开机。原异常解包并原样抛出；原方法自己的 `$isPowerOn$` 状态机重入时不包装参数，避免隐藏状态机类型和恢复标签。观察器不改变原结果和调度上下文。
