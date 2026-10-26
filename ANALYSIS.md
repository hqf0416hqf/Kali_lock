# 静态调查结果与方案依据

范围：用户自己的天猫校园 5.7.2（50007002）和后来观察到的 5.8.0（50008000）安装包。没有调用门禁后台，没有读取应用私有账号数据，没有提取凭证，没有执行开锁。

## 已实测

- Android 16 / API 36，设备标识不进入报告。
- 用户手动进入开门页后，前台组件为 CampusWebActivity。
- APK 清单由 Androguard 和 Google aapt2 两种解析工具交叉检查，一致确认该 Activity 无 exported=true 且无 intent-filter。
- DispatchActivity 对外开放，匹配 tmallcampus scheme。
- 安装助手时发现原应用变为 5.8.0，未由本项目升级。重新复制本机 APK、解析清单并比较 DispatchActivity、DeepLink、CampusWebActivity、SplashActivity 的方法指令，两版本在这些比较范围内一致。助手 0.1.1 增加新版静态适配；网页按钮与实际开门仍待验证。

## 静态代码证据

以下是行为概括，不把原应用反汇编加入公开源码。

1. DispatchActivity.dispatchIntent 调用 DeepLink.isCoolLaunch。该判断在 ActivityStack 中 Activity 数为 1 时返回 true；此分支保存链接并启动 SplashActivity，另一个分支才直接交给 DeepLink.handle。
2. DeepLink.handle 转交 Navigator。Navigator 支持 /web/open 路由，通过 url 查询参数解析网页目标。存在通用网页入口不代表已找到当前开门页 URL，也不代表获得直接开锁 API。
3. CampusWebActivity 继承 WebViewActivity；后者读取 Intent 的 url 字符串参数。由此确认它是网页容器。当前真实页面地址未提取，避免混入账号或门禁参数。
4. SplashActivity 读取广告相关配置，默认时间字段有 3000、3000、6000、15000 毫秒；这些值可被配置中心覆盖。initCountDownTimer 使用配置的启动到首页时间创建 CountDownTimer；回调受暂停和广告点击状态影响，且回调只是设置状态字段，不能据此宣称一定在 15 秒内进入首页。
5. 静态搜索本次 classes7.dex 的门相关页面 URL 未找到候选。这是有限搜索结果，不能证明整个应用或服务端没有入口。

## 仍未知

追加检查：5.8.0 清单中 activity、service、receiver、provider 等组件名及 action 名，未匹配到 door/unlock/开门/开锁 等明确命名。这是名称筛查，不排除通用路由中的功能，不证明存在可获准调用的后台开锁入口。

- 2–3 分钟延迟的具体阶段与原因；尚无一次受控的完整基线。
- 实际使用的是哪家门锁、蓝牙还是在线通信。包中存在门锁与蓝牙模块不足以推断当前门的通信方式。
- 当前网页是否向无障碍树暴露“我开门”和“开锁”等按钮。用户离开后手机处于锁屏，未唤醒或解锁去读取。
- 冷/热启动差异、实际网络耗时、开门后广告跳转条件。

## 方案选择

后续 0.2.1 更新：取得真实页面固定路径后，进一步发现已导出的 SchemeFilterActivity 把 Intent data 直接交给 DRouter。路由表中 /web/open 对应 CampusWebActivity。改用该页面入口，保留 loginRequired=true，与原 Dispatcher 的先入启动页分支分开。已完成一次受控的原应用强停后导航和一次实际助手图标导航；二者观察窗口都没有首页/启动页生命周期事件。详见 VALIDATION.md。当前图标导航不再需要无障碍，仍依赖原应用。下述界面自动化是此前保留的可选原型。

没有验证过的直达入口，也没有公开或获准的门禁接口。选择原应用正常界面自动化作为可验证原型，保留原应用认证与权限。

无障碍是较强权限，仅在无低权限入口证据的情况下作为显式可选路线。原型默认演练，不自动开启服务。若后续确认低权限入口，应优先替换导航部分。

此版本预期减少手工点击，不拦截广告、不修改初始化、不保证减少等待，不声称已完成用户手机上的一键开门。

## 后续基线与对照

用户在场后，先在同一网络、相似启动状态下手工执行到开门页并计时，再运行演练。助手自动记录启动任务、首次目标界面、观察到启动页、观察到首页、入口点击、开锁按钮稳定可用等时间。启动页出现不等于广告出现，广告出现仍由用户观察标记。

实际开锁仅在用户门边明确配合时进行一次。计时器里的点击提交不能替代门的实际响应证据。保留失败与未观测项目，不把单次成功当作稳定性证明。

参考 Android 文档：

- https://developer.android.com/guide/topics/manifest/activity-element#exported
- https://developer.android.com/guide/components/intents-filters
- https://developer.android.com/guide/topics/ui/accessibility/service
- https://developer.android.com/guide/components/activities/secure-bal
