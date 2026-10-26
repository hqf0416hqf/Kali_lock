# 独立客户端的登录与调用入口检查

日期：2026-10-27。对象：本机已保存的天猫校园 5.8.0 base APK 与开门页直接引用的公开前端静态文件。只读静态分析，无手机操作、业务 API 请求、开锁指令或凭证读取。

## 本阶段结果

已将一个开锁分支追踪到“前端业务请求 → 原生桥接 → 蓝牙库”，并核对正常登录管理、网页请求配置和可能的跨应用入口。尚未找到可直接交付的第三方后台开门入口，也未建立独立客户端的正常授权链。不能据此认定所有可能方案均不存在。

目前助手依旧只是原应用开门页入口；本阶段没有修改 APK、安装新版或实现后台开门。

## 证据链与限制

1. `p_doorlock-index.js` 的一条分支调用业务请求包装模块，再将返回的 `macId` / `encryptData` 映射为 `ZMUnlockJsBridge.unlock` 的 `macAddress` / `syncData`。这里只检查变量名与调用顺序，未请求、读取或保存实际值。不能由静态分支证明用户当前硬件一定使用这一分支。
2. 该页面引用的 `main.js` 模块 81252 包装 MTOP 请求，模块 80403 的非淘宝环境配置包含 `forbiddenDowngradeH5: true`。这表示所检查配置禁止自动降级为普通 H5 请求；并不证明所有环境、所有请求都只能走原生通道，也未证明独立认证在技术上不可能。没有修改此配置或测试降级。
3. 原应用 `LocalLoginInterceptor.handle` 调用 `Login.checkSessionValid` 和 `UserCenter.autoLoginSync`；`UserCenter.realLogin` 委托登录 SDK，`fullLogin`、`campusLogin` 等处理校园账号状态。`MtopInitJob` 配置 MTOP 实例并注册网页请求拦截器；`SiteRequestInterceptor` 使用 `H5LoginStatusDispatcher` 选择账号状态。证据支持登录、业务请求与原应用初始化有关；没有取得第三方应用可使用的登录注册方式、接入标识或许可。
4. `WebOpenApi` 及其 `MyJSBridgeAuthAopHandler` 是新增线索：存在配置驱动的网页域名与接口许可规则；`OpenApiJsBridge.getAuthToken` 对应的协程先检查 `UserCenter.isLogin`，再通过原应用 APICenter 请求授权结果。这是原应用内部的网页桥接，不能仅凭命名将其认定为公开 Android 跨应用 API。没有调用桥接、读取 token、修改许可规则或伪造来源。尚未证明其授权结果可用于门锁业务。
5. Manifest 检查列出 48 个可能对外导出的组件：24 个 Activity、1 个别名、12 个 Service、10 个 Receiver、1 个 Provider。其中包含未显式指定 exported、但有 intent-filter 的待审查项；工具没有模拟所有 Android 默认导出规则，计数不能当成最终系统可达性结论。未发现明确声明的开锁 Service/Provider。
6. 额外核对 `WidgetDispatchActivity` / `WidgetDispatcher`，其路径仍通向 `INavigator.openPage` 页面导航。`ProtocolMessageReceiver` 委托通用消息分发，尚未发现已验证的开锁命令入口；未遍历全部动态订阅，也未发送测试广播。

## 当前方案判断

| 方案 | 本阶段判断 |
| --- | --- |
| 现有助手直接进入开门页 | 继续保留，依赖原应用，既有导航测试见 VALIDATION.md |
| 新 App 仅包一层同地址 WebView | 不足以完成所检查分支：缺少原生桥接、正常登录与业务请求环境 |
| 让原应用后台替助手开门 | 尚未找到支持这一用途的跨应用入口；本阶段没有实现 |
| 自建完整客户端并卸载原应用 | 尚缺自建客户端的正常认证/授权链，以及可使用的蓝牙通信实现与许可；并未证明永远不可行 |
| 经正式授权的网页集成 | 有原应用网页许可机制这条线索，但是否开放申请、是否支持门锁及能否脱离原应用均未知 |

后续仍可研究公开开发者接入资料和 SDK 许可，或在用户明确配合下一次自然开门时补齐时间线。重复开门、提取登录态或重放授权数据不能替代正常集成。厂商/学校答复是减少接入资格不确定性的一种途径，不是继续静态分析的先决条件。

## 复现

新增 `tools/inspect_integration.py`，读取 APK Manifest 和 DEX，只导出组件结构、候选类名、所选类的方法签名与调用目标，不导出字符串常量或运行时数据。不连接设备或网络。依赖沿用已有 `/tmp/dorm-door-analysis-python` 中 androguard 4.1.3 等本地分析环境。

```bash
PYTHONPATH=/tmp/dorm-door-analysis-python python3 tools/inspect_integration.py \
  private/tmall-campus-5.8.0-base.apk \
  > private/integration-structure-5.8.0.json

PYTHONPATH=/tmp/dorm-door-analysis-python python3 tools/inspect_integration.py \
  private/tmall-campus-5.8.0-base.apk --dex classes7.dex \
  --class-prefix 'Lcom/tmall/campus/user/UserCenter' \
  --class-prefix 'Lcom/tmall/campus/bizwebview/openapi/' \
  > private/integration-selected-recheck.json
```

`--dex` 限制扫描范围，用于已知类位置之后的复核；限制范围的结果不能代表整包。调用列表按目标去重排序，不保留指令顺序，不能单靠它证明控制流与检查条件。本文关于先后顺序的判断另结合所选静态指令/前端源文件，仍不是运行时验证。

本机证据包括 `private/integration-structure-5.8.0.json`、`private/integration-login-calls-5.8.0.json`、`private/integration-indirect-calls-5.8.0.json`。报告保留在私有目录，不进入源码包。

前端文件均为原页面直接引用的固定版本 `https://g.alicdn.com/tmall-xiaoyuan/page-m-webview/1.1.390/js/` 下的静态资源，普通 GET、无 Cookie、不执行 JavaScript；23 个 JS 合计 5,032,698 字节。文件名、大小及 SHA256 保存于私有 `frontend-static-inventory.json`，前端原文件不进入源码包。

## 验证与恢复

已对真实本机 APK 完成整包结构扫描与两轮所选类检查，JSON 均可解析；工具语法检查与源码导出检查另执行。未重新构建助手，因为没有修改运行代码。手机未发生变化，无手机恢复步骤。新增工具和文档可独立移除，不影响现有助手；不自动发布。

复习小任务：用自己的话区分“网页地址”“正常授权数据”“蓝牙通信”三者的作用。无需再操作真实门锁。
