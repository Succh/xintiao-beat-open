# 情侣手环实时互动 App：调研报告（小米手环9 Pro + Redmi Watch）

调研日期：2026-10-29。只做了调研，还没写代码。

## 0. 结论速览
- **可以做。** 手环9 Pro 和 Redmi Watch 5/6 都跑 Xiaomi Vela 系统，都能装 JS 快应用（.rpk）。同一套代码稍微适配屏幕就能两边用。
- **手环本身不能上网**（手环9 Pro 不支持 `system.fetch`），必须经手机中转：**手环 ⇄ 蓝牙 ⇄ 手机 ⇄ 云端 ⇄ 对方手机 ⇄ 蓝牙 ⇄ 对方手环**。
- **最大的限制：手环 App 退到后台就停止运行，后台也不能调用振动。** 所以 App 没开着时，要靠手机给手环推一条**系统通知**（会振动），App 开着时才能做到“对讲机式”的实时互动。
- **abox.run = AstroBox**，一个第三方穿戴设备工具箱，可以侧载快应用，有插件系统。它的插件可以在手机上当“桥”（和快应用互发消息、发 HTTP 请求、给手环推通知），还有 iOS 版，可能是 iPhone 方案的一条路。

---

## 1. https://abox.run/ 是什么
- 这是 **AstroBox** 的官网和文档站。它自称“由 Rust 驱动的跨平台穿戴设备第三方工具箱”（开发方 AstralSight Studios，开源仓库 https://github.com/AstralSightStudios/AstroBox-NG ）。来源：https://abox.run/
- 它不是小米官方产品，也不是托管服务，属于**社区工具 + 插件平台 + 资源分发平台**。主要有三块：
  1. **使用教程**：在 Windows/macOS/Linux/**iOS**/Android 上连接手环，安装表盘、快应用、固件。
  2. **插件开发**：插件基于 WIT + WASI，可以用 Rust、Go、Python、JS 写（API Level 4）。
  3. **创作者工具 CreatorConsole**：上传和发布表盘、快应用，也能接外部平台做付费售卖。
- **支持哪些设备**（来源：https://abox.run/docs/usage/connection ）：
  - 完整支持：小米手环 9 / 9 Pro 及以后的型号、REDMI Watch 5 及以后的型号、小米 Watch S3 及以后的型号。
  - 不支持：REDMI Watch 4 及更老的型号、手环 8 及更老的型号、REDMI 手环 / Active 系列。
  - iOS 上给 REDMI Watch 5 装快应用前，要先做“安卓伪装”。
- **对本项目有什么用**：
  - **开发时侧载**：AIoT IDE 打出的 .rpk 可以直接用 AstroBox 装到手环上，不用走官方商店。
  - **发布给别人**：可以通过 AstroBox 和米坛（BandBBS）资源区分发，有现成的用户群。
  - **手机端桥接（重点）**：插件有几组相关接口：
    - Interconnect 接口：`interconnect::send-qaic-message(addr, pkg, data)` 给手环上的快应用发字符串，`register-interconnect-recv` 接收快应用发来的消息。https://abox.run/docs/plugin-development/v2/host-api/interconnect 、https://abox.run/docs/plugin-development/v2/host-api/register
    - ThirdpartyApp 接口：`launch-qa` 可以远程打开手环上的某个快应用页面。https://abox.run/docs/plugin-development/v2/host-api/thirdpartyapp
    - Level 4 新增：给设备发送、更新、撤回通知和“超级岛实时活动”，还能起本地 HTTP 服务器。https://abox.run/docs/plugin-development/v4/whats-new
    - 已有一个官方“网桥 FetchBridge”插件，让快应用借道 AstroBox 访问网络，协议支持分片、ACK、流式传输。https://github.com/AstralSightStudios/AstroBox-NG-Plugin-MiWear-InterconnectFetch/blob/master/PROTOCOL.md
  - **注意**：同一时间手环通常只能连一个 App，AstroBox 和“小米运动健康”会抢蓝牙连接（https://github.com/hefeishui0829/xiaomi-vela-docs/blob/master/04-community/xiaomi-band-dev-guide/docs/05-sideload-and-deployment.md ）。所以走 AstroBox 桥接路线，等于让 AstroBox 替代小米运动健康作为手环的连接 App。

## 2. 手环9 Pro 的系统与第三方 App 能力
- **系统**：Xiaomi Vela（基于 NuttX），带 JS 快应用引擎。用 HTML/CSS/JS 写 `.ux` 页面，打包成 `.rpk`。https://www.luming.cool/posts/2025/08/build-a-quick-app-for-vela-devices/
- **官方开发文档**：Xiaomi Vela JS 应用 https://iot.mi.com/vela/quickapp/zh/ 。官方 IDE 是 **AIoT IDE**（基于 VS Code，自带手环/手表模拟器）。
- **安装方式**：
  - 官方商店：目前**不能自助上架**，要联系小米合作发布。https://iot.mi.com/vela/quickapp/zh/guide/publish/
  - 侧载：AstroBox，或米坛社区的资源（https://www.bandbbs.cn/resources/categories/100/ ）。
- **关键接口**：
  - **设备通信 `system.interconnect`**：和**同包名、同签名**的手机 Android App 双向收发 JSON。有 `send`、`onmessage`、`onopen`、`onclose`、`diagnosis`，连接自动建立。快应用要用 Android App 的 jks 证书来签名。https://iot.mi.com/vela/quickapp/zh/features/network/interconnect.html
  - **手机端 SDK**：“小米穿戴第三方APP能力开放”SDK（也叫小米运动健康通信 SDK），只有 Android 版，要经“小米运动健康”App 中转。它能做的事：
    - 查询连接、电量、佩戴、睡眠状态
    - `MessageApi.sendMessage` 收发消息
    - `NodeApi.launchWearApp` 打开手环上的 App
    - `NotifyApi.sendNotify(title, message)` 给手环推系统通知，手环 App 不需要在运行
    - 文档整理：https://www.aristore.top/posts/mi_band_app_dev/ ；社区 Demo：https://github.com/A5245/WatchSDK ；隐私声明：https://watch.iot.mi.com/html/privacy_sdk/index.html
  - **振动 `system.vibrator`**：手环9 Pro 只支持 `vibrate({mode:'short'|'long'})`，**不支持**自定义节奏的 `start/stop`（只有 Watch S5 支持）。https://iot.mi.com/vela/quickapp/zh/features/system/vibrator.html
  - **网络 `system.fetch`**：手环9/9 Pro、Redmi Watch 4 **不支持**；Redmi Watch 5/6 支持。https://iot.mi.com/vela/quickapp/zh/features/network/fetch.html
  - **后台运行**：App 退到后台就会被停掉，只有 `system.audio`、`system.request`、`system.geolocation` 可以申请后台运行。后台**禁止振动**。https://iot.mi.com/vela/quickapp/zh/guide/framework/other/background-running.html
  - **传感器**：社区里有用加速度传感器做的应用（例如“醒神”），心率等接口请以官方文档“接口”目录为准：https://iot.mi.com/vela/quickapp/zh/features/
- **官方现成的情侣功能**：小米运动健康的“我的亲友”可以互看睡眠、步数、活动，但不能在手环上互动。https://finance.sina.com.cn/tech/discovery/2024-08-09/doc-inchzyah2426870.shtml

## 3. Redmi Watch 能不能用同一个 App
- REDMI Watch 4/5/6 都是 Vela 系统，带快应用引擎。https://www.luming.cool/posts/2025/08/build-a-quick-app-for-vela-devices/
- **一套代码 + 按屏幕适配多份布局**就能跑。社区已有同一个应用同时适配 8P/9P/10P/9/10/11/表4/表5/表6 的例子（BandBBS 9 Pro 资源区）。
- 差别：
  - Redmi Watch 5/6 能直接 `fetch` 上网（如果手表连着手机，网络大概也是经手机走的），手环9 Pro 不能。
  - **Redmi Watch 4 不被 AstroBox 支持**，侧载要另想办法。所以对方最好是 Redmi Watch 5 或更新的型号。
- 分辨率：Redmi Watch 4 是 390×450，Redmi Watch 5 是 432×514，圆角约 90px。

## 4. 推荐架构
```
[手环9 Pro 快应用]
   ⇅ interconnect（蓝牙）
[我的 Android 伴侣 App：前台服务 + WebSocket]
   ⇅
[云端：WebSocket 中转 + 配对 + 离线消息 + 推送]
   ⇅
[TA 的 Android 伴侣 App]
   ⇅ interconnect / sendNotify
[TA 的 Redmi Watch 快应用]
```
- **方案 A（官方，推荐）**：自己写一个 Android 伴侣 App，集成小米穿戴 SDK，经“小米运动健康”和手环通信。包名和签名要和快应用一致。用户照常用小米运动健康，不影响同步。
- **方案 B（AstroBox 插件）**：手机端不写原生 App，写一个 AstroBox 插件，负责桥接、联网、推通知。好处是开发轻、能覆盖 iOS；代价是用户得用 AstroBox 代替小米运动健康连接手环，插件的后台常驻能力还需要实测。
- **两种收消息的场景**：
  - 手环 App 开着：`onmessage` 收到后，界面动画 + `vibrate('long')`，体验接近“对讲机”。
  - 手环 App 没开着：手机调用 `sendNotify`（AstroBox 走 Level 4 的通知接口），手环弹系统通知并振动（例如“💗 TA 在想你”）。也可以试试 `launchWearApp`，直接拉起手环 App（会不会打断用户、是否被允许，需要实测）。
- **延迟（估计，需实测）**：蓝牙段约几百毫秒，WebSocket 段约 100–300ms，两边 App 都在线时端到端大约 0.5–2 秒。走通知通道会更慢，也更不稳定。
- **限制和风险**：
  - Android 端要做后台保活：前台服务常驻通知、关掉电池优化、打开 HyperOS 自启动。对方手机被杀后台时，要用厂商推送 / FCM 唤醒作为兜底。
  - 单条 interconnect 消息大小有限，所以只传短 JSON，不传语音。手环9 Pro 应该没有麦克风接口，真正的“语音对讲”大概率做不了，“对讲机”指的是即时的心情、振动、短语。
  - 后台不能振动，接收端 App 不开时只能靠系统通知。
  - 手机端 App 是否必须一直运行才能收到手环的消息，需要实测。
- **现有类似应用**：没有找到成熟的手环端“情侣心情实时同步”应用。米坛上有“RE真心话大冒险”（多人互动）、“遥控手机”（手环发指令到手机）、多款 AI 聊天应用（借蓝牙桥联网），说明技术链路社区已经跑通。

## 5. 手环9 Pro 屏幕（UI 设计用）
- 1.74 英寸 AMOLED，**336×480**（竖屏圆角矩形，圆角约 48px），336 PPI，60Hz，最高亮度 1200nit。https://www.mi.com/global/product/xiaomi-smart-band-9-pro/specs/
- 设计建议：
  - 纯黑背景（AMOLED 省电），大号点击目标（≥ 80px）。
  - 每个组件都写 `position:absolute`，用 `background-color`，不要用渐变。
  - 设计工具社区常用“即时设计”，有现成的“无源流沙”UI 套件。
- 其他设备：Redmi Watch 5 是 432×514（圆角约 90），手环9 是 192×490（胶囊形）。

## 6. 对方用 iPhone 行不行
- 官方手机端通信 SDK **只有 Android 版**，iOS 上原生方案（方案 A）**做不了**。小米运动健康 iOS 版历来对第三方应用支持有限。
- 可能的出路是方案 B：AstroBox 有 iOS 版（https://apps.apple.com/cn/app/astrobox-%E6%AC%A1%E4%B8%96%E4%BB%A3%E7%A9%BF%E6%88%B4%E8%AE%BE%E5%A4%87%E5%B7%A5%E5%85%B7%E7%AE%B1/id6785374105 ），插件可以和快应用通信、联网。但 iOS 后台蓝牙和联网限制很严，能不能常驻接收消息**要实测**。另外 Redmi Watch 5 在 iOS 上还要先做“安卓伪装”。
- 结论：**两人都用 Android 最稳**；iPhone 只能作为实验性目标。

## 7. MVP 功能清单
1. **配对**：手机 App 生成 6 位配对码 / 二维码，两人绑定成一对。
2. **预设心情**：8–12 个图标（开心、想你、累、生气、晚安……），点一下就发出，对方手环振动并出现动画卡片。
3. **“想你了” 一键振动**：单击是短振，长按是长振。可以用连发几次 `vibrate` 模拟几种节奏（例如“戳三下”）。
4. **短状态**：预设短语 + 手机端自定义短语，同步到手环上选择发送（手环上不方便打字）。
5. **对方状态卡片**：对方最近一次的心情、时间、在线 / 佩戴状态（手机 SDK 可以查佩戴和睡眠状态）。
6. **兜底**：手环 App 没开时，用系统通知送达；对方离线时，云端存着，上线后补发。
7. 之后可加：手机端心情历史时间线、纪念日倒数、睡眠时自动免打扰。

## 8. 技术栈
- **手环 / 手表端**：Vela JS 快应用（.ux），AIoT IDE + 模拟器；接口用 `system.interconnect`、`system.vibrator`、`system.storage`、`system.router`。
- **手机端**：Kotlin Android App + 小米穿戴 SDK + OkHttp WebSocket + 前台服务。（方案 B 改成 AstroBox 插件，用 Rust/JS/Python 写。）
- **云端**：Node.js（Fastify / ws）或 Go；Redis 存在线状态和离线队列；推送用 FCM / 小米推送。也可以用 Supabase Realtime、Firebase、LeanCloud 等托管服务快速起步。
- **设计**：即时设计 / Figma，336×480 画布，圆角 48。

## 9. 大致开发步骤
1. 装 AIoT IDE，跑通 Hello World，用 AstroBox 侧载到手环9 Pro。
2. 生成一份 Android 签名 jks，转成 pem 给快应用签名，统一包名。跑通官方 interconnect demo，实现手环 ⇄ 手机互发 JSON。
3. 手机端接入小米穿戴 SDK（权限申请、`sendMessage`、`sendNotify`、`launchWearApp`）。
4. 搭建 WebSocket 中转服务，做配对和离线队列。
5. 两台手机 + 两块手环端到端联调，测延迟和后台存活。
6. 做 UI 设计稿，落地手环端页面，再适配 Redmi Watch 5 的布局。
7. （可选）做 AstroBox 插件版，验证 iOS。
8. 发布：先通过 AstroBox 和米坛分发；要上官方商店需联系小米合作。

## 10. 需要老板确认或提供的
1. 两个人的手机分别是什么系统、什么品牌？都是 Android 最好，小米 / 红米更好。
2. 对方手表的具体型号：Redmi Watch 4、5 还是 6？Redmi Watch 4 不被 AstroBox 支持。
3. 只自己两人用，还是要公开发布？这决定服务器规模、隐私合规、要不要上官方商店。
4. 能接受装一个自研 Android 伴侣 App（方案 A），还是希望只用 AstroBox（方案 B）？
5. 有没有电脑（Windows/macOS）用来跑 AIoT IDE？有没有服务器 / 域名预算？
6. 设计偏好：配色、风格参考、App 名字、心情图标的数量和种类。
7. “对讲机”指的是即时的心情、振动、短语，还是希望发语音？语音在手环9 Pro 上大概率做不了。

---

## 11. 追加调研（2026-10-29，新加坡时间 UTC+8）：Redmi Watch 3 兼容 + 无电脑工具链

已确认的前提：双方都用 Android（老板：小米 17 Pro）。老板没有电脑，所以所有开发都在云端 Linux 机器上做。对方的手表是 **Redmi Watch 3**，以后可能换成 Watch 5/6。

### 11.1 Redmi Watch 3 / 3 Active 能做什么
| 项目 | Redmi Watch 3 | Redmi Watch 3 Active |
|---|---|---|
| 系统 | 小米定制 RTOS，**不是 Vela** [1] | 同样是 RTOS，不是 Vela |
| 能否装第三方应用 / 快应用 (.rpk) | **不能**。没有应用商店，也装不了小程序 [2][3] | 不能 |
| AstroBox 能否侧载 | **不能**。官方支持表写明 “REDMI Watch 4 及更老：不支持”，“REDMI 手环 / Active 系列：不支持（协议版本不支持）” [4] | 不能 |
| 手机通知镜像 + 振动 | **能**。在小米运动健康 / Mi Fitness 里打开“消息通知”，按 App 勾选。手表收到通知会振动，可设置自动亮屏 [5][6] | 能，路径相同 [6] |
| 快捷回复 / 通知按钮 | 原生 **不支持** 回复 [3]。第三方 “Notify for Mi Band” 借用手表的“音乐控制”按键，做了一种变通的快捷回复（只适用于有音乐控制功能的表）[7] | 原生不支持回复 [8] |

区分两款的方法：Watch 3 是 1.75" AMOLED，带 GPS（2023 年发布）；3 Active 是更便宜的 1.83" LCD 款 [9]。两款结论一样：都装不了我们的应用。

**结论：** 对方的 Redmi Watch 3 跑不了我们的手表端应用，只能走“通知镜像”降级方案。

### 11.2 Redmi Watch 3 的降级方案（兼容 5/6 的升级路径）
- **收（能做到）**：对方手机上装我们的伴侣 Android App。老板在手环上点一下“想你”，消息经过服务器推送到对方手机，App 发一条高优先级的 Android 通知，例如 “💗 老板：想你了”，并带 emoji 心情。Mi Fitness 把这条通知镜像到 Redmi Watch 3，手表振动并显示文字。对方需要在 Mi Fitness 的通知列表里勾选我们的 App。
  - 振动节奏改不了，是手表系统决定的；文字和 emoji 可以自己定。表上最多保留 10 条通知 [6]。
- **回（能力有限）**：表上不能回复，对方要在手机 App 或手机通知的快捷回复按钮里回。
  - **一个待验证的办法**：伴侣 App 注册一个 Android MediaSession，把手表的“音乐控制”按键（播放/暂停、上一首、下一首）映射成预设回复，例如“下一首”= 发送 “❤️ 收到”。原理和 Notify 的变通做法相同 [7]，但要先在真表上测试。
- **升级路径**：伴侣 App 先检测对方的设备型号，按能力选择模式：
  - Watch 5/6 或 Band 9 及以后：能力检测通过，装完整的 Vela 快应用，用 interconnect 实时双向通信（AstroBox 支持表明确写了 Watch 5 及以后的系列 [4]）。
  - Watch 3/4：自动退回“通知模式”。
  - 双方共用同一套服务器和协议，换表时不用改服务器。

### 11.3 能否完全在 Linux 上开发、只用手机安装 —— 可以（已在本机实测一部分）
- **快应用 .rpk 构建（已实测成功）**：
  - 本机环境：Node v24.22.1。用 `npm i -g aiot-toolkit create-aiot` 安装工具 [10]。
  - 用 `create-aiot ux --name demo --template vela-demo` 建项目，再用 `npx aiot build` 构建，几秒内生成 `dist/com.application.watch.demo.debug.1.0.0.rpk`。
  - 构建结束时工具会提示“建议使用 AIoT-IDE”，但不影响产出 [10][11]。
  - AIoT IDE 也有 Linux 版，但老板没有电脑，命令行已经够用。
- **签名（已实测成功）**：
  - 先生成证书：`openssl req -newkey rsa:2048 -nodes -keyout sign/private.pem -x509 -days 3650 -out sign/certificate.pem`。
  - 再运行 `npx aiot release`，生成 release 包 `….release.1.0.0.rpk`（13 KB）[11][12]。
  - **要走 interconnect，快应用和 Android App 必须用同一个签名、同一个包名**：先用 keytool 生成 Android 的 jks，从中导出 pem 给快应用用，放在 `sign/release`。这一步还没实测。
- **模拟器（可选）**：Linux 上的 velasim 需要 i386 依赖库，而且需要图形界面。这个可以跳过，直接上真机测试。
- **Android APK（尚未实测）**：
  - 本机目前没有 Java/Gradle。需要装 JDK 17、Android cmdline-tools（`sdkmanager`）和 Gradle，然后 `./gradlew assembleRelease`。这是标准做法，Linux 完全支持。
  - 本机配置：内存 15 GB（当时空闲约 3 GB），磁盘空闲 121 GB，够用，但 Gradle 要限制堆内存。
- **交付到手机**：在本机生成 APK 和 .rpk 后发给老板，比如上传到 Google Drive 或发下载链接。手机下载后：
  - **APK**：直接点击安装，需要允许“安装未知来源应用”。
  - **.rpk**：用 **AstroBox Android 版**（V2.2.0，要求 Android 10 以上，建议 15 以上）[13] 安装。连上手环后，在“设备页 → 快应用卡片 → 右上角 +”选择本地 .rpk，或在“队列 → +”里添加。官方写明支持“完全离线、从本地安装”[14]。整个过程不需要电脑。
  - **注意**：AstroBox 连接手环要在手环上点“连接新手机”，会和 Mi Fitness 抢蓝牙连接 [4]。而我们的 Android App 走 Xiaomi Wearable SDK 时依赖 Mi Fitness 的连接。所以实际流程是：用 AstroBox 装好 .rpk 后，切回 Mi Fitness 并保持连接。切回后已装的快应用会不会保留、interconnect 能不能用，**需要真机验证**，这是当前最大的不确定点。

### 11.4 来源
1. https://www.mi.com/global/support/faq/details/KA-229113/ （Redmi Watch 3 使用定制 RTOS）
2. https://www.bandbbs.cn/threads/7396/ （无法安装小程序）
3. https://www.calcalistech.com/ctechnews/article/sjbmjtipn （没有应用商店，不能回复消息）
4. https://abox.run/docs/usage/connection （AstroBox 设备支持表、连接流程）
5. https://www.mi.com/tw/support/faq/details/KA-19426/ （配合小米运动健康，支持消息通知）
6. https://www.mi.com/vn/support/faq/details/KA-38977/ （3 Active FAQ：通知镜像路径、振动、亮屏、最多 10 条）
7. https://www.mibandnotify.com/tutorial/redmi-watch-3/button/quick-reply-messages--2028.html ；https://play.google.com/store/apps/details?id=com.mc.xiaomi1
8. https://androidphoria.com/tutoriales/como-poner-whatsapp-en-redmi-watch-3-active （3 Active 不能回复消息）
9. https://www.mi.com/global/product/redmi-watch-3 ；https://www.mi.com/global/product/redmi-watch-3-active/
10. https://www.npmjs.com/package/aiot-toolkit
11. https://iot.mi.com/vela/quickapp/zh/tools/toolkit/start.html ；https://iot.mi.com/vela/quickapp/en/content/tutorial/toolkit.html
12. https://iot.mi.com/vela/quickapp/zh/guide/start/toolkit-params.html
13. https://abox.run/docs/usage/install
14. https://abox.run/docs/usage/resource
