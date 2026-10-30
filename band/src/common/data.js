// Shared constants (keep in sync with phone/.../Logic.java and server/protocol.js)
export const MOODS = [
  { id: 'miss_you', label: '想你' },
  { id: 'sweet', label: '甜甜' },
  { id: 'love', label: '喜欢' },
  { id: 'warm', label: '暖暖' },
  { id: 'happy', label: '开心' },
  { id: 'soft', label: '软软' },
  { id: 'shy', label: '害羞' },
  { id: 'clingy', label: '黏你' },
  { id: 'sleepy', label: '困困' },
  { id: 'cozy', label: '窝着' },
  { id: 'excited', label: '兴奋' },
  { id: 'grateful', label: '感谢' },
  { id: 'playful', label: '调皮' },
  { id: 'calm', label: '平静' }
]

/** Old ids → canonical (0.1.2 / early 0.1.3). */
export const MOOD_ALIASES = {
  miss: 'miss_you', tired: 'sleepy', angry: 'soft', aggrieved: 'soft',
  busy: 'calm', hungry: 'cozy', night: 'sleepy', jealous: 'shy',
  cheer: 'excited', melt: 'love'
}

export const DEFAULT_PHRASES = [
  { e: 'eyes', text: '在干嘛？' },
  { e: 'heart', text: '想你了' },
  { e: 'night', text: '晚安～' },
  { e: 'rice', text: '吃饭了吗' },
  { e: 'run', text: '马上到！' }
]

// emoji keys that have a PNG in /common/emoji
export const EMOJI_KEYS = [
  'miss_you', 'sweet', 'love', 'warm', 'happy', 'soft', 'shy', 'clingy',
  'sleepy', 'cozy', 'excited', 'grateful', 'playful', 'calm',
  'miss', 'tired', 'angry', 'aggrieved', 'busy', 'hungry', 'night', 'jealous', 'cheer', 'melt',
  'eyes', 'heart', 'rice', 'run', 'chat', 'hearts', 'poke', 'coffee', 'party', 'kiss', 'hug', 'wave',
  'home', 'phone', 'book', 'ok', 'sun', 'sparkle']
