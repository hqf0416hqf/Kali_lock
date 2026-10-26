# 开门页组件与入口检查

## 证据

用户手动进入开门页后，读取 dumpsys activity activities，仅输出前台组件字段：

`com.tmall.campus.and/com.tmall.campus.bizwebview.ui.CampusWebActivity`

未输出或保存原始 dumpsys；未读取 Intent 参数值、页面文字、账号、Cookie 或令牌；未点击开锁。

从本机安装包解析 AndroidManifest.xml，交叉核验 package=com.tmall.campus.and，versionName=5.7.2。

APK SHA256：ce67aa835c4abbf36a2eef2e102f9f27206132487848fb1a546f140e46e721f7。

| 组件 | exported 声明 | intent-filter | 解释 |
| --- | --- | --- | --- |
| com.tmall.campus.bizwebview.ui.CampusWebActivity | 未显式设置 | 无 | 按 Android 默认规则不对普通其他应用开放 |
| com.tmall.campus.and.dp.DispatchActivity | true | VIEW、DEFAULT、BROWSABLE；scheme=tmallcampus | 存在外部链接分发入口，但尚未确认开门页路由 |

application 和 DispatchActivity 均未声明 permission 属性。此结果不是对分发器内部校验、可接受路径或开门能力的验证。

Android 规则来源：https://developer.android.com/guide/topics/manifest/activity-element#exported

## 结论边界

- 不能把 CampusWebActivity 类名直接做成普通应用快捷入口。
- 存在 tmallcampus scheme 是新线索，不等于有公开支持的开门接口。
- 从名称和所在包推测为网页容器；未检查实际页面 URL 和加载内容。
- 未启动任何候选链接、未测量等待时间、未实现或验证一键开锁。
- 后续先静态阅读 DispatchActivity 如何解析链接以及正常页面路由；不猜测执行型 URL，不请求门禁后台。

## 复现清单解析

依赖：androguard==4.1.3、loguru==0.7.3，以及环境已有 lxml。

```bash
PYTHONPATH=/tmp/dorm-door-analysis-python python3 /home/kali/dorm-door-shortcut/inspect_manifest.py /home/kali/dorm-door-shortcut/private/tmall-campus-installed-base.apk
```

该脚本只解析 APK 清单，不运行 APK、不连接手机。已在本机 APK 上运行成功；其 package/version 与此前 ADB 查询一致。

首次复制后 Python 3.10 不支持 hashlib.file_digest，哈希计算报错；APK 复制已完成，随后通过 sha256sum 成功计算上述哈希。工具下载曾失败，最终完成依赖安装和实际清单解析。
