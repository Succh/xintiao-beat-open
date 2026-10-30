// Pure helpers (no Vela APIs) so they can be unit-tested with Node.
import { MOODS, MOOD_ALIASES, EMOJI_KEYS } from './data.js'

export function canonicalizeMood(id) {
  if (!id) return id || ''
  return MOOD_ALIASES[id] || id
}

export function moodLabel(id) {
  if (!id) return ''
  const c = canonicalizeMood(id)
  for (let i = 0; i < MOODS.length; i++) if (MOODS[i].id === c) return MOODS[i].label
  return '心情' // unknown id: degrade, do not crash
}

export function emojiSrc(key) {
  return '/common/emoji/' + (EMOJI_KEYS.indexOf(key) >= 0 ? key : 'chat') + '.png'
}

export function timeAgo(ts, now) {
  if (!ts) return ''
  const s = Math.max(0, Math.floor((now - ts) / 1000))
  if (s < 60) return '刚刚'
  const m = Math.floor(s / 60)
  if (m < 60) return m + ' 分钟前'
  const h = Math.floor(m / 60)
  if (h < 24) return h + ' 小时前'
  return Math.floor(h / 24) + ' 天前'
}

export function pad2(n) { return (n < 10 ? '0' : '') + n }

export function clock(ts) {
  const d = new Date(ts)
  return pad2(d.getHours()) + ':' + pad2(d.getMinutes())
}

export function countdown(expiresAt, now) {
  const s = Math.max(0, Math.floor((expiresAt - now) / 1000))
  return pad2(Math.floor(s / 60)) + ':' + pad2(s % 60)
}

export function codeDigits(code) {
  const s = String(code || '').replace(/\D/g, '')
  const out = []
  for (let i = 0; i < 6; i++) out.push(s.charAt(i) || '-')
  return out
}

// interconnect delivers {data: "<json string>"}; be tolerant of objects too
export function parseMessage(raw) {
  try {
    let d = raw && raw.data !== undefined ? raw.data : raw
    if (typeof d === 'string') d = JSON.parse(d)
    return d && typeof d === 'object' && d.t ? d : null
  } catch (e) {
    return null
  }
}

// Text for the incoming screen: { title: '想你了', meta: '刚刚 · 戳了你 3 下', icon }
export function describeIncoming(ev, now) {
  const ago = timeAgo(ev.ts || now, now)
  if (ev.kind === 'miss') {
    if (ev.level === 'poke3') return { title: '戳了你', meta: ago + ' · 戳了你 3 下', icon: 'poke' }
    return { title: '想你了', meta: ago + (ev.level === 'long' ? ' · 好想好想你' : ' · 想你了'), icon: 'miss' }
  }
  if (ev.kind === 'mood') {
    const mid = canonicalizeMood(ev.mood)
    return { title: moodLabel(mid) || '换了心情', meta: ago + ' · 心情' + (ev.text ? '：' + ev.text : ''), icon: mid }
  }
  if (ev.kind === 'phrase') {
    return { title: ev.text || '', meta: ago + ' · 发来短语', icon: ev.e || 'chat' }
  }
  if (ev.kind === 'ack') {
    return { title: '收到啦', meta: ago + ' · 回了「收到」', icon: 'ok' }
  }
  return { title: '找你', meta: ago, icon: 'heart' }
}

// Vibration plan for an incoming event: list of [delayMs, 'short'|'long']
// Empty when silent (quiet hours / ack) — UI still shows.
export function vibrationPlan(ev) {
  if (!ev || ev.silent) return []
  if (ev.kind === 'ack') return []
  if (ev.kind === 'miss' && ev.level === 'long') return [[0, 'long']]
  if (ev.kind === 'miss' && ev.level === 'poke3') return [[0, 'short'], [450, 'short'], [900, 'short']]
  if (ev.kind === 'miss') return [[0, 'long']]
  return [[0, 'short']]
}
