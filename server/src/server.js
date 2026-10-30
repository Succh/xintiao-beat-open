'use strict'
/**
 * 心跳 Beat relay.
 * One WebSocket per phone app. JSON messages {op: ..., ...}.
 * See ../PROTOCOL.md for the full message list.
 */
const http = require('http')
const crypto = require('crypto')
const { WebSocketServer } = require('ws')
const { MODES, LIMITS, str, cleanEvent } = require('./protocol')
const { Store } = require('./store')

function rid(bytes) { return crypto.randomBytes(bytes).toString('hex') }
function sixDigits() { return String(crypto.randomInt(0, 1000000)).padStart(6, '0') }
function safeEqual(a, b) {
  const x = Buffer.from(String(a)); const y = Buffer.from(String(b))
  return x.length === y.length && crypto.timingSafeEqual(x, y)
}

function createRelay(opts = {}) {
  const store = new Store(opts.dataFile)
  const now = opts.now || (() => Date.now())
  const pingMs = opts.pingMs || 30000
  const sockets = new Map() // uid -> Set<ws>
  const codes = new Map()   // code -> {uid, expiresAt}

  const server = http.createServer((req, res) => {
    if (req.url === '/health') {
      res.writeHead(200, { 'content-type': 'application/json' })
      res.end(JSON.stringify({ ok: true, online: sockets.size }))
      return
    }
    res.writeHead(200, { 'content-type': 'text/plain; charset=utf-8' })
    res.end('心跳 Beat relay. Connect with WebSocket at /ws\n')
  })
  const wss = new WebSocketServer({ server, path: '/ws', maxPayload: LIMITS.maxPayload })

  function send(ws, msg) {
    if (ws.readyState === 1) ws.send(JSON.stringify(msg))
  }
  function sendTo(uid, msg) {
    const set = sockets.get(uid)
    if (!set || !set.size) return false
    set.forEach(ws => send(ws, msg))
    return true
  }
  function online(uid) { const s = sockets.get(uid); return !!(s && s.size) }

  function publicView(u) {
    if (!u) return null
    return {
      uid: u.uid,
      name: u.name,
      avatar: u.avatar,
      device: u.device,
      mode: u.mode,
      mood: u.status.mood,
      text: u.status.text,
      wearing: u.status.wearing,
      updatedAt: u.status.updatedAt,
      online: online(u.uid)
    }
  }

  function notifyPartnerStatus(u) {
    if (u && u.partner) sendTo(u.partner, { op: 'partner_status', partner: publicView(u) })
  }

  function newUser(h) {
    const u = {
      uid: rid(8),
      token: rid(24),
      name: str(h.name, LIMITS.name) || '我',
      avatar: str(h.avatar, LIMITS.emojiKey) || 'happy',
      device: str(h.device, LIMITS.device),
      mode: MODES.indexOf(h.mode) >= 0 ? h.mode : 'notify',
      partner: null,
      pairedAt: 0,
      status: { mood: '', text: '', wearing: false, updatedAt: 0 },
      createdAt: now()
    }
    store.putUser(u)
    return u
  }

  function welcome(ws, u) {
    const p = u.partner ? store.user(u.partner) : null
    send(ws, {
      op: 'welcome',
      uid: u.uid,
      token: u.token,
      me: publicView(u),
      paired: !!p,
      pairedAt: u.pairedAt,
      partner: publicView(p),
      history: p ? store.history(u.uid, p.uid).slice(-LIMITS.historySend) : [],
      serverTime: now()
    })
    // flush anything that arrived while offline
    const q = store.drain(u.uid)
    if (q.length) {
      q.forEach(m => send(ws, m))
      const ids = q.filter(m => m.op === 'event').map(m => m.event.id)
      if (p && ids.length) sendTo(p.uid, { op: 'delivered', ids })
    }
  }

  function handle(ws, msg) {
    const op = msg && msg.op
    if (op === 'ping') return send(ws, { op: 'pong', t: now() })

    if (op === 'hello') {
      let u = null
      if (msg.uid && msg.token) {
        const ex = store.user(msg.uid)
        if (ex && safeEqual(ex.token, msg.token)) u = ex
        else return send(ws, { op: 'error', reason: 'bad_token' })
      } else {
        u = newUser(msg)
      }
      // profile updates sent with hello
      if (msg.name) u.name = str(msg.name, LIMITS.name) || u.name
      if (msg.avatar) u.avatar = str(msg.avatar, LIMITS.emojiKey) || u.avatar
      if (msg.device !== undefined) u.device = str(msg.device, LIMITS.device)
      if (MODES.indexOf(msg.mode) >= 0) u.mode = msg.mode
      store.putUser(u)
      if (ws.uid && ws.uid !== u.uid) detach(ws)
      ws.uid = u.uid
      if (!sockets.has(u.uid)) sockets.set(u.uid, new Set())
      sockets.get(u.uid).add(ws)
      welcome(ws, u)
      notifyPartnerStatus(u)
      return
    }

    const u = ws.uid ? store.user(ws.uid) : null
    if (!u) return send(ws, { op: 'error', reason: 'hello_first' })

    if (op === 'pair_code') {
      for (const [c, v] of codes) if (v.uid === u.uid || v.expiresAt < now()) codes.delete(c)
      let code
      do { code = sixDigits() } while (codes.has(code))
      const expiresAt = now() + LIMITS.codeTtlMs
      codes.set(code, { uid: u.uid, expiresAt })
      return send(ws, { op: 'pair_code', code, expiresAt })
    }

    if (op === 'pair_join') {
      const t = now()
      ws.joins = (ws.joins || []).filter(x => t - x < 60000)
      if (ws.joins.length >= LIMITS.joinPerMinute) return send(ws, { op: 'pair_error', reason: 'too_many_tries' })
      ws.joins.push(t)
      const code = String(msg.code || '').replace(/\D/g, '')
      const entry = codes.get(code)
      if (!entry || entry.expiresAt < t) return send(ws, { op: 'pair_error', reason: 'bad_code' })
      if (entry.uid === u.uid) return send(ws, { op: 'pair_error', reason: 'own_code' })
      const other = store.user(entry.uid)
      if (!other) return send(ws, { op: 'pair_error', reason: 'bad_code' })
      codes.delete(code)
      // break any previous pairs
      ;[u, other].forEach(x => {
        if (x.partner && x.partner !== u.uid && x.partner !== other.uid) {
          const old = store.user(x.partner)
          if (old) { old.partner = null; old.pairedAt = 0; store.putUser(old); sendTo(old.uid, { op: 'unpaired' }) }
        }
      })
      u.partner = other.uid; other.partner = u.uid
      u.pairedAt = other.pairedAt = t
      store.putUser(u); store.putUser(other)
      sendTo(u.uid, { op: 'paired', partner: publicView(other), pairedAt: t })
      sendTo(other.uid, { op: 'paired', partner: publicView(u), pairedAt: t })
      return
    }

    if (op === 'unpair') {
      const p = u.partner ? store.user(u.partner) : null
      u.partner = null; u.pairedAt = 0; store.putUser(u)
      if (p) { p.partner = null; p.pairedAt = 0; store.putUser(p); store.clearHistory(u.uid, p.uid); sendTo(p.uid, { op: 'unpaired' }) }
      return sendTo(u.uid, { op: 'unpaired' })
    }

    if (op === 'status') {
      if (msg.wearing !== undefined) u.status.wearing = !!msg.wearing
      if (msg.device !== undefined) u.device = str(msg.device, LIMITS.device)
      if (MODES.indexOf(msg.mode) >= 0) u.mode = msg.mode
      if (msg.name) u.name = str(msg.name, LIMITS.name) || u.name
      if (msg.avatar) u.avatar = str(msg.avatar, LIMITS.emojiKey) || u.avatar
      store.putUser(u)
      notifyPartnerStatus(u)
      return send(ws, { op: 'me', me: publicView(u) })
    }

    if (op === 'set_partner_mode') {
      const p = u.partner ? store.user(u.partner) : null
      if (!p || MODES.indexOf(msg.mode) < 0) return send(ws, { op: 'error', reason: 'bad_request' })
      p.mode = msg.mode
      store.putUser(p)
      if (!sendTo(p.uid, { op: 'mode_changed', mode: msg.mode, by: u.name })) {
        store.enqueue(p.uid, { op: 'mode_changed', mode: msg.mode, by: u.name }, LIMITS.queueKeep)
      }
      return send(ws, { op: 'partner_status', partner: publicView(p) })
    }

    if (op === 'history') {
      const p = u.partner ? store.user(u.partner) : null
      return send(ws, { op: 'history', events: p ? store.history(u.uid, p.uid).slice(-LIMITS.historySend) : [] })
    }

    if (op === 'event') {
      const p = u.partner ? store.user(u.partner) : null
      if (!p) return send(ws, { op: 'error', reason: 'not_paired' })
      const ev = cleanEvent(msg.event)
      if (!ev) return send(ws, { op: 'error', reason: 'bad_event' })
      ev.id = rid(6)
      ev.ts = now()
      ev.from = u.uid
      ev.fromName = u.name
      if (ev.kind === 'mood') {
        u.status.mood = ev.mood
        u.status.text = ev.text
        u.status.updatedAt = ev.ts
        store.putUser(u)
      }
      store.addHistory(u.uid, p.uid, ev, LIMITS.historyKeep)
      const out = { op: 'event', event: ev }
      const delivered = sendTo(p.uid, out)
      if (!delivered) store.enqueue(p.uid, out, LIMITS.queueKeep)
      // echo to all of the sender's sockets so their timeline updates
      sendTo(u.uid, { op: 'sent', event: ev, delivered })
      if (ev.kind === 'mood') notifyPartnerStatus(u)
      return
    }

    send(ws, { op: 'error', reason: 'unknown_op' })
  }

  function detach(ws) {
    const uid = ws.uid
    if (!uid) return
    const set = sockets.get(uid)
    if (set) {
      set.delete(ws)
      if (!set.size) {
        sockets.delete(uid)
        notifyPartnerStatus(store.user(uid))
      }
    }
  }

  wss.on('connection', ws => {
    ws.alive = true
    ws.on('pong', () => { ws.alive = true })
    ws.on('message', data => {
      let msg
      try { msg = JSON.parse(data.toString()) } catch (e) { return send(ws, { op: 'error', reason: 'bad_json' }) }
      try { handle(ws, msg) } catch (e) {
        console.error('handler error', e)
        send(ws, { op: 'error', reason: 'server_error' })
      }
    })
    ws.on('close', () => detach(ws))
    ws.on('error', () => {})
  })

  const pinger = setInterval(() => {
    wss.clients.forEach(ws => {
      if (!ws.alive) return ws.terminate()
      ws.alive = false
      try { ws.ping() } catch (e) {}
    })
  }, pingMs)

  return {
    server,
    store,
    listen(port, host) {
      return new Promise(resolve => server.listen(port, host, () => resolve(server.address().port)))
    },
    close() {
      clearInterval(pinger)
      wss.clients.forEach(ws => ws.terminate())
      store.flush()
      return new Promise(resolve => wss.close(() => server.close(() => resolve())))
    }
  }
}

module.exports = { createRelay }
