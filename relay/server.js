const http = require('http');
const crypto = require('crypto');
const WebSocket = require('ws');

const PORT = process.env.PORT || 8080;
const TOKEN = process.env.RELAY_TOKEN || null;

// Multi-tenant: one deployment serves a whole friend group, hosting many
// independent rooms at once, each identified by a roomId. Pressing "Watch
// Together" mints a fresh room (host seated at index 0); joining requires
// that specific roomId (learned from the Home screen's /rooms directory,
// not shared out-of-band the way the old single-tenant pairing flow worked).
// A room lives as long as anyone at all is seated in it — the host leaving
// no longer ends it outright (a guest mid-movie shouldn't get cut off just
// because the host's connection dropped); it's only deleted once every seat
// has been empty for EMPTY_ROOM_TIMEOUT_MS straight. See
// releaseExpiredReservations below.
const MAX_DEVICE_SEATS = Number(process.env.MAX_DEVICE_SEATS || 8);
const MAX_CHAT_PEERS = 4;
const RECONNECT_GRACE_MS = 60_000;
const EMPTY_ROOM_TIMEOUT_MS = 10 * 60_000;
const HEARTBEAT_INTERVAL_MS = 15_000;
const CHAT_HISTORY_LIMIT = 100;
// Device seats reclaim themselves via reconnect tokens, but a chat peer
// (a phone browser tab) has no such lifecycle — someone can leave the page
// open long after the movie's over, holding a slot indefinitely. Kicking
// after a fixed session length bounds that without needing any real
// idle-detection.
const CHAT_MAX_SESSION_MS = 30 * 60_000;

// rooms: roomId -> RoomState. seats: up to MAX_DEVICE_SEATS, index 0 is
// always the host (assigned at createRoom, never anyone else), 1..N-1 are
// guests — assigned in join order and then stable across reconnects because
// a returning device presents the reconnectToken it was minted the first
// time, reclaiming its own seat rather than taking whichever is free.
// seat.ws is null while the seat is reserved-but-disconnected.
const rooms = new Map();

function mintToken() {
  return crypto.randomBytes(24).toString('base64url');
}

function mintRoomId() {
  return crypto.randomBytes(6).toString('base64url');
}

// Design spec section 14 "Maximum seats": a client-side cap on rooms a
// device hosts, sent along with createRoom — clamped to MAX_DEVICE_SEATS
// (the relay's own ceiling) rather than trusting the client, since a
// misbehaving/older client could otherwise ask for more seats than this
// deployment is provisioned for.
function clampMaxSeats(requested) {
  const n = Number(requested);
  if (!Number.isFinite(n) || n < 1) return MAX_DEVICE_SEATS;
  return Math.min(Math.floor(n), MAX_DEVICE_SEATS);
}

function createRoomState({ title, thumb, ratingKey, hostName, maxSeats }) {
  const cappedSeats = clampMaxSeats(maxSeats);
  return {
    roomId: mintRoomId(),
    title: String(title || 'Untitled'),
    thumb: typeof thumb === 'string' ? thumb : null,
    ratingKey: typeof ratingKey === 'string' ? ratingKey : null,
    hostName: typeof hostName === 'string' && hostName ? hostName : 'Host',
    maxSeats: cappedSeats,
    seats: new Array(cappedSeats).fill(null),
    chatPeers: new Set(),
    chatHistory: [],
    createdAt: Date.now(),
    // Set the instant every seat is empty, cleared the instant anyone's
    // seated again — see releaseExpiredReservations, which deletes the room
    // once this has stood for EMPTY_ROOM_TIMEOUT_MS. A freshly-created room
    // always has its host in seat 0, so this starts null.
    emptySince: null,
  };
}

// Shared tail of both create and join: claim the first free seat for a
// brand-new peerId. Always succeeds for createRoom (a fresh room's seats
// are all empty); join uses the same claim path once it's confirmed
// peerId isn't already reconnecting into an existing seat (see
// reclaimSeat below).
function assignSeat(ws, room, peerId) {
  const freeIndex = room.seats.findIndex((seat) => seat === null);
  if (freeIndex === -1) return null;
  const reconnectToken = mintToken();
  room.seats[freeIndex] = { peerId, reconnectToken, ws, reservedUntil: null };
  ws.roomId = room.roomId;
  ws.deviceSeatIndex = freeIndex;
  return { seatIndex: freeIndex, reconnectToken };
}

// A device reconnecting to a seat it already holds in this room — same
// peerId, and the token it presents has to match what was minted for that
// seat, so a guessed/reused peerId can't hijack someone else's seat.
function reclaimSeat(ws, room, existingIndex, presentedToken) {
  const seat = room.seats[existingIndex];
  if (seat.reconnectToken !== presentedToken) return null;
  if (seat.ws) {
    // Same device reconnecting while its old socket is still technically
    // open (e.g. a fast app relaunch before the heartbeat noticed) — the
    // new connection wins.
    try { seat.ws.terminate(); } catch { /* already gone */ }
  }
  seat.ws = ws;
  seat.reservedUntil = null;
  ws.roomId = room.roomId;
  ws.deviceSeatIndex = existingIndex;
  return { seatIndex: existingIndex, reconnectToken: seat.reconnectToken };
}

function releaseExpiredReservations() {
  const now = Date.now();
  for (const [roomId, room] of rooms) {
    for (let i = 0; i < room.seats.length; i++) {
      const seat = room.seats[i];
      if (seat && seat.ws === null && seat.reservedUntil !== null && now >= seat.reservedUntil) {
        room.seats[i] = null;
      }
    }
    // A room stays alive indefinitely as long as anyone at all is seated —
    // the host leaving isn't special anymore, only every seat being empty
    // is. Tracks how long it's been fully empty and only deletes it once
    // that's held for EMPTY_ROOM_TIMEOUT_MS straight; regaining an occupant
    // (a reconnect, or someone else joining) within that window clears the
    // clock with no other cleanup needed.
    const occupied = room.seats.some((seat) => seat !== null);
    if (occupied) {
      room.emptySince = null;
    } else if (room.emptySince === null) {
      room.emptySince = now;
    } else if (now - room.emptySince >= EMPTY_ROOM_TIMEOUT_MS) {
      rooms.delete(roomId);
    }
  }
}

function handleCreateRoom(ws, msg) {
  const peerId = typeof msg.peerId === 'string' && msg.peerId ? msg.peerId : crypto.randomUUID();
  const room = createRoomState({
    title: msg.title,
    thumb: msg.thumb,
    ratingKey: msg.ratingKey,
    hostName: msg.hostName,
    maxSeats: msg.maxSeats,
  });
  rooms.set(room.roomId, room);
  const result = assignSeat(ws, room, peerId); // always succeeds: fresh room, all seats empty
  ws.send(JSON.stringify({
    type: 'welcome',
    roomId: room.roomId,
    peerId,
    reconnectToken: result.reconnectToken,
    seatIndex: result.seatIndex,
  }));
}

function handleJoinRoom(ws, msg) {
  releaseExpiredReservations();
  const room = rooms.get(msg.roomId);
  if (!room) {
    // The room ended (host left) between the client listing it via /rooms
    // and pressing Join — distinct from `full` so the client can show a
    // more accurate "that room just ended" message.
    ws.send(JSON.stringify({ type: 'notFound' }));
    ws.close();
    return;
  }

  const peerId = typeof msg.peerId === 'string' && msg.peerId ? msg.peerId : crypto.randomUUID();
  const existingIndex = room.seats.findIndex((seat) => seat && seat.peerId === peerId);
  const result = existingIndex !== -1
    ? reclaimSeat(ws, room, existingIndex, msg.reconnectToken)
    : assignSeat(ws, room, peerId);

  if (!result) {
    ws.send(JSON.stringify({ type: 'full' }));
    ws.close();
    return;
  }
  ws.send(JSON.stringify({
    type: 'welcome',
    roomId: room.roomId,
    peerId,
    reconnectToken: result.reconnectToken,
    seatIndex: result.seatIndex,
  }));
}

function handleChatHello(ws, msg) {
  const room = rooms.get(msg.roomId);
  if (!room) {
    ws.send(JSON.stringify({ type: 'notFound' }));
    ws.close();
    return;
  }
  if (room.chatPeers.size >= MAX_CHAT_PEERS) {
    ws.send(JSON.stringify({ type: 'full' }));
    ws.close();
    return;
  }
  ws.isChatPeer = true;
  ws.roomId = room.roomId;
  ws.chatConnectedAt = Date.now();
  room.chatPeers.add(ws);
  ws.send(JSON.stringify({ type: 'welcome', peerId: crypto.randomUUID(), seatIndex: null }));
  // Replays this room's own history (and only this room's) to a freshly
  // (re)connected phone — covers both "scanned a different room's QR" (gets
  // that room's history, not some other movie's) and "accidentally closed
  // the browser tab" (reconnecting to the same room replays what it missed,
  // for as long as the room itself is still alive).
  ws.send(JSON.stringify({ type: 'chatHistory', messages: room.chatHistory }));
}

function broadcastEvent(sender, payload) {
  const room = rooms.get(sender.roomId);
  if (!room) return;
  if (payload && payload.kind === 'chat') {
    room.chatHistory.push(payload);
    if (room.chatHistory.length > CHAT_HISTORY_LIMIT) room.chatHistory.shift();
  }
  const data = JSON.stringify({ type: 'event', payload });
  for (const seat of room.seats) {
    if (seat && seat.ws && seat.ws !== sender && seat.ws.readyState === WebSocket.OPEN) {
      seat.ws.send(data);
    }
  }
  for (const peer of room.chatPeers) {
    if (peer !== sender && peer.readyState === WebSocket.OPEN) {
      peer.send(data);
    }
  }
}

function releaseConnection(ws) {
  const room = rooms.get(ws.roomId);
  if (!room) return;
  if (ws.isChatPeer) {
    room.chatPeers.delete(ws);
    return;
  }
  if (typeof ws.deviceSeatIndex === 'number') {
    const seat = room.seats[ws.deviceSeatIndex];
    if (seat && seat.ws === ws) {
      seat.ws = null;
      seat.reservedUntil = Date.now() + RECONNECT_GRACE_MS;
    }
  }
}

// A plain WebSocket.Server with no request handler leaves regular HTTP GETs
// (e.g. Render's health check) hanging forever, so the WS server is attached
// to an http.Server that answers those directly. It also now serves the
// phone-facing chat page (see CHAT_PAGE_HTML below) and the room directory
// the Home screen polls.
const server = http.createServer((req, res) => {
  const { pathname } = new URL(req.url, `http://${req.headers.host}`);
  if (pathname === '/chat') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(CHAT_PAGE_HTML);
    return;
  }
  if (pathname === '/rooms') {
    releaseExpiredReservations();
    const list = [];
    for (const room of rooms.values()) {
      const occupants = room.seats.filter(Boolean).length;
      if (occupants === 0) continue; // shouldn't happen (seat 0 is the host) — guard anyway
      list.push({
        roomId: room.roomId,
        title: room.title,
        thumb: room.thumb,
        ratingKey: room.ratingKey,
        hostName: room.hostName,
        occupants,
        maxSeats: room.maxSeats,
      });
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(list));
    return;
  }
  // POST /rooms/:roomId/close — lets a host end their own room on demand
  // (e.g. from Home, well after the app's live socket to it is gone) without
  // needing a live connection: proof of "you're really the host" is the same
  // peerId + reconnectToken a reconnect would present, checked against seat
  // 0. Deliberate close, not the accidental-disconnect path — that one still
  // goes through the normal reconnect grace in releaseConnection below, so
  // losing power/wifi never silently ends a room out from under a guest.
  const closeMatch = req.method === 'POST' && pathname.match(/^\/rooms\/([^/]+)\/close$/);
  if (closeMatch) {
    let body = '';
    req.on('data', (chunk) => { body += chunk; });
    req.on('end', () => {
      let parsed;
      try { parsed = JSON.parse(body); } catch { parsed = null; }
      const room = rooms.get(closeMatch[1]);
      const hostSeat = room && room.seats[0];
      const authorized = hostSeat && parsed &&
        hostSeat.peerId === parsed.peerId && hostSeat.reconnectToken === parsed.reconnectToken;
      if (authorized) {
        // Every occupied seat gets an explicit "closed" frame before its
        // socket closes — not just the host's. Without this, a guest
        // waiting in the Lobby just sees their socket drop, which the
        // client reads as a transient failure and retries into a
        // misleading "Can't reach relay" state instead of the deliberate
        // close it actually was.
        for (const seat of room.seats) {
          if (seat && seat.ws) {
            try {
              seat.ws.send(JSON.stringify({ type: 'closed' }));
              seat.ws.close();
            } catch { /* already gone */ }
          }
        }
        rooms.delete(room.roomId);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ ok: true }));
      } else {
        res.writeHead(404, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ ok: false }));
      }
    });
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
      case 'createRoom':
        handleCreateRoom(ws, msg);
        break;
      case 'joinRoom':
        handleJoinRoom(ws, msg);
        break;
      case 'hello':
        // Only chat (phone) peers still use the old hello envelope — device
        // seating now always goes through createRoom/joinRoom above.
        if (msg.role === 'chat') handleChatHello(ws, msg);
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
// minutes or never fire at all through a hosting provider's proxy. Also
// sweeps expired reservations each tick so a room whose host never
// reconnects gets deleted even if no create/join traffic arrives to trigger
// the lazy sweep in handleJoinRoom/GET /rooms.
const heartbeatInterval = setInterval(() => {
  releaseExpiredReservations();
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
// Reads its relay token and room id straight from the URL the QR code
// encodes (?token=...&room=...) and reconnects with backoff since a phone's
// browser tab can lose its socket (screen lock, backgrounding) far more
// often than the TV.
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
  .msg { display: flex; flex-direction: column; max-width: 75%; }
  .msg.mine { align-self: flex-end; align-items: flex-end; }
  .msg.theirs { align-self: flex-start; align-items: flex-start; }
  .msg .who { font-size: 11px; color: #C7C7D1; margin: 0 4px 2px; }
  .msg .bubble {
    padding: 8px 12px; border-radius: 16px; font-size: 14px;
    overflow-wrap: break-word; white-space: pre-wrap;
  }
  .msg.mine .bubble { background: linear-gradient(135deg, #E795FC, #AD2BD7); color: #0D0D12; border-bottom-right-radius: 4px; }
  .msg.theirs .bubble { background: #2A2A33; color: #F2F2F5; border-bottom-left-radius: 4px; }
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
    const roomId = new URLSearchParams(location.search).get('room');

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

    // Chat history now lives on the relay, scoped to this one room (see
    // server.js's chatHistory/broadcastEvent) — the room sends its buffer
    // back right after 'hello', so there's nothing for this page to persist
    // itself any more. Previously this mirrored history into localStorage,
    // which had no room boundary at all and is exactly why a phone used to
    // see every past movie's messages piled together; the server replacing
    // that also means a browser-close mid-room now recovers correctly
    // (the room's buffer outlives the tab, for as long as the room itself
    // is alive), which localStorage never actually guaranteed either.
    function renderMessage(who, text, isMe) {
      const line = document.createElement('div');
      line.className = 'msg ' + (isMe ? 'mine' : 'theirs');
      if (!isMe) {
        const whoEl = document.createElement('div');
        whoEl.className = 'who';
        whoEl.textContent = who;
        line.appendChild(whoEl);
      }
      const bubble = document.createElement('div');
      bubble.className = 'bubble';
      bubble.textContent = text;
      line.appendChild(bubble);
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
        ws.send(JSON.stringify({ type: 'hello', role: 'chat', roomId }));
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
        if (msg.type === 'notFound') {
          statusEl.textContent = 'This room has ended.';
          return;
        }
        if (msg.type === 'chatHistory') {
          logEl.innerHTML = '';
          for (const m of msg.messages || []) {
            renderMessage(m.username || 'them', m.text || '', (m.username || '') === username);
          }
          return;
        }
        if (msg.type === 'event' && msg.payload && msg.payload.kind === 'chat') {
          renderMessage(msg.payload.username || 'them', msg.payload.text || '', false);
        }
      };
    }
    connect();

    form.addEventListener('submit', (e) => {
      e.preventDefault();
      const text = textInput.value.trim();
      if (!text || !ws || ws.readyState !== WebSocket.OPEN) return;
      ws.send(JSON.stringify({ type: 'event', payload: { kind: 'chat', username, text } }));
      renderMessage(username, text, true);
      textInput.value = '';
    });
  </script>
</body></html>
`;

server.listen(PORT, () => {
  console.log(`Shumtimes relay listening on port ${PORT}`);
});
