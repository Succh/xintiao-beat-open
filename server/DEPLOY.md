# 心跳 Beat relay – deployment notes

## Live deployment: instabox (`succh-relay`)
Deployed 2026-10-30 on the user's existing instabox machine **`succh-relay`** (free plan, Debian 13, Node 22 preinstalled).

- Public URL: `https://8787-agqyzrc3avii2vtxe6iswqfr5m.instabox.tech` (health: `/health`)
- Phone app server address: **`wss://8787-agqyzrc3avii2vtxe6iswqfr5m.instabox.tech/ws`** (baked into `beat-0.1.1.apk`)
- TLS is terminated by instabox's port proxy (WebSockets pass through); no Caddy/domain needed.
- Code: `/data/workspace/beat-server` on the machine; data: `/data/workspace/beat-server/data/beat.json` (persistent disk).
- Service: systemd unit `beat.service` (enabled, `Restart=always`, `User=user`); survives reboots (tested).

### Steps used (from a computer with the instabox CLI and an API key in `INSTABOX_API_KEY`)
```bash
IB="npx -y instabox@latest"
$IB ls                                              # find the machine
$IB push succh-relay server /data/workspace/beat-server   # git-tracked files only
$IB exec succh-relay -- 'cd /data/workspace/beat-server && npm ci && npm test'
$IB exec succh-relay -- 'mkdir -p /data/workspace/beat-server/data && sudo tee /etc/systemd/system/beat.service >/dev/null <<UNIT
[Unit]
Description=Xintiao Beat relay
After=network-online.target
Wants=network-online.target
[Service]
WorkingDirectory=/data/workspace/beat-server
ExecStart=/usr/local/bin/node src/index.js
Environment=PORT=8787 HOST=0.0.0.0 DATA_FILE=/data/workspace/beat-server/data/beat.json
Restart=always
RestartSec=2
User=user
[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload && sudo systemctl enable --now beat'
$IB port publish succh-relay 8787 --name beat-relay --public   # prints the https URL; stable, kept until unpublished
node test/e2e-remote.js wss://8787-agqyzrc3avii2vtxe6iswqfr5m.instabox.tech/ws   # from any computer
```
Update the code later: `$IB push succh-relay server /data/workspace/beat-server && $IB exec succh-relay -- 'cd /data/workspace/beat-server && npm ci --omit=dev && sudo systemctl restart beat'`.
Logs: `$IB exec succh-relay -- 'journalctl -u beat -n 50 --no-pager'`.

### Free-plan limits (important)
- **Idle pause after 30 min** (fixed on free). Idle = no exec/SSH/terminal, no CPU, no public port requests; it is not documented whether an open but quiet WebSocket counts as activity. A self-paused machine **wakes automatically on a request to the public URL** (the request waits up to 45 s), so the phones' reconnect usually wakes it; processes and memory are kept across a pause.
- **Max runtime 480 min (8 h)**, then a forced pause plus a **5–20 min cooldown** in which nothing can wake it. So expect a short outage about every 8 hours; the app's offline queue/reconnect covers it, but realtime pokes in that window are delayed.
- A pause/stop done **by hand** (dashboard or `instabox suspend/stop`) is not woken by the URL: run `instabox resume succh-relay` / `instabox start succh-relay`.
- From 2026-12-01 early free accounts may run only **one** free sandbox at a time (the most recently active). Other machines on the account (`succh-main`, `succh-nanomuse`) can then pause the relay.
- For truly always-on use: a paid instabox plan (`instabox auto-suspend succh-relay 0`, `instabox max-run succh-relay -1`) or the VPS setup below.

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
