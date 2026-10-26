# 构建与恢复

## 构建环境

Linux，Python 3.10+ 标准库，Java 11+（含 jdk.compiler 模块）、keytool，Android Build Tools 35.0.0 与 Android API 36 android.jar。应用不用 Gradle、第三方运行时库或网络服务。

本机只有 java 命令，没有 javac 命令，但包含 jdk.compiler 模块；构建脚本已支持通过 java -m 调用编译器。Android API 编译使用 Java 8 语言目标，D8 生成 min API 26 的 DEX。

本次实际运行成功的命令：

```bash
cd /home/kali/dorm-door-shortcut
python3 tools/build.py --tools /tmp/dorm-android-sdk/android-15 --android-jar /tmp/dorm-android-sdk/android-36/android.jar
```

临时 SDK 重启后可能消失。其他机器可使用已有 SDK，把参数替换为自己的 build-tools/35.0.0 和 platforms/android-36/android.jar。也可先阅读 Google SDK 条款，再运行下列脚本，从 Google 下载固定版本到项目 private/toolchain，校验 SHA256，不安装系统包：

```bash
python3 tools/bootstrap_sdk.py
python3 tools/build.py --tools private/toolchain/android-15 --android-jar private/toolchain/android-36/android.jar
```

SDK 来源：https://dl.google.com/android/repository/repository2-1.xml

SDK 条款：https://developer.android.com/studio/terms

下载归档目录名 android-15 是 Build Tools 归档的内部名称；实际工具包版本是 35.0.0，不表示目标 Android 15。

## 输出与签名

- dist/dorm-door-shortcut-0.2.1.apk：本项目开发版 APK。输出版本取自 AndroidManifest.xml。
- dist/SHA256SUMS：该次构建的 SHA256。
- private/development-signing.jks：本地开发签名，绝不放进源码包。密码是 Android 开发常用公开密码，不适合作为公开发行签名。
- build/：可重新生成的资源、class、DEX 和测试输出。

同一源码在不同开发密钥或打包时间下不会得到完全相同的 APK 哈希。此处“可复现”指步骤与行为可重现，不声称 APK 位级可复现。公开发行前应另行管理发行密钥。

```bash
python3 tools/export_source.py
```

导出器通过允许列表生成源码 ZIP，排除 APK、密钥、反汇编、录屏、日志、个人配置和构建产物，不上传。

## 安装

用系统安装器安装本项目生成的 APK，或在用户确认手机端安装提示时运行：

```bash
adb install -r dist/dorm-door-shortcut-0.2.1.apk
```

不要安装仓库参考资料中的第三方模块或降级原应用。0.2.1 图标直达不需要无障碍。只有保留的旧版演练/小组件才需要用户手动授权；不得通过 ADB 自动修改无障碍设置或关闭受限设置。

## 恢复

1. 图标入口只发起一次导航，无后台重试任务；需要停止旧版自动化时，长按图标进入“助手设置”或锁屏。
2. 在系统无障碍设置关闭本助手。
3. 移除桌面小组件，按需卸载“宿舍开门助手”。卸载会删除其本机计时和设置。
4. 天猫校园未修改，无需重装、恢复数据或重新签名。
5. 电脑端可运行 adb kill-server 结束 ADB 服务；会影响其他正在进行的 ADB 会话。

项目 private/ 内含原 APK、反汇编、开发密钥，仅本地保存；研究结束后可由用户选择删除。不得把整个项目目录直接当作发布包。
