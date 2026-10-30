import test from 'node:test'
import assert from 'node:assert/strict'
import { timeAgo, countdown, codeDigits, parseMessage, describeIncoming, vibrationPlan, emojiSrc, moodLabel } from '../src/common/logic.js'

test('timeAgo', () => {
  const now = 1_000_000_000
  assert.equal(timeAgo(now - 5000, now), '刚刚')
  assert.equal(timeAgo(now - 3 * 60000, now), '3 分钟前')
  assert.equal(timeAgo(now - 2 * 3600000, now), '2 小时前')
  assert.equal(timeAgo(0, now), '')
})

test('countdown + codeDigits', () => {
  assert.equal(countdown(292000, 0), '04:52')
  assert.equal(countdown(0, 5000), '00:00')
  assert.deepEqual(codeDigits('382915'), ['3', '8', '2', '9', '1', '5'])
  assert.deepEqual(codeDigits('12'), ['1', '2', '-', '-', '-', '-'])
})

test('parseMessage handles interconnect shapes', () => {
  assert.deepEqual(parseMessage({ data: '{"t":"state","paired":true}' }), { t: 'state', paired: true })
  assert.deepEqual(parseMessage({ data: { t: 'code', code: '1' } }), { t: 'code', code: '1' })
  assert.equal(parseMessage({ data: 'not json' }), null)
  assert.equal(parseMessage({ data: '{"x":1}' }), null)
})

test('incoming descriptions + vibration', () => {
  const now = Date.now()
  assert.equal(describeIncoming({ kind: 'miss', level: 'poke3', ts: now }, now).meta, '刚刚 · 戳了你 3 下')
  assert.equal(describeIncoming({ kind: 'mood', mood: 'tired', ts: now }, now).title, '困困')
  assert.equal(describeIncoming({ kind: 'phrase', e: 'eyes', text: '在干嘛？', ts: now }, now).icon, 'eyes')
  assert.equal(vibrationPlan({ kind: 'miss', level: 'poke3' }).length, 3)
  assert.deepEqual(vibrationPlan({ kind: 'miss', level: 'long' }), [[0, 'long']])
  assert.equal(emojiSrc('nope'), '/common/emoji/chat.png')
  assert.equal(moodLabel('night'), '困困')  // legacy night→sleepy
  assert.equal(moodLabel('shy'), '害羞')
  assert.equal(moodLabel('miss'), '想你')
  assert.equal(moodLabel('miss_you'), '想你')
  assert.equal(moodLabel('unknown_mood_x'), '心情')
  assert.equal(describeIncoming({ kind: 'mood', mood: 'love', ts: now }, now).title, '喜欢')
})

test('silent skips vibration', () => {
  assert.deepEqual(vibrationPlan({ kind: 'miss', level: 'short', silent: true }), [])
  assert.deepEqual(vibrationPlan({ kind: 'ack' }), [])
  assert.equal(describeIncoming({ kind: 'ack', ts: Date.now() }, Date.now()).title, '收到啦')
})
