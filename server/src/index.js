'use strict'
const path = require('path')
const { createRelay } = require('./server')

const port = Number(process.env.PORT || 8787)
const host = process.env.HOST || '0.0.0.0'
const dataFile = process.env.DATA_FILE || path.join(__dirname, '..', 'data', 'beat.json')

const relay = createRelay({ dataFile })
relay.listen(port, host).then(p => console.log(`心跳 Beat relay listening on ${host}:${p} (ws path /ws, data ${dataFile})`))

function shutdown() { relay.close().then(() => process.exit(0)) }
process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)
