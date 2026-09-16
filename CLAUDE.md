# NinebotEnhance 项目约定

## 界面文案
- 界面上不写说明性文案：对话框里不放提示段落、注解行或“为什么 / 怎么工作”的解释。
- 控件标签只写名称，后面不加括号补充（写“寄存器探测”，不写“寄存器探测（画在画面左侧并写日志）”）。
- 滑块当前值只显示数值和单位（“45 秒”“100 W”），不写条件或后果。
- 需要解释的内容放到 README.md 或 docs/architecture.md，不进 APK。

## 构建与验证
- 主机测试 + APK：`py -3 scripts/build.py --sdk <SDK> --jdk <JDK>`。
- 设备冒烟：`py -3 scripts/test_hud_device.py --sdk <SDK> --jdk <JDK> --adb <adb> --serial <serial>`；安装后 `adb shell am force-stop cn.ninebot.ninebot`。
- 不提交 git。AES 密钥、IV 和解密脚本只存放在 D:\NinebotReverse\analysis，不进模块源码、APK 或仓库。
- 模块只通过九号自身接口发送只读蓝牙指令。
