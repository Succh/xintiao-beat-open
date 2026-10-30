'use strict'
// Shared vocabulary (keep in sync with band/src/common/data.js and phone Logic.java)
const MOODS = [
  'miss_you', 'sweet', 'love', 'warm', 'happy', 'soft', 'shy', 'clingy',
  'sleepy', 'cozy', 'excited', 'grateful', 'playful', 'calm'
]
const MOOD_ALIASES = {
  miss: 'miss_you', tired: 'sleepy', angry: 'soft', aggrieved: 'soft',
  busy: 'calm', hungry: 'cozy', night: 'sleepy', jealous: 'shy',
  cheer: 'excited', melt: 'love'
}
const EVENT_KINDS = ['miss', 'mood', 'phrase', 'poke', 'ack']
const MISS_LEVELS = ['short', 'long', 'poke3']
const MODES = ['notify', 'full']
const LIMITS = {
  name: 16,
  text: 40,
  device: 40,
  emojiKey: 16,
  historyKeep: 200,
  historySend: 50,
  queueKeep: 100,
  codeTtlMs: 5 * 60 * 1000,
  joinPerMinute: 10,
  maxPayload: 16 * 1024
}

function str(v, max) {
  if (typeof v !== 'string') return ''
  return Array.from(v.trim()).slice(0, max).join('')
}

function canonicalizeMood(id) {
  if (!id || typeof id !== 'string') return ''
  return MOOD_ALIASES[id] || id
}

// Validate + normalise an event coming from a client. Returns null if invalid.
function cleanEvent(e) {
  if (!e || typeof e !== 'object') return null
  const kind = e.kind
  if (EVENT_KINDS.indexOf(kind) < 0) return null
  const out = { kind }
  if (kind === 'miss') out.level = MISS_LEVELS.indexOf(e.level) >= 0 ? e.level : 'short'
  if (kind === 'poke') { out.kind = 'miss'; out.level = 'poke3' }
  if (kind === 'mood') {
    const mood = canonicalizeMood(e.mood)
    if (MOODS.indexOf(mood) < 0) return null
    out.mood = mood
    out.text = str(e.text, LIMITS.text)
  }
  if (kind === 'phrase') {
    out.text = str(e.text, LIMITS.text)
    if (!out.text) return null
    out.e = str(e.e, LIMITS.emojiKey) || 'chat'
  }
  if (typeof e.cid === 'string') out.cid = str(e.cid, 40) // client correlation id
  out.via = e.via === 'band' ? 'band' : 'phone'
  return out
}

module.exports = { MOODS, MOOD_ALIASES, EVENT_KINDS, MISS_LEVELS, MODES, LIMITS, str, canonicalizeMood, cleanEvent }
