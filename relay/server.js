const http = require('http');
const crypto = require('crypto');
const WebSocket = require('ws');

const PORT = process.env.PORT || 8080;
const TOKEN = process.env.RELAY_TOKEN || null;

// This relay is single-tenant — one deployment serves exactly one pair of
// people (per the pairing/QR flow in Settings), never a pool of strangers.
// That's why there's no multi-room/session-id model here: "the room" is
// just this process. What still matters, and what this file exists to fix,
// is *identity* across a reconnect — a device that drops (app killed by
// Android's low-memory reaper, backgrounded through Netflix, network blip)
// needs to get its own seat back, not race a stale ghost for one of only
// two slots.
const MAX_DEVICE_SEATS = Number(process.env.MAX_DEVICE_SEATS || 8);
const MAX_CHAT_PEERS = 4;
const RECONNECT_GRACE_MS = 60_000;
const HEARTBEAT_INTERVAL_MS = 15_000;
// Device seats reclaim themselves via reconnect tokens, but a chat peer
// (a phone browser tab) has no such lifecycle — someone can leave the page
// open long after the movie's over, holding a slot indefinitely. Kicking
// after a fixed session length bounds that without needing any real
// idle-detection.
const CHAT_MAX_SESSION_MS = 30 * 60_000;

// device seats: up to MAX_DEVICE_SEATS, index 0 is host, 1..N-1 are guests —
// assigned in first-ever-connect order and then stable across reconnects
// because a returning device presents the reconnectToken it was minted the
// first time, reclaiming its own seat rather than taking whichever is free.
// seat.ws is null while the seat is reserved-but-disconnected.
const seats = new Array(MAX_DEVICE_SEATS).fill(null);
const chatPeers = new Set();

function mintToken() {
  return crypto.randomBytes(24).toString('base64url');
}

function findSeatByPeerId(peerId) {
  return seats.findIndex((seat) => seat && seat.peerId === peerId);
}

function releaseExpiredReservations() {
  const now = Date.now();
  for (let i = 0; i < seats.length; i++) {
    const seat = seats[i];
    if (seat && seat.ws === null && seat.reservedUntil !== null && now >= seat.reservedUntil) {
      seats[i] = null;
    }
  }
}

function handleDeviceHello(ws, msg) {
  releaseExpiredReservations();
  const peerId = typeof msg.peerId === 'string' && msg.peerId ? msg.peerId : crypto.randomUUID();

  const existingIndex = findSeatByPeerId(peerId);
  if (existingIndex !== -1) {
    const seat = seats[existingIndex];
    if (seat.reconnectToken !== msg.reconnectToken) {
      // Same peerId but the wrong token — don't hand back someone else's
      // seat just because they guessed/reused an id.
      ws.send(JSON.stringify({ type: 'full' }));
      ws.close();
      return;
    }
    if (seat.ws) {
      // Same device reconnecting while its old socket is still technically
      // open (e.g. a fast app relaunch before the heartbeat noticed) — the
      // new connection wins.
      try { seat.ws.terminate(); } catch { /* already gone */ }
    }
    seat.ws = ws;
    seat.reservedUntil = null;
    ws.deviceSeatIndex = existingIndex;
    ws.send(JSON.stringify({ type: 'welcome', peerId, reconnectToken: seat.reconnectToken, seatIndex: existingIndex }));
    return;
  }

  const freeIndex = seats.findIndex((seat) => seat === null);
  if (freeIndex === -1) {
    ws.send(JSON.stringify({ type: 'full' }));
    ws.close();
    return;
  }

  const reconnectToken = mintToken();
  seats[freeIndex] = { peerId, reconnectToken, ws, reservedUntil: null };
  ws.deviceSeatIndex = freeIndex;
  ws.send(JSON.stringify({ type: 'welcome', peerId, reconnectToken, seatIndex: freeIndex }));
}

function handleChatHello(ws) {
  if (chatPeers.size >= MAX_CHAT_PEERS) {
    ws.send(JSON.stringify({ type: 'full' }));
    ws.close();
    return;
  }
  ws.isChatPeer = true;
  ws.chatConnectedAt = Date.now();
  chatPeers.add(ws);
  ws.send(JSON.stringify({ type: 'welcome', peerId: crypto.randomUUID(), seatIndex: null }));
}

function broadcastEvent(sender, payload) {
  const data = JSON.stringify({ type: 'event', payload });
  for (const seat of seats) {
    if (seat && seat.ws && seat.ws !== sender && seat.ws.readyState === WebSocket.OPEN) {
      seat.ws.send(data);
    }
  }
  for (const peer of chatPeers) {
    if (peer !== sender && peer.readyState === WebSocket.OPEN) {
      peer.send(data);
    }
  }
}

function releaseConnection(ws) {
  if (ws.isChatPeer) {
    chatPeers.delete(ws);
    return;
  }
  if (typeof ws.deviceSeatIndex === 'number') {
    const seat = seats[ws.deviceSeatIndex];
    if (seat && seat.ws === ws) {
      seat.ws = null;
      seat.reservedUntil = Date.now() + RECONNECT_GRACE_MS;
    }
  }
}

// A plain WebSocket.Server with no request handler leaves regular HTTP GETs
// (e.g. Render's health check) hanging forever, so the WS server is attached
// to an http.Server that answers those directly. It also now serves the
// phone-facing chat page (see CHAT_PAGE_HTML below).
const server = http.createServer((req, res) => {
  const { pathname } = new URL(req.url, `http://${req.headers.host}`);
  if (pathname === '/chat') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(CHAT_PAGE_HTML);
    return;
  }
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('shumtimes relay ok\n');
});

// noServer + a manual 'upgrade' handler, instead of letting WebSocket.Server
// auto-accept every upgrade: rejecting a bad token *before* the handshake
// completes means a bad client gets an immediate HTTP error and no
// connection at all, rather than being accepted and then close()'d
// afterward — the latter measured as a ~20s-delayed, code-1006 disconnect
// through Render's proxy in testing, instead of a prompt rejection.
const wss = new WebSocket.Server({ noServer: true });

server.on('upgrade', (req, socket, head) => {
  const { searchParams } = new URL(req.url, `http://${req.headers.host}`);

  if (TOKEN && searchParams.get('token') !== TOKEN) {
    socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
    socket.destroy();
    return;
  }

  wss.handleUpgrade(req, socket, head, (ws) => {
    wss.emit('connection', ws, req);
  });
});

wss.on('connection', (ws) => {
  ws.isAlive = true;
  ws.on('pong', () => {
    ws.isAlive = true;
  });

  ws.on('message', (data) => {
    let msg;
    try {
      msg = JSON.parse(data.toString());
    } catch {
      return; // Malformed — ignore rather than tear down the connection.
    }
    switch (msg.type) {
      case 'hello':
        if (msg.role === 'chat') {
          handleChatHello(ws);
        } else {
          handleDeviceHello(ws, msg);
        }
        break;
      case 'event':
        broadcastEvent(ws, msg.payload);
        break;
    }
  });

  ws.on('close', () => releaseConnection(ws));
});

// Ghost-connection reaper: terminate() (not close()) is deliberate — a
// non-responding socket may not be able to complete a graceful close
// handshake either, so this forces the TCP connection down immediately and
// frees its seat (with the same reconnect grace as a clean disconnect)
// right away instead of waiting on a TCP-level timeout that can take many
// minutes or never fire at all through a hosting provider's proxy.
const heartbeatInterval = setInterval(() => {
  const now = Date.now();
  for (const ws of wss.clients) {
    if (ws.isChatPeer && now - ws.chatConnectedAt > CHAT_MAX_SESSION_MS) {
      // A real close (not terminate()) with a distinct message type — the
      // page needs to tell this apart from an ordinary drop so it doesn't
      // just auto-reconnect and immediately restart the 30-minute clock.
      try { ws.send(JSON.stringify({ type: 'kicked', reason: 'session_expired' })); } catch { /* already gone */ }
      releaseConnection(ws);
      ws.close(4001, 'session expired');
      continue;
    }
    if (ws.isAlive === false) {
      releaseConnection(ws);
      ws.terminate();
      continue;
    }
    ws.isAlive = false;
    ws.ping();
  }
}, HEARTBEAT_INTERVAL_MS);

wss.on('close', () => clearInterval(heartbeatInterval));

// Minimal, dependency-free chat client — no app install needed, matches the
// existing "Pair from phone" QR flow's philosophy of a plain served page.
// Reads its relay token straight from the URL the QR code encodes
// (?token=...) and reconnects with backoff since a phone's browser tab can
// lose its socket (screen lock, backgrounding) far more often than the TV.
const CHAT_PAGE_HTML = `<!doctype html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, interactive-widget=resizes-content">
<title>Movies Shumtimes — Chat</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  html, body { height: 100%; }
  body {
    /* interactive-widget=resizes-content (above) makes the visual viewport
       actually shrink when the on-screen keyboard opens, instead of the
       keyboard just overlaying fixed content — 100dvh (falls back to 100vh
       on browsers that don't know the unit) means this flex column
       recalculates to that shorter height, so the input at the bottom
       stays pinned above the keyboard like a texting app instead of
       getting covered by it. */
    margin: 0; height: 100vh; height: 100dvh; display: flex; flex-direction: column;
    background: #0D0D12; color: #F2F2F5; font-family: -apple-system, system-ui, sans-serif;
  }
  header { padding: 16px 20px; border-bottom: 1px solid #2A2A33; }
  h1 { font-size: 16px; margin: 0; }
  #status { font-size: 12px; color: #C7C7D1; margin-top: 4px; }
  #nameField {
    background: transparent; border: none; border-bottom: 1px dashed #C7C7D1;
    color: #E795FC; font-weight: 600; font-size: 12px; padding: 0 2px; width: 120px;
  }
  #nameField:focus { outline: none; border-bottom-color: #AD2BD7; }
  #log { flex: 1; overflow-y: auto; padding: 16px 20px; display: flex; flex-direction: column; gap: 8px; }
  .msg { font-size: 14px; }
  .msg .who { color: #E795FC; font-weight: 600; margin-right: 6px; }
  form { display: flex; gap: 8px; padding: 16px 20px; border-top: 1px solid #2A2A33; }
  input[type=text] {
    flex: 1; padding: 12px; border-radius: 10px; border: 1px solid #2A2A33;
    background: #2A2A33; color: #F2F2F5; font-size: 16px;
  }
  input[type=text]:focus { outline: none; border-color: #AD2BD7; }
  button {
    padding: 12px 20px; border-radius: 10px; border: none; font-size: 16px; font-weight: 600;
    color: #0D0D12; cursor: pointer; background: linear-gradient(135deg, #E795FC, #AD2BD7);
  }
</style>
</head><body>
  <header>
    <h1>Movies Shumtimes — Chat</h1>
    <div id="status">Connecting…</div>
    <div>Chatting as <input type="text" id="nameField" maxlength="24"></div>
  </header>
  <div id="log"></div>
  <form id="form">
    <input type="text" id="text" placeholder="Say something…" autocomplete="off" maxlength="200">
    <button type="submit">Send</button>
  </form>
  <script>
    const statusEl = document.getElementById('status');
    const logEl = document.getElementById('log');
    const form = document.getElementById('form');
    const textInput = document.getElementById('text');
    const nameField = document.getElementById('nameField');

    // Priority: a name you've already edited here (localStorage) beats the
    // TV's QR-embedded default (?name=...) on return visits, which beats a
    // random placeholder if neither is set. Scanning your own TV's QR the
    // first time pre-fills your real Plex username instead of "Phone 123".
    const urlName = new URLSearchParams(location.search).get('name');
    let username = localStorage.getItem('shumtimes_chat_name')
      || urlName
      || ('Phone ' + Math.floor(Math.random() * 900 + 100));
    nameField.value = username;

    nameField.addEventListener('change', () => {
      username = nameField.value.trim() || username;
      nameField.value = username;
      localStorage.setItem('shumtimes_chat_name', username);
    });

    function appendLine(who, text) {
      const line = document.createElement('div');
      line.className = 'msg';
      const whoEl = document.createElement('span');
      whoEl.className = 'who';
      whoEl.textContent = who + ':';
      line.appendChild(whoEl);
      line.appendChild(document.createTextNode(text));
      logEl.appendChild(line);
      logEl.scrollTop = logEl.scrollHeight;
    }

    let ws;
    let backoffMs = 1000;
    let kicked = false;
    function connect() {
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
      ws = new WebSocket(proto + '//' + location.host + '/' + location.search);
      ws.onopen = () => {
        statusEl.textContent = 'Connected';
        backoffMs = 1000;
        ws.send(JSON.stringify({ type: 'hello', role: 'chat' }));
      };
      ws.onclose = () => {
        if (kicked) return;
        statusEl.textContent = 'Disconnected — retrying…';
        setTimeout(connect, backoffMs);
        backoffMs = Math.min(backoffMs * 2, 15000);
      };
      ws.onerror = () => ws.close();
      ws.onmessage = (event) => {
        const msg = JSON.parse(event.data);
        if (msg.type === 'kicked') {
          kicked = true;
          statusEl.textContent = 'Session ended after 30 minutes — reopen the chat from the TV to rejoin.';
          return;
        }
        if (msg.type === 'event' && msg.payload && msg.payload.kind === 'chat') {
          appendLine(msg.payload.username || 'them', msg.payload.text || '');
        }
      };
    }
    connect();

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const text = textInput.value.trim();
      if (!text || !ws || ws.readyState !== WebSocket.OPEN) return;
      ws.send(JSON.stringify({ type: 'event', payload: { kind: 'chat', username, text } }));
      appendLine('you', text);
      textInput.value = '';
    });
  </script>
</body></html>
`;

server.listen(PORT, () => {
  console.log(`Shumtimes relay listening on port ${PORT}`);
});
