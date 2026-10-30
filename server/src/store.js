'use strict'
// Tiny JSON-file store. Fine for one couple / a handful of users; swap for SQLite/Redis if it grows.
const fs = require('fs')
const path = require('path')

class Store {
  constructor(file) {
    this.file = file
    this.data = { users: {}, history: {}, queues: {} }
    this.timer = null
    if (file && fs.existsSync(file)) {
      try { this.data = Object.assign(this.data, JSON.parse(fs.readFileSync(file, 'utf8'))) } catch (e) {
        console.error('store: could not read', file, e.message)
      }
    }
  }
  user(uid) { return this.data.users[uid] || null }
  putUser(u) { this.data.users[u.uid] = u; this.save() }
  pairKey(a, b) { return [a, b].sort().join(':') }
  history(a, b) { return this.data.history[this.pairKey(a, b)] || [] }
  addHistory(a, b, ev, keep) {
    const k = this.pairKey(a, b)
    const h = this.data.history[k] || (this.data.history[k] = [])
    h.push(ev)
    if (h.length > keep) h.splice(0, h.length - keep)
    this.save()
  }
  clearHistory(a, b) { delete this.data.history[this.pairKey(a, b)]; this.save() }
  enqueue(uid, msg, keep) {
    const q = this.data.queues[uid] || (this.data.queues[uid] = [])
    q.push(msg)
    if (q.length > keep) q.splice(0, q.length - keep)
    this.save()
  }
  drain(uid) {
    const q = this.data.queues[uid] || []
    delete this.data.queues[uid]
    if (q.length) this.save()
    return q
  }
  save() {
    if (!this.file || this.timer) return
    this.timer = setTimeout(() => { this.timer = null; this.flush() }, 200)
  }
  flush() {
    if (!this.file) return
    if (this.timer) { clearTimeout(this.timer); this.timer = null }
    fs.mkdirSync(path.dirname(this.file), { recursive: true })
    const tmp = this.file + '.tmp'
    fs.writeFileSync(tmp, JSON.stringify(this.data))
    fs.renameSync(tmp, this.file)
  }
}

module.exports = { Store }
