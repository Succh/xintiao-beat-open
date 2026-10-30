# 心跳 Beat – message protocol

## Band ⇄ phone (Xiaomi interconnect, JSON strings, same package `com.xintiao.beat`)
Band → phone
| t | fields | meaning |
|---|---|---|
| hello | – | band opened, please send `state` |
| miss | level: short \| long \| poke3 | 想你了 (tap = short, long-press = long) |
| mood | mood: happy…melt (14 ids) | set my mood |
| phrase | e, text | send a quick phrase |
| ack | – | 收到 on the incoming screen |
| pair_code | – | ask for a pairing code to show on the band |

Phone → band
| t | fields |
|---|---|
| state | server(bool), paired, me{name,mood}, partner{name,mood,text,online,wearing,updatedAt}, phrases[{e,text}] |
| code | code, expiresAt |
| incoming | kind (miss/mood/phrase/ack), level, mood, e, text, from, ts, silent? |
| sent | ok (delivered to partner's phone right now) |

## Phone ⇄ relay (WebSocket `/ws`, JSON `{op,...}`)
Client → server: `hello{uid?,token?,name,avatar,device,mode}`, `pair_code`, `pair_join{code}`, `event{event:{kind,level|mood,text|e,text,cid,via}}`, `status{wearing,device,mode,name,avatar}`, `set_partner_mode{mode}`, `history`, `unpair`, `ping`.

Server → client: `welcome{uid,token,me,paired,partner,pairedAt,history}`, `pair_code{code,expiresAt}`, `paired{partner}`, `pair_error{reason}`, `event{event}`, `sent{event,delivered}`, `delivered{ids}`, `partner_status{partner}`, `me`, `mode_changed{mode,by}`, `history{events}`, `unpaired`, `error{reason}`, `pong`.

Moods: happy 开心 · miss 想你 · tired 累了 · angry 生气 · aggrieved 委屈 · busy 忙碌 · hungry 饿了 · night 晚安 · shy 害羞 · jealous 吃醋 · cheer 加油 · love 喜欢 · clingy 黏你 · melt 融化.
Modes: `notify` (Android notification, mirrored to the watch by Mi Fitness) · `full` (band/watch app via Mi Wear SDK, falls back to notify).
