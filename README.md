# 心跳 Beat

情侣之间的「想你了」——手机 App + 小米手环/手表快应用 + 中继服务器。

当前版本：**0.1.9**（手机 `versionCode` 10，手环 `0.1.9` / `versionCode` 8，两端同版）。

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

1. 两人手机都装 `beat-0.1.9.apk`，允许通知，并按 [`INSTALL.md`](./INSTALL.md) 做好后台保活。
2. 打开「心跳」，设昵称；服务器地址已打包进 0.1.9（见下），一般不用手填。
3. 一方点出 6 位配对码发给对方；另一方输入后点「开始配对」。
4. 能装快应用的手环再按 INSTALL 用 AstroBox 装 `.rpk`，装完切回小米运动健康，并在 App 里「去授权」。

完整图文步骤（含 Watch 3 通知设置、常见问题）：**[`INSTALL.md`](./INSTALL.md)**。

## 服务器

当前演示环境跑在 **instabox** 上，公开 WebSocket 地址：

```text
wss://YOUR-RELAY.example.com/ws
```

已写入 `beat-0.1.9.apk`。自建请看 [`server/DEPLOY.md`](./server/DEPLOY.md)。  
免费版 instabox 约每 8 小时会暂停一阵，App 会自动重连；断线期间发出的消息会进手机 **outbox 持久化队列**，连上后按 `cid` 补发。

> 仓库**不包含**签名私钥、keystore、API key。`keys/` 仅本地构建使用，已被 `.gitignore` 排除。

## 安装包（构建产物）

本地构建后放在 `out/`：

- `out/beat-0.1.9.apk` — 手机
- `out/com.xintiao.beat.debug.0.1.9.rpk` — 手环调试包（常用；本版手环仅同步版本号）
- `out/com.xintiao.beat.release.0.1.9.rpk` — 手环正式签名包（备用）

```bash
# 手机（保持线上 wss）
BEAT_SERVER='wss://YOUR-RELAY.example.com/ws' ./phone/build.sh

# 手环
cd band && npm i && npm run build && npm run release
# 注意 release 会清空 dist：每步之后各自把 .rpk 拷到 ../out/
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
| **0.1.9** | 发短语 / 对方发来短语时手机端播轻特效（中心跟短语表情走）；手环同步到 0.1.9 |
| 0.1.8 | 对方发来「想你了 / 戳三下」时手机端也播轻特效；首页今日统计把「想你了」和「戳」分开显示；手环同步到 0.1.8 |
| 0.1.7 | 心情特效跟心情表情走（中心+粒子）；点「想你了 / 戳三下」有轻特效；首页底部「今天互发了 N 次想你了」；手环同步到 0.1.7 |
| 0.1.6 | 心情长按 400ms 本机预览（松手不发）；首页底部「今天你们互换了 N 次心情」+ 今日列表（只读本地记录）；Watch 3 / notify 设备改为「通知镜像清单」3 步 + 本机试发 + 2 条排查，full 设备保持 AstroBox 引导；手环同步到 0.1.6 |
| 0.1.5 | 小白文案：配对页不再误导「先填服务器」；设置里说明演示包已内置地址；INSTALL 同步 |
| 0.1.4 | 想你了「轻点短振 · 长按更想」；发出「已送出/已存着」；notify 常驻提示；手环重试失败文案；Watch3 强制 notify；HyperOS 保活三句；引导「安装到你的表」+ AstroBox 搜不到提示 |
| 0.1.3 | 心情 14（miss_you/sweet/love…，旧 id 兼容）；手机粉紫特效 560ms+心形粒子；7 步小白「安装手环」引导（AstroBox→切回→试发）；UI 精致化；手环轻量短动效；Watch 3 禁选 full |
| 0.1.2 | 免打扰：手环 silent 仍显示；outbox 持久化 + `cid`；手环 interconnect `JSON.stringify`；首页戳三下；版本对齐 |
| 0.1.1 | 手机打包写入线上 wss；instabox 部署中继 |
| 0.1.0 | MVP |

## License / 说明

私人情侣项目，未上架应用商店；安装需允许「未知来源」。请勿把 `keys/`、token、私钥推到公开仓库。
