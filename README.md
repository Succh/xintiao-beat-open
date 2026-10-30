# 心跳 Beat

情侣之间的「想你了」——手机 App + 小米手环/手表快应用 + 中继服务器。

当前版本：**0.1.3**（手机 `versionCode` 4，手环 `versionCode` 3）。

> **公开仓库说明**：本仓库是脱敏公开版，**不含**预配置安装包（`out/`）、签名密钥（`keys/`）、真实服务器地址或部署账号信息。请自行部署中继，并用 `BEAT_SERVER=…` 构建手机 App（或在 App 设置里填写 `wss://…/ws`）。

## 这是什么

两个人各自装上手机 App（可选再装手环 App）后配对，就能互相：

- 发「想你了」（短振 / 长振）
- **戳三下**
- 改心情、发短语
- 回「收到」

消息经中继服务器实时送达；对方离线时会排队，上线后补发。

## 架构

```
手环快应用  ←── interconnect (同包名同签名 JSON) ──→  手机 App  ←── WebSocket /ws ──→  中继服务器
 (band/)                                              (phone/)                        (server/)
```

| 端 | 说明 |
|---|---|
| **phone/** | Android 伴侣 App（`com.xintiao.beat`）。连中继、管配对/历史/通知，并通过小米 Mi Wear SDK 与手环互通。 |
| **band/** | 小米手环 9 Pro / Redmi Watch 5·6 等可装第三方快应用的表端 UI（Vela JS，336×480）。 |
| **server/** | Node.js WebSocket 中继：配对码、事件转发、离线队列、伙伴在线状态。 |

协议细节见 [`PROTOCOL.md`](./PROTOCOL.md)。

**产品定位**：手机 App 是主端（更多内容与特效）；手环/表是轻量分支（关键操作 + 简洁展示）。

**提醒方式**

- **完整手表 App（full）**：对方消息会拉起手环「心跳」，界面弹出并可振动。
- **通知模式（notify）**：走系统通知，由小米运动健康镜像到不支持快应用的表（如 Redmi Watch 3）。

## 免打扰（夜间 23:30–07:30）

默认开启，可在手机「设置 → 睡眠时免打扰」关闭。

| 端 | 行为 |
|---|---|
| **手机** | 低优先级**静默**通知（不打扰） |
| **手环（full）** | **仍弹出/显示**消息，但带 `silent:true`，**不振动** |
| **ack「收到」** | 同样：手环可显示、不振 |

## 配对流程（简述）

1. 两人手机都装 `beat-0.1.3.apk`，允许通知，并按 [`INSTALL.md`](./INSTALL.md) 做好后台保活。
2. 打开「心跳」，设昵称；请自建中继，构建时用 `BEAT_SERVER` 烘焙地址，或在 App「设置 → 服务器」填写。
3. 一方点出 6 位配对码发给对方；另一方输入后点「开始配对」。
4. 能装快应用的手环再按 INSTALL 用 AstroBox 装 `.rpk`，装完切回小米运动健康，并在 App 里「去授权」。

完整图文步骤（含 Watch 3 通知设置、常见问题）：**[`INSTALL.md`](./INSTALL.md)**。

## 服务器

部署完成后两端填同一个地址，例如：

```text
wss://beat.example.com/ws
```

公开库不附带预配置 apk。自建请看 [`server/DEPLOY.md`](./server/DEPLOY.md)。  
断线期间发出的消息会进手机 **outbox 持久化队列**，连上后按 `cid` 补发。

> 仓库**不包含**签名私钥、keystore、API key。`keys/` 仅本地构建使用，已被 `.gitignore` 排除。

## 构建安装包

公开库**不附带**已烘焙真实地址的 apk/rpk。本地构建：

```bash
# 1. 准备签名材料到 keys/（勿提交）
# 2. 手机
BEAT_SERVER='wss://beat.example.com/ws' ./phone/build.sh
# 3. 手环
cd band && npm i && npm run build && npm run release
```

单测：`cd server && npm test` · `./phone/test.sh` · `cd band && npm test`。

## 仓库结构

```text
band-couple-app/
├── README.md          # 本文件
├── INSTALL.md         # 给用户的安装说明
├── PROTOCOL.md        # 手机⇄手环、手机⇄中继协议
├── phone/             # Android 伴侣 App
├── band/              # 手环/手表快应用
├── server/            # WebSocket 中继
├── tools/             # 表情等资源脚本
├── mockups/           # 设计稿
├── out/               # 构建产出 apk / rpk（可本地生成）
└── keys/              # 签名材料（勿提交）
```

## 版本记录（摘要）

| 版本 | 说明 |
|---|---|
| **0.1.3** | 心情 14（miss_you/sweet/love…，旧 id 兼容）；手机粉紫特效 560ms+心形粒子；7 步小白「安装手环」引导（AstroBox→切回→试发）；UI 精致化；手环轻量短动效；Watch 3 禁选 full |
| 0.1.2 | 免打扰：手环 silent 仍显示；outbox 持久化 + `cid`；手环 interconnect `JSON.stringify`；首页戳三下；版本对齐 |
| 0.1.1 | 手机打包可写入 `BEAT_SERVER`；自建中继部署文档 |
| 0.1.0 | MVP |

## License / 说明

私人情侣项目，未上架应用商店；安装需允许「未知来源」。请勿把 `keys/`、token、私钥推到公开仓库。
