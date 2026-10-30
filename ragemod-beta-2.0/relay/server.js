// RAGE relay server (v2).
//
// What it does:
//  - decides by Minecraft NAME who may use the mod (allowlist, managed from the Dev tab in the mod)
//  - mod users online, private chat, help requests, war + top priority sync
//  - admin tools (users with IP, pending names, allow / remove / kick), protected by ADMIN_PASSWORD
//
// Environment variables (set them in your host, e.g. Render > Environment):
//   ADMIN_PASSWORD   password for the Dev tab (never put it in the mod)
//   OWNER_NAMES      comma separated Minecraft names that are always allowed, e.g. "YourName,CoOwner"
//   GITHUB_TOKEN     (optional) token so the allowlist survives restarts, see README
//   GITHUB_REPO      (optional) "owner/repo" of a repo to keep allowlist.json in (can be private)
//   GITHUB_PATH      (optional) file path in that repo, default allowlist.json
//   GITHUB_BRANCH    (optional) default main
//   PORT             set by the host

const { WebSocketServer } = require('ws');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const PORT = parseInt(process.env.PORT || '8080', 10);
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || '';
const CLAN = process.env.CLAN_NAME || 'RAGE';
const OWNERS = new Map(); // lowercase -> display name
for (const n of (process.env.OWNER_NAMES || '').split(',').map(x => x.trim()).filter(Boolean)) OWNERS.set(n.toLowerCase(), n);
const GH_TOKEN = process.env.GITHUB_TOKEN || '';
const GH_REPO = process.env.GITHUB_REPO || '';
const GH_PATH = process.env.GITHUB_PATH || 'allowlist.json';
const GH_BRANCH = process.env.GITHUB_BRANCH || 'main';
const LOCAL_FILE = path.join(__dirname, 'allowlist.json');
const NAME_RE = /^[A-Za-z0-9_]{1,16}$/;

// ---------------- allowlist (by name) ----------------
const allow = new Map(); // lowercase name -> display name

function isAllowedName(name) {
  const l = String(name).toLowerCase();
  return OWNERS.has(l) || allow.has(l);
}

function ghHeaders() {
  return {
    'Authorization': 'Bearer ' + GH_TOKEN,
    'Accept': 'application/vnd.github+json',
    'User-Agent': 'rage-relay',
    'X-GitHub-Api-Version': '2022-11-28',
  };
}

function ghUrl() {
  return `https://api.github.com/repos/${GH_REPO}/contents/${encodeURI(GH_PATH)}`;
}

async function ghGet() {
  const res = await fetch(ghUrl() + '?ref=' + encodeURIComponent(GH_BRANCH), { headers: ghHeaders() });
  if (res.status === 404) return null;
  if (!res.ok) throw new Error('GitHub GET ' + res.status);
  const json = await res.json();
  const text = Buffer.from(json.content || '', 'base64').toString('utf8');
  return { sha: json.sha, data: JSON.parse(text || '{}') };
}

let saveChain = Promise.resolve();
function saveAllow() {
  const names = [...allow.values()].sort((a, b) => a.localeCompare(b));
  try {
    fs.writeFileSync(LOCAL_FILE, JSON.stringify({ names }, null, 2));
  } catch (e) { /* read-only disk is fine */ }
  if (!GH_TOKEN || !GH_REPO) return;
  // serialize writes so the file sha never conflicts
  saveChain = saveChain.then(async () => {
    try {
      const cur = await ghGet();
      const body = {
        message: 'update allowlist',
        content: Buffer.from(JSON.stringify({ names }, null, 2)).toString('base64'),
        branch: GH_BRANCH,
      };
      if (cur) body.sha = cur.sha;
      const res = await fetch(ghUrl(), { method: 'PUT', headers: { ...ghHeaders(), 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
      if (!res.ok) throw new Error('GitHub PUT ' + res.status + ' ' + (await res.text()).slice(0, 200));
    } catch (e) {
      console.error('could not save allowlist to GitHub:', e.message);
    }
  });
}

async function loadAllow() {
  let names = [];
  try {
    if (GH_TOKEN && GH_REPO) {
      const cur = await ghGet();
      if (cur && Array.isArray(cur.data.names)) names = cur.data.names;
      console.log('allowlist loaded from GitHub:', names.length, 'names');
    } else if (fs.existsSync(LOCAL_FILE)) {
      names = JSON.parse(fs.readFileSync(LOCAL_FILE, 'utf8')).names || [];
      console.log('allowlist loaded from local file:', names.length, 'names');
    }
  } catch (e) {
    console.error('could not load allowlist:', e.message);
  }
  for (const n of names) if (NAME_RE.test(n)) allow.set(n.toLowerCase(), n);
}

// ---------------- sessions ----------------
const sessions = new Map();  // ws -> { name, uuid, server, ip, since, allowed, admin }
const pending = new Map();   // lowercase name -> { name, uuid, ip, at }
const failures = new Map();  // ip -> { count, until }

const wss = new WebSocketServer({ port: PORT, maxPayload: 8 * 1024 });

function clean(v, max = 200) {
  return String(v == null ? '' : v).replace(/[\u0000-\u001f]/g, ' ').slice(0, max);
}

function send(ws, obj) {
  if (ws.readyState === 1) ws.send(JSON.stringify(obj));
}

function ipOf(req) {
  const xf = req.headers['x-forwarded-for'];
  if (xf) return String(xf).split(',')[0].trim();
  return req.socket.remoteAddress || '?';
}

function roster() {
  const out = new Map();
  for (const [l, disp] of OWNERS) out.set(l, { name: disp, role: 'owner' });
  for (const [l, n] of allow) if (!out.has(l)) out.set(l, { name: n, role: 'member' });
  return [...out.values()].sort((a, b) => a.name.localeCompare(b.name));
}

function sendWelcome(ws, s) {
  send(ws, {
    t: 'welcome',
    allowed: s.allowed,
    clan: CLAN,
    reason: s.allowed ? '' : 'You are not on the RAGE allowlist. Give your name (' + s.name + ') to an admin.',
    roster: s.allowed ? roster() : [],
  });
}

function broadcastUsers() {
  const list = [];
  for (const [, s] of sessions) if (s.allowed) list.push({ name: s.name, uuid: s.uuid, server: s.server });
  for (const [ws, s] of sessions) if (s.allowed) send(ws, { t: 'users', list });
}

function broadcast(obj, except) {
  for (const [ws, s] of sessions) if (ws !== except && s.allowed) send(ws, obj);
}

function adminData() {
  const users = [];
  for (const [, s] of sessions) users.push({ name: s.name, uuid: s.uuid, server: s.server, ip: s.ip, since: s.since, allowed: s.allowed });
  return {
    t: 'a_data',
    users,
    pending: [...pending.values()].sort((a, b) => b.at - a.at),
    allowed: roster(),
  };
}

function applyAllowState(name) {
  const l = name.toLowerCase();
  const ok = isAllowedName(name);
  if (ok) pending.delete(l);
  for (const [ws, s] of sessions) {
    if (s.name.toLowerCase() === l && s.allowed !== ok) {
      s.allowed = ok;
      if (!ok) s.admin = false;
      sendWelcome(ws, s);
    }
  }
  broadcastUsers();
}

function safeEqual(a, b) {
  const ha = crypto.createHash('sha256').update(String(a)).digest();
  const hb = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(ha, hb);
}

wss.on('connection', (ws, req) => {
  const ip = ipOf(req);
  let hits = 0;
  const rate = setInterval(() => { hits = 0; }, 10_000);

  ws.on('message', (raw) => {
    if (++hits > 60) return; // flood protection
    let m;
    try { m = JSON.parse(raw.toString()); } catch { return; }
    let s = sessions.get(ws);

    if (m.t === 'hello') {
      const name = clean(m.name, 16);
      if (!NAME_RE.test(name)) { send(ws, { t: 'deny', reason: 'bad name' }); ws.close(); return; }
      const prev = s;
      s = {
        name,
        uuid: clean(m.uuid, 40),
        server: clean(m.server, 60),
        ip,
        since: prev ? prev.since : Date.now(),
        allowed: isAllowedName(name) || (prev ? prev.admin : false),
        admin: prev ? prev.admin : false,
      };
      sessions.set(ws, s);
      if (!s.allowed) pending.set(name.toLowerCase(), { name, uuid: s.uuid, ip, at: Date.now() });
      else pending.delete(name.toLowerCase());
      sendWelcome(ws, s);
      broadcastUsers();
      return;
    }
    if (!s) return; // must say hello first

    if (m.t === 'auth') {
      const f = failures.get(ip) || { count: 0, until: 0 };
      if (Date.now() < f.until) { send(ws, { t: 'admin', ok: false, reason: 'Too many tries, wait a few minutes.' }); return; }
      if (!ADMIN_PASSWORD) { send(ws, { t: 'admin', ok: false, reason: 'Admin is not set up on the relay (ADMIN_PASSWORD).' }); return; }
      if (safeEqual(m.password, ADMIN_PASSWORD)) {
        failures.delete(ip);
        s.admin = true;
        s.allowed = true;
        if (!allow.has(s.name.toLowerCase()) && !OWNERS.has(s.name.toLowerCase())) { allow.set(s.name.toLowerCase(), s.name); saveAllow(); }
        pending.delete(s.name.toLowerCase());
        send(ws, { t: 'admin', ok: true });
        sendWelcome(ws, s);
        broadcastUsers();
      } else {
        f.count += 1;
        if (f.count >= 5) { f.until = Date.now() + 10 * 60 * 1000; f.count = 0; }
        failures.set(ip, f);
        send(ws, { t: 'admin', ok: false, reason: 'Wrong password.' });
      }
      return;
    }

    // ---- admin only ----
    if (m.t === 'a_list' || m.t === 'a_allow' || m.t === 'a_remove' || m.t === 'a_kick') {
      if (!s.admin) return;
      const name = clean(m.name, 16);
      if (m.t === 'a_allow' && NAME_RE.test(name)) {
        allow.set(name.toLowerCase(), name);
        saveAllow();
        applyAllowState(name);
      } else if (m.t === 'a_remove' && NAME_RE.test(name)) {
        if (!OWNERS.has(name.toLowerCase())) { allow.delete(name.toLowerCase()); saveAllow(); applyAllowState(name); }
      } else if (m.t === 'a_kick' && NAME_RE.test(name)) {
        for (const [cws, cs] of sessions) {
          if (cs.name.toLowerCase() === name.toLowerCase() && cws !== ws) { send(cws, { t: 'deny', reason: 'kicked by an admin' }); cws.close(); }
        }
      }
      send(ws, adminData());
      return;
    }

    // ---- everything below needs access ----
    if (!s.allowed) return;

    switch (m.t) {
      case 'pm': {
        const to = clean(m.to, 16).toLowerCase();
        for (const [cws, cs] of sessions) {
          if (cs.allowed && cs.name.toLowerCase() === to) {
            send(cws, { t: 'pm', from: s.name, msg: clean(m.msg, 300) });
            break;
          }
        }
        break;
      }
      case 'help':
        broadcast({ t: 'help', from: s.name, msg: clean(m.msg, 200), server: clean(m.server || s.server, 60) }, ws);
        break;
      case 'war': {
        const enemies = Array.isArray(m.enemies) ? m.enemies.slice(0, 64).map(e => clean(e, 16)) : [];
        broadcast({ t: 'war', action: clean(m.action, 10), from: s.name, server: clean(m.server || s.server, 60), enemies, name: clean(m.name, 16) }, ws);
        break;
      }
      case 'ping':
        break;
    }
  });

  ws.on('close', () => {
    clearInterval(rate);
    if (sessions.delete(ws)) broadcastUsers();
  });
  ws.on('error', () => {});
});

loadAllow().then(() => {
  console.log('RAGE relay listening on port', PORT,
    ADMIN_PASSWORD ? '(admin enabled)' : '(WARNING: ADMIN_PASSWORD not set, Dev tab disabled)',
    OWNERS.size ? '' : '(WARNING: OWNER_NAMES not set)');
});
