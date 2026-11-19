#!/usr/bin/env node
// Demo helper: makes a family member "walk" so you can watch their marker move live in the
// app. Signs in (or registers) a demo user, optionally joins a circle, then reports a point
// every few seconds along a loop around a center point. Node 18+, no dependencies.
//
//   node tools/simulate-walk.mjs --code K7QM-3XPA                 # join this circle and walk
//   node tools/simulate-walk.mjs --code K7QM-3XPA --lat 40.4168 --lng -3.7038 --every 3
//
// Options: --api (default http://localhost:8080)  --name Leo  --email leo.demo@example.com
//          --password demo-pass-123  --code <invite code>  --lat --lng (loop center)
//          --radius 300 (meters)  --every 2 (seconds)

const args = Object.fromEntries(
  process.argv.slice(2).reduce((pairs, arg, i, all) => {
    if (arg.startsWith('--')) pairs.push([arg.slice(2), all[i + 1]]);
    return pairs;
  }, []),
);

const API = args.api ?? 'http://localhost:8080';
const name = args.name ?? 'Leo';
const email = args.email ?? `${name.toLowerCase()}.demo@example.com`;
const password = args.password ?? 'demo-pass-123';
const center = { lat: Number(args.lat ?? 40.4168), lng: Number(args.lng ?? -3.7038) }; // Madrid
const radiusM = Number(args.radius ?? 300);
const everyS = Number(args.every ?? 2);

async function call(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  return { status: res.status, data: text ? JSON.parse(text) : null };
}

async function signIn() {
  let res = await call('/api/auth/login', { method: 'POST', body: { email, password } });
  if (res.status === 401) {
    res = await call('/api/auth/register', { method: 'POST', body: { name, email, password } });
    console.log(`registered demo user ${email}`);
  }
  if (res.status >= 300) throw new Error(`sign-in failed: ${res.status} ${JSON.stringify(res.data)}`);
  return res.data.accessToken;
}

let token = await signIn();

if (args.code) {
  const res = await call('/api/families/join', { method: 'POST', body: { inviteCode: args.code }, token });
  if (res.status !== 200) throw new Error(`join failed: ${res.status} ${res.data?.detail ?? ''}`);
  console.log(`${name} is in "${res.data.name}" (${res.data.memberCount} members)`);
}

// Walk a loop: ~120 steps per lap, battery slowly draining.
const metersPerDegLat = 111_320;
const metersPerDegLng = 111_320 * Math.cos((center.lat * Math.PI) / 180);
let step = 0;
let battery = 87;

console.log(`walking a ${radiusM} m loop around ${center.lat}, ${center.lng} every ${everyS}s (Ctrl+C to stop)`);
setInterval(async () => {
  const angle = (step++ / 120) * 2 * Math.PI;
  const lat = center.lat + (radiusM * Math.sin(angle)) / metersPerDegLat;
  const lng = center.lng + (radiusM * Math.cos(angle)) / metersPerDegLng;
  if (step % 20 === 0) battery = Math.max(5, battery - 1);

  let res = await call('/api/locations', {
    method: 'POST', token,
    body: { lat: +lat.toFixed(6), lng: +lng.toFixed(6), accuracy: +(5 + Math.random() * 10).toFixed(1), battery },
  });
  if (res.status === 401) { // access token expired (15 min): sign in again
    token = await signIn();
    return;
  }
  const where = `${lat.toFixed(5)}, ${lng.toFixed(5)}`;
  console.log(res.data?.shared ? `→ ${where}  battery ${battery}%` : `not shared (${res.status}): join a circle with --code`);
}, everyS * 1000);
