'use strict'
const WebSocket = require('ws')

class Client {
  constructor(url) {
    this.url = url
    this.inbox = []
    this.waiters = []
  }
  open() {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(this.url)
      this.ws.on('open', resolve)
      this.ws.on('error', reject)
      this.ws.on('message', d => {
        const m = JSON.parse(d.toString())
        this.inbox.push(m)
        this.waiters = this.waiters.filter(w => { if (w.pred(m)) { w.resolve(m); return false } return true })
      })
    })
  }
  send(m) { this.ws.send(JSON.stringify(m)) }
  wait(op, pred = () => true, ms = 2000) {
    const p = m => m.op === op && pred(m)
    const i = this.inbox.findIndex(p)
    if (i >= 0) return Promise.resolve(this.inbox.splice(i, 1)[0])
    return new Promise((resolve, reject) => {
      const w = { pred: p, resolve: m => { const j = this.inbox.indexOf(m); if (j >= 0) this.inbox.splice(j, 1); resolve(m) } }
      this.waiters.push(w)
      setTimeout(() => reject(new Error('timeout waiting for ' + op)), ms)
    })
  }
  async req(m, op) { this.send(m); return this.wait(op) }
  close() { return new Promise(r => { this.ws.once('close', r); this.ws.close() }) }
}

module.exports = { Client }
