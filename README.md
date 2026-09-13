# Ninebot Enhance

为九号出行添加应用投屏、系统录屏、虚拟屏预览与输入、会话统计的 LSPosed 模块。

## 功能

- 在车辆的“仪表投屏导航”卡片直接启动投屏，无需输入目的地发起导航。
- 在独立虚拟显示器运行指定应用，默认应用尺寸 **848 × 440，160 DPI**，支持自定义分辨率。
- 使用“无（投屏）”模式，通过系统录屏选择分享单个应用或整个屏幕，无需 Root 或 Shizuku / Sui 授权。
- 虚拟屏与模块采集目标为 **20 FPS**，读取最新画面，实际帧率可在统计和日志中查看。
- 设置中选择启动应用，投屏期间不显示启动器；应用离开副屏后可一键重新启动。
- 支持横屏应用布局、手机息屏后保持虚拟屏运行。
- 提供无需车辆或蓝牙连接的“本地模拟”，可在手机上预览和操作虚拟屏。
- 预览支持触摸、返回、旋转画面，以及使用手机当前输入法输入文字。
- 支持 **自动、Root、Shizuku / Sui、无（投屏）**四种方式，开始前检查所选模式和车辆状态。
- 查看采集、编码、发送提交的会话统计，以及原会话编码分辨率、目标 FPS 和码率日志。

## 适配范围

| 项目 | 支持范围 |
| --- | --- |
| 九号出行 | **6.10.10 / 610104038**，版本不匹配时禁用画面替换 |
| Android | **Android 14–16（API 34–36）** 为当前适配范围 |
| LSPosed | 支持 libxposed API 101 或后续兼容 API |
| 作用域 | 仅 **九号出行**（`cn.ninebot.ninebot`），无需勾选地图应用或系统框架 |
| 授权方式 | Root、提供 API 13+ 的 Shizuku / Sui，或系统录屏授权 |

所有模式均需启用 LSPosed 模块。Root / Shizuku / Sui 用于创建和控制独立虚拟屏；“无（投屏）”使用系统录屏。Root 及以 Root 运行的 Shizuku / Sui 需要设备支持 `su 2000`；辅助进程以 shell 身份运行。其他手机、系统和仪表的行为可能不同。

## 安装与使用

1. 安装 `NinebotEnhance-1.0.0.apk`。
2. 在 LSPosed 启用 **Ninebot Enhance**，作用域勾选 **九号出行**。
3. 强制停止并重新打开九号出行，进入车辆信息页，在“仪表投屏导航”卡片点击“设置”。模块没有独立桌面入口。
4. 打开“授权方式”，完成授权并保存。
5. 虚拟屏模式下选择启动应用、设置显示参数并保存；录屏模式会隐藏这些选项，开始时通过系统窗口选择分享内容。修改设置前，先结束当前投屏。

### 授权方式

| 方式 | 使用方法 |
| --- | --- |
| 自动 | 优先使用已授权的 Shizuku / Sui，其次检查 Root |
| Root | 在 KernelSU / Magisk 等管理器中允许 Ninebot Enhance；设置中可点“申请 / 验证 Root 权限” |
| Shizuku / Sui | 先启动对应服务，再点“授权 Shizuku / Sui”，允许后保存 |
| 无（投屏） | 无需 Root / Shizuku / Sui；每次开始后，在系统录屏窗口选择单个应用或整个屏幕 |

已有 Root 连接会被复用；服务重启或连接失效后，投屏前自动重新验证。缺少授权时会提示前往设置，完成授权后重新点击投屏。Shizuku / Sui 被拒绝且不再询问时，需要在其管理界面为 Ninebot Enhance 开启权限；Sui 管理入口见 [官方说明](https://github.com/RikkaApps/Sui#management-ui)。

### 车辆投屏

车辆开机并连接后，点击“全屏投屏”。模块检查所选方式，再通过九号原巡航入口确认车辆状态。开机检查通过且巡航页面就绪后，虚拟屏模式创建独立显示并打开所选应用；“无（投屏）”模式打开系统录屏授权，确认后将录制画面交给九号原编码及发送流程。车辆关闭、检查失败或状态未知时不启动画面采集。等待期间可再次点击按钮取消。

录屏模式下，直接在手机上操作所选应用或整个屏幕，画面尺寸随录制内容自动调整。单应用录制只分享系统选择器中选定的内容；更换分享目标需要结束后重新开始。每次启动都需要系统确认；锁屏或从系统结束录屏会终止当前会话，参见 [Android 录屏说明](https://developer.android.com/media/grow/media-projection)。

### 本地模拟与输入

虚拟屏模式下，在设置中点击“本地模拟”，保存当前选择并启动手机预览，无需车辆开机或连接蓝牙，本地模式不向车辆发送画面。“无（投屏）”模式隐藏本地模拟及虚拟屏配置，不提供独立副屏或远程触控。

预览工具栏提供“返回、横屏、输入法、结束”。“横屏”只旋转手机上的预览，不改变副屏应用或仪表画面的方向。输入时先点击副屏输入框，再点“输入法”。中文和 emoji 转发可能临时使用剪贴板，具体行为见 [预览与输入](docs/preview-input.md)。

## 统计与诊断

在设置中打开“统计信息”。每次新投屏重新计数，结束后保留本次累计值；实时速率使用最近约两秒的采样窗口。

| 指标 | 含义 |
| --- | --- |
| 画面采集 | 虚拟屏或系统录屏收到并转换为 Bitmap 的 RGBA 帧数及帧率 |
| 编码器供帧 | 模块向原捕获流程提供画面的次数 |
| 编码输出 | 命中的编码监听回调次数、数据量及码率 |
| RTP 发送帧 | 观察到的不同 RTP 时间戳结束标记数 |
| 发送提交 | 提交给命中的 RTP 发送方法的包数、字节数及速率 |
| 原会话编码配置 | 原捕获参数、编码请求与输出分辨率、目标 FPS、码率和实测编码速率 |

未命中兼容接口时显示未读取状态。发送提交表示调用发送方法，不代表仪表已收到或显示，也不等同于蓝牙空口吞吐量。目标 FPS、实际采集 FPS 和编码 FPS 分别统计。

车辆投屏运行至少 10 秒后，在设置中打开“日志”，点击“分享完整日志”，通过系统分享窗口发送或保存 UTF-8 `.txt` 文件，不再复制到剪贴板。文件包含导出时两个进程当前保留的日志、连接状态及原会话编码配置，不再额外按 96,000 字符截断；进程重启前或环形缓冲区已淘汰的事件无法恢复。生成文件需要模块服务可连接；连接异常时仍可打开本地摘要。

`ENCODING CAPTURE` 记录原参数，`CONFIG` 记录编码请求，`OUTPUT` 记录输出格式，`RATE` 和 `STREAM` 记录实测速率。停止后仍可分享本次配置，直到下一次启动。日志不记录画面、输入文字或编码载荷。实现细节见 [架构与统计](docs/architecture.md)。

设置底部依次为“关于、日志、关闭、保存”。“关于”显示版本、开源许可证及第三方声明，许可证全文可离线阅读。

## 项目结构

```text
app/src/main/
├── java/dev/ichinomiya/ninebotenhance/
│   ├── core/          会话、几何、采集调度与授权策略
│   ├── hook/          LSPosed 入口、画面替换与统计 Hook
│   ├── client/        九号进程的供帧、服务连接与异步请求
│   ├── ipc/           协议常量与 Binder 数据封装
│   ├── service/       会话管理、握手与录屏前台服务
│   ├── display/       虚拟屏、方向、电源与输入辅助进程
│   ├── privilege/     Root / Shizuku / Sui 授权管理
│   ├── platform/      应用目录与平台适配
│   ├── ui/            设置、预览、系统录屏授权与统计界面
│   └── diagnostics/   日志、编码配置与会话统计
├── res/               Android 资源
└── resources/         Xposed 入口与第三方许可证
docs/                  架构、输入与发布流程
scripts/               构建与签名工具
tests/java/            Java 主机测试
libs/                  固定版本依赖与校验值
gradle/                Gradle Wrapper
version.properties     发布版本与 Android 版本代码
```

## 构建

需要 JDK 17+、Python 3.11+、Android SDK Platform **36.1** 和 Build Tools **37.0.0**。Gradle Wrapper 为 **8.13**，AGP 为 **8.13.2**。

配置 `JAVA_HOME` 和 `ANDROID_HOME`，或在 `local.properties` 中设置 `sdk.dir` 后执行：

```sh
./gradlew :app:assembleDebug
```

Windows 使用 `gradlew.bat`。开发 APK 位于 `app/build/outputs/apk/debug/`，使用调试签名。

发布构建统一使用 `scripts/build.py`。首次使用自己的密钥构建：

```sh
python scripts/create_signing_key.py --jdk "$JAVA_HOME"
python scripts/build.py --sdk "$ANDROID_HOME" --jdk "$JAVA_HOME" --allow-other-signer
```

PowerShell 使用 `$env:JAVA_HOME` 和 `$env:ANDROID_HOME`。`--allow-other-signer` 允许开发者使用自己的证书，签名不同的 APK 不能覆盖官方签名的安装。维护者使用原发布密钥时省略此参数，保留证书校验。

构建脚本校验依赖、编译源码、运行主机测试、打包签名并核对 APK 的包名、版本、作用域、必要类与签名。结果输出到 `dist/`。源码归档和发布流程见 [发布说明](docs/release.md)。

## 使用限制

- 独立副屏使用原应用的数据，不是应用分身；应用的方向、窗口、输入法与后台行为受系统和应用实现影响。
- 系统录屏遵循 Android 的录制限制，受保护窗口可能显示为空白；录屏模式不支持手机锁屏后继续采集。
- 仪表编码尺寸和目标帧率沿用九号原会话参数，修改虚拟屏尺寸或采集目标不会直接修改车辆编码配置。
- 手机预览正常时，仪表仍可能有延迟或卡顿；现有统计无法测得仪表实际显示时延。
- 主机测试和构建检查不代替不同手机、授权后端及车辆的实测。

## 许可证与致谢

本项目采用 **[Apache License 2.0](LICENSE)**。允许在遵守许可证的前提下使用、修改和分发，并保留版权及第三方声明。选择该许可证是为了与已采用的 Apache 2.0 副屏实现保持一致，并提供明确的专利授权条款。

| 组件或参考项目 | 核对的许可证 | 在本项目中的用途 |
| --- | --- | --- |
| Shizuku API 13.1.5 | [MIT](https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE) | 打包官方 API、Provider 与 Sui 初始化类 |
| VirtualDisplay | [Apache 2.0](https://github.com/Ynkcc/VirtualDisplay/blob/67aabb32b87ff9ce90ff31b5e35f53b90ab8bb35/LICENSE) | 副屏方案参考 |
| scrcpy | [Apache 2.0](https://github.com/ynkcc/scrcpy/blob/2926c06c5dc3064ae6d8db706f1a98a37cfcf3f0/LICENSE) | 适配启动及系统接口封装 |
| VirtualDisplayDaemon | [仓库未声明独立许可证](https://github.com/Ynkcc/VirtualDisplayDaemon/tree/0552fe9d9abfbde6e6a21ca71a86976c47b0ceba) | 方案参考，不打包其 daemon 或补丁；其 scrcpy 子模块另有 Apache 2.0 许可证 |
| libxposed API 101.0.1 | [Apache 2.0](https://github.com/libxposed/api/blob/101.0.1/LICENSE) | 仅编译依赖 |
| AndroidX Annotation 1.3.0 | [Apache 2.0](https://dl.google.com/dl/android/maven2/androidx/annotation/annotation/1.3.0/annotation-1.3.0.pom) | 仅编译依赖 |
| Gradle Wrapper 8.13 | [Apache 2.0](https://github.com/gradle/gradle/blob/v8.13.0/LICENSE) | 构建工具 |

外部运行环境另有许可证：[Shizuku 本体为 Apache 2.0](https://github.com/RikkaApps/Shizuku/blob/master/LICENSE)，[Sui 本体为 GPLv3](https://github.com/RikkaApps/Sui/blob/master/LICENSE)，[LSPosed 为 GPLv3](https://github.com/LSPosed/LSPosed/blob/master/LICENSE)，[KernelSU 根许可证为 GPLv3](https://github.com/tiann/KernelSU/blob/main/LICENSE)。本 APK 不包含这些服务、管理器或 Root 模块；连接 Shizuku / Sui 使用上表中的 MIT API。具体组件、固定引用版本及声明见 [第三方声明](THIRD_PARTY_NOTICES.md)。

参考 [VirtualDisplay](https://github.com/Ynkcc/VirtualDisplay)、[VirtualDisplayDaemon](https://github.com/Ynkcc/VirtualDisplayDaemon) 和 [scrcpy](https://github.com/Genymobile/scrcpy) 的副屏方案。Shizuku / Sui 使用其 [官方 API 与 UserService](https://github.com/RikkaApps/Shizuku-API)。本项目不是九号官方产品。
