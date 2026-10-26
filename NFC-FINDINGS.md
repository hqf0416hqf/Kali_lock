# NFC 与蓝牙路线补充调查

用户说明：应用里点击按钮可以开门，但需要开启蓝牙和定位；用户提出同一门禁可能同时支持 NFC 与蓝牙。本篇先记录 NFC 静态检查；随后进行的原应用正常开门观察见 LIVE-OBSERVATION.md，未开展 NFC 刷卡测试。

## 当前证据

- ADB 功能检查确认本机支持 NFC、HCE 与 BLE；这些硬件能力不能证明当前门禁支持相同模式。
- 天猫校园 5.8.0 同时声明 NFC 与蓝牙权限，不能仅由权限推断当前操作的通信方式。
- 5.8.0 清单中 NfcDispatchActivity 接收 NDEF_DISCOVERED；未发现声明的 HCE/卡模拟服务。这不排除手机钱包或安全元件提供独立卡模拟功能。
- 静态读取 NfcDispatchActivity：它接收 NFC 标签记录并解析内容；存在门锁业务分支，交给 NfcDispatchViewModel.parseCard 处理。没有读取任何真实 NFC 标签、门锁标识或凭证，没有构造 NFC 事件或调用该分支。
- 同一组件有链接跳转处理；部分路径在冷启动时仍经过启动页。不能据此保证 NFC 会消除广告等待。

## 合理推断与未知

手机读取门旁 NFC 标签，可能只是选定或导航到门锁功能，之后仍通过蓝牙通信；与手机模拟门禁卡直接刷读卡器不同。当前代码证据支持存在 NFC 门锁业务处理，但未验证实际学校部署、门旁标签、是否直接开锁、是否仍依赖蓝牙或网络。

优先确认学校/应用现有的正式 NFC“碰一碰”流程；若它能正常满足目标，可能无需额外助手。只在用户门边明确配合时观察一次正常操作，不复制卡、不伪造标签、不重放开锁通信、不枚举周边设备。

定位开关可能与蓝牙发现所需条件有关，不能仅由此判断其用于地理围栏或实际上传位置。具体要求与系统、应用目标 SDK 和实现有关。

## 后续采集限制

原应用的 NFC 分支存在记录标签 payload 的日志语句，因此不得直接保存 NFCDispatch 的原始日志。后续仅保留必要的事件类别、相对时间及非标识性状态，剔除账号、令牌、地址、标签内容和门锁配置。通信事件不等于物理门响应。

参考：

- https://developer.android.com/develop/connectivity/nfc
- https://developer.android.com/develop/connectivity/nfc/hce
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
