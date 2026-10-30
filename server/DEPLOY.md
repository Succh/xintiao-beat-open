# 心跳 Beat relay – deployment notes

## Generic VPS option

## What it is
- Node.js ≥ 18, one dependency (`ws`). About 30 MB RAM. Data is one JSON file (`data/beat.json`).
- Endpoints: `GET /health` (JSON), WebSocket at `/ws`.
- Env vars: `PORT` (default 8787), `HOST` (default 0.0.0.0), `DATA_FILE`.

## What hosting is needed
Both phones must reach the relay from mobile data, so it has to run on an **always-on public server**:
1. **A small Linux VPS** (the smallest plan is plenty: 1 vCPU / 512 MB). A mainland-China VPS is the most reliable for phones in China, but it needs ICP 备案 to serve a domain on ports 80/443. A Hong Kong or Japan VPS needs no 备案 and is usually fine.
2. **A domain name** (or a subdomain), e.g. `beat.example.com`, pointing at the VPS.
3. **TLS (wss://)**. Use Caddy as a reverse proxy; it fetches a free Let's Encrypt certificate automatically.

Free "serverless" tiers that sleep when idle are a poor fit, because WebSockets must stay open all day.

## Steps on a fresh Debian/Ubuntu VPS
```bash
# 1. Node + the code
sudo apt-get install -y nodejs npm caddy        # or NodeSource for a newer Node
sudo mkdir -p /opt/beat && sudo chown $USER /opt/beat
# copy this server/ folder to /opt/beat (scp/rsync/git)
cd /opt/beat && npm ci --omit=dev

# 2. systemd service
sudo tee /etc/systemd/system/beat.service >/dev/null <<'UNIT'
[Unit]
Description=Xintiao Beat relay
After=network.target
[Service]
WorkingDirectory=/opt/beat
ExecStart=/usr/bin/node src/index.js
Environment=PORT=8787 HOST=127.0.0.1 DATA_FILE=/opt/beat/data/beat.json
Restart=always
User=www-data
[Install]
WantedBy=multi-user.target
UNIT
sudo chown -R www-data /opt/beat/data 2>/dev/null || true
sudo systemctl enable --now beat

# 3. Caddy (TLS + WebSocket proxy)
sudo tee /etc/caddy/Caddyfile >/dev/null <<'CADDY'
beat.example.com {
    reverse_proxy 127.0.0.1:8787
}
CADDY
sudo systemctl reload caddy
```
Then, in the phone app, go to 设置 → 服务器地址 and enter `wss://beat.example.com/ws`.

## Quick test without a domain (not for daily use)
`ws://<VPS-IP>:8787/ws` works if port 8787 is open (set `HOST=0.0.0.0`). The Android app allows plain `ws://`, but traffic is unencrypted.

## Backups
Copy `data/beat.json` occasionally. Losing it means both phones must pair again.
