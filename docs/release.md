# 发布说明

Ninebot Enhance 的首个发布版本为 **1.0.0**，Git 发布标签为 `v1.0.0`。

## 版本与签名

- 项目名：`Ninebot Enhance`。
- 包名：`dev.ichinomiya.ninebotenhance`。
- `version.properties` 与 `ipc/Protocol.java` 中的版本名称、版本代码必须一致，构建脚本会自动核对。
- 1.0.0 使用 Android `versionCode=34`，保留覆盖安装能力。版本代码是单调递增的安装标识，不是发布次数；后续构建继续递增，首发前仍使用 1.0.0 名称。
- 发布使用固定签名，公开 SHA-256 指纹保存在 `release-signing-certificate.txt`。
- 私钥与密码保存在 Git 忽略的 `signing/`，后续更新继续使用同一密钥。

## 构建与检查

配置 JDK、Android SDK 后，在项目根目录执行：

```sh
python scripts/build.py --jdk "$JAVA_HOME" --sdk "$ANDROID_HOME"
./gradlew :app:assembleDebug --offline
```

PowerShell 使用 `$env:JAVA_HOME`、`$env:ANDROID_HOME` 和 `gradlew.bat`。Gradle 离线构建要求本机已有对应 Gradle 和 AGP 缓存；首次构建可去掉 `--offline`。

发布脚本使用本机 SDK 和仓库固定依赖，无需在线解析 Maven 依赖；它执行 Java 主机测试、资源及 DEX 编译、签名、对齐，以及 APK 身份、版本、Xposed 作用域、必要类和第三方许可证检查。主机测试覆盖几何与输入边界、授权和会话生命周期、车辆启动条件、编码统计与采集调度。

设备验证应分别关注车辆投屏、本地模拟，以及实际使用的授权后端、息屏保持、应用恢复、输入法和预览操作。“无（投屏）”还需验证系统选择器的单应用 / 全屏授权、拒绝、取消后迟到结果、锁屏终止、旋转 / 折叠时的尺寸更新及停止重开。录屏与独立虚拟屏模式切换后应恢复各自界面和配置。日志中的采集或编码 FPS 不能作为仪表接收帧率或显示时延的证明。

关于与日志需验证亮暗主题、四个底部按钮在小屏和大字体下的可见性、离线许可证阅读，以及至少一个接收应用能读取分享的 TXT 文件。检查长日志的文件末尾、中文和 emoji、导出超时后的重试，以及分享过程中关闭日志窗口或旋转手机不会触发迟到弹窗。当前主机测试覆盖大于 1 MiB 的日志保真、UID 所有权、路径限制、过期与清理；这些测试不替代 Android 跨应用 URI 授权实测。

## GitHub Actions

仓库：[Margele/NinebotEnhance](https://github.com/Margele/NinebotEnhance)，初始可见性为 **Private**。

### CI

每次 push、PR 更新和手动运行 `CI` 都会构建，不设置文档路径过滤，也不自动取消较早的 CI。工作流安装 JDK 21、SDK 36.1 和 Build Tools 37.0.0，运行发布构建脚本及 Java 主机断言，再验证 Gradle Debug 构建。构建成功后，在该次 Actions 运行的 Artifacts 中下载 APK、`BUILD-INFO.json` 和 `SHA256SUMS.txt`，保留 7 天。

CI 在 runner 临时目录生成测试签名，不读取发布密钥；CI APK 文件名带 `-ci`，不能覆盖原正式签名的安装。自动 CI 不创建标签或 GitHub Release。

### 手动 Release

1. 在 `main` 更新 `version.properties` 和 `Protocol.java` 中的版本；后续正式发布须使用尚未发布的版本名称，并递增版本代码。
2. 打开仓库 **Actions → Release → Run workflow**，分支选择 `main`。
3. 仅验证构建时，不勾选“发布 GitHub Release”。需要发布时勾选该项；按需选择预发布标记。
4. 工作流使用原发布密钥构建、运行主机断言、验证签名指纹，并生成 APK、当前提交的源码 ZIP、构建信息和校验值。
5. 勾选发布时，构建成功后才创建对应 `v版本号` 标签和 Release，并上传文件。未勾选时只提供 Actions 构建附件，保留 30 天。

`release.yml` 的唯一触发器是 `workflow_dispatch`，推送分支、推送标签和 CI 完成都不会自动发布。Release 仅接受 `main`；重复版本会明确失败，不移动已存在的标签。版本与源码取自触发运行时的提交，而非运行期间更新后的分支。

签名材料存于仅允许 `main` 的 GitHub `release` 环境，包含 `RELEASE_KEYSTORE_BASE64` 和 `RELEASE_KEYSTORE_PASSWORD` 两个加密 Secrets。密钥只在手动 Release 的临时目录还原，构建后清理，不进入仓库、缓存或构建附件。普通 CI 使用只读仓库权限；发布 job 才申请写入 Release 所需的权限。所有外部 Actions 固定到完整提交 SHA。

## 本地源码归档

首发前可以从当前提交导出源码，无需提前创建标签：

```sh
git archive --format=zip --prefix=NinebotEnhance/ --output=dist/NinebotEnhance-1.0.0-source.zip HEAD
```

源码包只包含该提交的项目文件，不包含 Git 历史、构建缓存、本机配置、签名私钥或旧构建产物。确认归档中的版本与 APK 一致，并将源码 ZIP 的 SHA-256 加入 `SHA256SUMS.txt`。保留根目录 `LICENSE` 与 `THIRD_PARTY_NOTICES.md`，打包的项目许可证和第三方声明须与源码版本一致。正式发布时，由手动 Release 工作流为同一提交创建标签并归档。

## 发布文件

| 文件 | 用途 |
| --- | --- |
| `NinebotEnhance-1.0.0.apk` | 已签名的安装包 |
| `NinebotEnhance-1.0.0-source.zip` | 对应标签的源码 |
| `SHA256SUMS.txt` | 安装包与源码包校验值 |

`artifact-checks.json` 用于本地核对构建结果；`build-verification.json` 包含本机工具路径和命令记录，不作为下载附件。第三方声明和许可证随源码及 APK 保留。

日常修改推送到 `main` 或通过 PR 合并，由 CI 产生测试构建；正式标签与 Release 统一通过上述手动工作流创建。
