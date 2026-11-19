#!/usr/bin/env node
// Minimal STOMP-over-WebSocket listener for manual testing. Needs Node 22+ (built-in
// WebSocket) and no npm install.
//
//   TOKEN=<access token> node tools/ws-listen.mjs <familyId> [ws://localhost:8080/ws]
//
// Connects with the JWT, subscribes to /topic/family/<familyId>/locations and prints
// every location pushed to the circle.

const [familyId, url = 'ws://localhost:8080/ws'] = process.argv.slice(2);
const token = process.env.TOKEN;
if (!familyId || !token) {
  console.error('usage: TOKEN=<access token> node tools/ws-listen.mjs <familyId> [wsUrl]');
  process.exit(1);
}

const NUL = '\0';
const frame = (command, headers = {}, body = '') =>
  `${command}\n${Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n')}\n\n${body}${NUL}`;

/** Parses one STOMP frame. Heartbeats are bare newlines and come back as null. */
function parse(raw) {
  const text = raw.replace(/^\n+/, '');
  if (!text) return null;
  const [head, ...rest] = text.split('\n\n');
  const [command, ...headerLines] = head.split('\n');
  const headers = Object.fromEntries(headerLines.map((l) => [l.slice(0, l.indexOf(':')), l.slice(l.indexOf(':') + 1)]));
  return { command, headers, body: rest.join('\n\n') };
}

const destination = `/topic/family/${familyId}/locations`;
const ws = new WebSocket(url, ['v12.stomp']);

ws.addEventListener('open', () => {
  // Heart-beat 0,0: this simple tool doesn't send heartbeats, so ask the server not to expect them.
  ws.send(frame('CONNECT', { 'accept-version': '1.2', host: 'localhost', 'heart-beat': '0,0', Authorization: `Bearer ${token}` }));
});

ws.addEventListener('message', (event) => {
  for (const raw of String(event.data).split(NUL)) {
    const f = parse(raw);
    if (!f) continue;
    if (f.command === 'CONNECTED') {
      ws.send(frame('SUBSCRIBE', { id: 'sub-0', destination }));
      console.log(`connected, subscribing to ${destination} (Ctrl+C to stop)`);
    } else if (f.command === 'MESSAGE') {
      const p = JSON.parse(f.body);
      console.log(`${new Date().toLocaleTimeString()}  user ${p.userId.slice(0, 8)}  ` +
        `${p.lat}, ${p.lng}  ±${p.accuracy != null ? Math.round(p.accuracy) : '?'}m  battery ${p.battery ?? '?'}%  (recorded ${p.recordedAt})`);
    } else if (f.command === 'ERROR') {
      console.error(`server rejected: ${f.headers.message ?? f.body}`);
    }
  }
});

ws.addEventListener('close', () => { console.log('disconnected'); process.exit(0); });
ws.addEventListener('error', (e) => { console.error('websocket error:', e.message ?? e); process.exit(1); });
