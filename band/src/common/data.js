// Shared constants (keep in sync with phone/.../Moods.java and server/protocol.js)
export const MOODS = [
  { id: 'happy', label: '开心' },
  { id: 'miss', label: '想你' },
  { id: 'tired', label: '累了' },
  { id: 'angry', label: '生气' },
  { id: 'aggrieved', label: '委屈' },
  { id: 'busy', label: '忙碌' },
  { id: 'hungry', label: '饿了' },
  { id: 'night', label: '晚安' }
]

export const DEFAULT_PHRASES = [
  { e: 'eyes', text: '在干嘛？' },
  { e: 'heart', text: '想你了' },
  { e: 'night', text: '晚安～' },
  { e: 'rice', text: '吃饭了吗' },
  { e: 'run', text: '马上到！' }
]

// emoji keys that have a PNG in /common/emoji
export const EMOJI_KEYS = ['happy', 'miss', 'tired', 'angry', 'aggrieved', 'busy', 'hungry', 'night',
  'eyes', 'heart', 'rice', 'run', 'chat', 'hearts', 'poke', 'coffee', 'party', 'kiss', 'hug', 'wave',
  'home', 'phone', 'book', 'ok', 'sun', 'sparkle']
