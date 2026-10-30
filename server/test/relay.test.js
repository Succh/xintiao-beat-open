'use strict'
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('fs')
const os = require('os')
const path = require('path')
const { createRelay } = require('../src/server')
const { cleanEvent } = require('../src/protocol')
const { Client } = require('./helpers')

async function setup(dataFile) {
  const relay = createRelay({ dataFile })
  const port = await relay.listen(0, '127.0.0.1')
  return { relay, url: `ws://127.0.0.1:${port}/ws` }
}

async function pairTwo(url) {
  const a = new Client(url); const b = new Client(url)
  await a.open(); await b.open()
  const wa = await a.req({ op: 'hello', name: '老板', device: 'Xiaomi Smart Band 9 Pro', mode: 'full' }, 'welcome')
  const wb = await b.req({ op: 'hello', name: '宝贝', device: 'Redmi Watch 3', mode: 'notify' }, 'welcome')
  const pc = await a.req({ op: 'pair_code' }, 'pair_code')
  assert.match(pc.code, /^\d{6}$/)
  b.send({ op: 'pair_join', code: pc.code })
  const pa = await a.wait('paired'); const pb = await b.wait('paired')
  assert.equal(pa.partner.name, '宝贝'); assert.equal(pb.partner.name, '老板')
  return { a, b, wa, wb }
}

test('cleanEvent validation', () => {
  assert.equal(cleanEvent({ kind: 'nope' }), null)
  assert.equal(cleanEvent({ kind: 'mood', mood: 'xx' }), null)
  assert.equal(cleanEvent({ kind: 'phrase', text: '   ' }), null)
  assert.deepEqual(cleanEvent({ kind: 'poke' }), { kind: 'miss', level: 'poke3', via: 'phone' })
  assert.equal(cleanEvent({ kind: 'mood', mood: 'miss' }).mood, 'miss_you')
  assert.equal(cleanEvent({ kind: 'mood', mood: 'miss_you' }).mood, 'miss_you')
  assert.equal(cleanEvent({ kind: 'phrase', text: 'x'.repeat(100) }).text.length, 40)
  assert.equal(cleanEvent({ kind: 'miss', level: 'weird', via: 'band' }).level, 'short')
})

test('pair, send miss/mood/phrase, history, status, unpair', async () => {
  const { relay, url } = await setup(null)
  const { a, b, wb } = await pairTwo(url)

  // miss from band via A -> B
  a.send({ op: 'event', event: { kind: 'miss', level: 'long', via: 'band', cid: 'c1' } })
  const evB = await b.wait('event')
  assert.equal(evB.event.kind, 'miss'); assert.equal(evB.event.level, 'long'); assert.equal(evB.event.fromName, '老板')
  const sent = await a.wait('sent')
  assert.equal(sent.delivered, true); assert.equal(sent.event.cid, 'c1')

  // mood from B updates B status at A
  b.send({ op: 'event', event: { kind: 'mood', mood: 'tired', text: '在上课，晚点聊' } })
  const evA = await a.wait('event')
  assert.equal(evA.event.mood, 'sleepy')
  const ps = await a.wait('partner_status', m => m.partner.mood === 'sleepy')
  assert.equal(ps.partner.text, '在上课，晚点聊'); assert.equal(ps.partner.online, true)

  b.send({ op: 'event', event: { kind: 'phrase', e: 'eyes', text: '在干嘛？' } })
  assert.equal((await a.wait('event')).event.text, '在干嘛？')

  const h = await a.req({ op: 'history' }, 'history')
  assert.equal(h.events.length, 3)

  // status (wearing) propagates
  b.send({ op: 'status', wearing: true })
  const ps2 = await a.wait('partner_status', m => m.partner.wearing === true)
  assert.equal(ps2.partner.device, 'Redmi Watch 3')

  // A switches B to full-watch mode remotely
  a.send({ op: 'set_partner_mode', mode: 'full' })
  assert.equal((await b.wait('mode_changed')).mode, 'full')

  // invalid event rejected
  a.send({ op: 'event', event: { kind: 'mood', mood: 'nope' } })
  assert.equal((await a.wait('error')).reason, 'bad_event')

  a.send({ op: 'unpair' })
  await a.wait('unpaired'); await b.wait('unpaired')
  a.send({ op: 'event', event: { kind: 'miss' } })
  assert.equal((await a.wait('error')).reason, 'not_paired')
  assert.ok(wb.uid && wb.token)
  await a.close(); await b.close(); await relay.close()
})

test('offline queue, reconnect with token, presence', async () => {
  const { relay, url } = await setup(null)
  const { a, b, wb } = await pairTwo(url)
  await b.close()
  const off = await a.wait('partner_status', m => m.partner.online === false)
  assert.equal(off.partner.name, '宝贝')

  a.send({ op: 'event', event: { kind: 'miss', level: 'short' } })
  const s = await a.wait('sent')
  assert.equal(s.delivered, false)

  const b2 = new Client(url); await b2.open()
  b2.send({ op: 'hello', uid: wb.uid, token: wb.token })
  const w = await b2.wait('welcome')
  assert.equal(w.paired, true); assert.equal(w.partner.name, '老板'); assert.equal(w.history.length, 1)
  const q = await b2.wait('event')
  assert.equal(q.event.id, s.event.id)
  const d = await a.wait('delivered')
  assert.deepEqual(d.ids, [s.event.id])
  await a.wait('partner_status', m => m.partner.online === true)

  // wrong token refused
  const c = new Client(url); await c.open()
  assert.equal((await c.req({ op: 'hello', uid: wb.uid, token: 'x' }, 'error')).reason, 'bad_token')
  await a.close(); await b2.close(); await c.close(); await relay.close()
})

test('pair errors and rate limit', async () => {
  const { relay, url } = await setup(null)
  const a = new Client(url); await a.open()
  await a.req({ op: 'hello', name: 'A' }, 'welcome')
  const pc = await a.req({ op: 'pair_code' }, 'pair_code')
  assert.equal((await a.req({ op: 'pair_join', code: pc.code }, 'pair_error')).reason, 'own_code')
  let last
  for (let i = 0; i < 10; i++) last = await a.req({ op: 'pair_join', code: '000000' }, 'pair_error')
  assert.equal(last.reason, 'too_many_tries')
  const n = new Client(url); await n.open()
  assert.equal((await n.req({ op: 'pair_code' }, 'error')).reason, 'hello_first')
  await a.close(); await n.close(); await relay.close()
})

test('persistence survives restart', async () => {
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'beat-')), 'beat.json')
  let { relay, url } = await setup(file)
  const { a, b, wa } = await pairTwo(url)
  a.send({ op: 'event', event: { kind: 'phrase', e: 'night', text: '晚安～' } })
  await b.wait('event')
  await a.close(); await b.close(); await relay.close()
  ;({ relay, url } = await setup(file))
  const a2 = new Client(url); await a2.open()
  const w = await a2.req({ op: 'hello', uid: wa.uid, token: wa.token }, 'welcome')
  assert.equal(w.paired, true); assert.equal(w.history[0].text, '晚安～')
  await a2.close(); await relay.close()
})
