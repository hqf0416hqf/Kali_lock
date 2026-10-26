# 第三方仓库初步审阅

## 来源

- https://github.com/xiaojie-yahu/TmallCampus-AdBlock
- https://github.com/Xposed-Modules-Repo/com.puretools.campus
- https://raw.githubusercontent.com/Xposed-Modules-Repo/com.puretools.campus/main/SOURCE_URL
- https://github.com/Xposed-Modules-Repo/com.puretools.campus/releases

本记录依据本次读取的 main 页面、README、SOURCE_URL 和发布页，不是固定提交的源码审计。

## 已确认的公开说明

第二个仓库 SOURCE_URL 指向第一个，两者并非两个独立实现。作者声明依赖 root 和 LSPosed / libxposed API 101+，支持天猫校园 5.0.0。README 声称可去开屏、跳过启动页、精简首页等；精简后保留功能列为扫一扫、校园洗烘、校园吹风，未列出宿舍开门。

当前默认分支文件列表展示 README、APK 及分发元数据，未见模块源码工程。README 声明模块 APK 加密混淆。没有下载或运行二进制，也没有验证其数据处理行为。

README 的许可描述是“仅供学习交流使用，请勿用于商业用途”，未据此认定可自由复用或再发布实现。

## 对本项目的判断

- 本机版本为 5.7.2，不能把 5.0.0 的支持说明当作当前版本兼容证据。
- 没有证据证明模块保留开门入口、能解决本机延迟或适配本机 Android 16 环境。
- 可参考其调查方向：启动页、广告加载、首页布局；不能把作者声明当成本机实测。
- 暂不采用需要 root 的模块路线，不降级应用。继续检查当前版本页面入口和分段耗时。
