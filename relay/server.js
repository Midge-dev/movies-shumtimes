const http = require('http');
const crypto = require('crypto');
const WebSocket = require('ws');

const PORT = process.env.PORT || 8080;
const TOKEN = process.env.RELAY_TOKEN || null;

const MAX_DEVICE_SEATS = Number(process.env.MAX_DEVICE_SEATS || 8);
const MAX_CHAT_PEERS = 4;
const RECONNECT_GRACE_MS = 60_000;
const EMPTY_ROOM_TIMEOUT_MS = 10 * 60_000;
const HEARTBEAT_INTERVAL_MS = 15_000;
const CHAT_HISTORY_LIMIT = 100;
const MAX_ROOMS = Number(process.env.MAX_ROOMS || 200);
const MAX_CONNECTIONS = Number(process.env.MAX_CONNECTIONS || 500);
const MAX_STRING_LEN = 200;
const MAX_CLOSE_BODY_BYTES = 4_096;
const CHAT_MAX_SESSION_MS = 30 * 60_000;

const rooms = new Map();

function truncate(value, max = MAX_STRING_LEN) {
  return typeof value === 'string' ? value.slice(0, max) : value;
}

function mintToken() {
  return crypto.randomBytes(24).toString('base64url');
}

function mintRoomId() {
  return crypto.randomBytes(6).toString('base64url');
}

function isAuthorized(searchParams) {
  return !TOKEN || searchParams.get('token') === TOKEN;
}

function clampMaxSeats(requested) {
  const n = Number(requested);
  if (!Number.isFinite(n) || n < 1) return MAX_DEVICE_SEATS;
  return Math.min(Math.floor(n), MAX_DEVICE_SEATS);
}

function createRoomState({ title, thumb, ratingKey, hostName, maxSeats }) {
  const cappedSeats = clampMaxSeats(maxSeats);
  return {
    roomId: mintRoomId(),
    title: truncate(String(title || 'Untitled')),
    thumb: typeof thumb === 'string' ? truncate(thumb, 2_000) : null,
    ratingKey: typeof ratingKey === 'string' ? truncate(ratingKey) : null,
    hostName: typeof hostName === 'string' && hostName ? truncate(hostName) : 'Host',
    maxSeats: cappedSeats,
    seats: new Array(cappedSeats).fill(null),
    chatPeers: new Set(),
    chatHistory: [],
    createdAt: Date.now(),
    emptySince: null,
  };
}

function assignSeat(ws, room, peerId) {
  const freeIndex = room.seats.findIndex((seat) => seat === null);
  if (freeIndex === -1) return null;
  const reconnectToken = mintToken();
  room.seats[freeIndex] = { peerId, reconnectToken, ws, reservedUntil: null };
  ws.roomId = room.roomId;
  ws.deviceSeatIndex = freeIndex;
  return { seatIndex: freeIndex, reconnectToken };
}

function reclaimSeat(ws, room, existingIndex, presentedToken) {
  const seat = room.seats[existingIndex];
  if (seat.reconnectToken !== presentedToken) return null;
  if (seat.ws) {
    try { seat.ws.terminate(); } catch {}
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
  if (rooms.size >= MAX_ROOMS) {
    ws.send(JSON.stringify({ type: 'full' }));
    return;
  }
  const peerId = typeof msg.peerId === 'string' && msg.peerId ? msg.peerId : crypto.randomUUID();
  const room = createRoomState({
    title: msg.title,
    thumb: msg.thumb,
    ratingKey: msg.ratingKey,
    hostName: msg.hostName,
    maxSeats: msg.maxSeats,
  });
  rooms.set(room.roomId, room);
  const result = assignSeat(ws, room, peerId);
  if (!result) {
    rooms.delete(room.roomId);
    ws.send(JSON.stringify({ type: 'full' }));
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

function handleJoinRoom(ws, msg) {
  releaseExpiredReservations();
  const room = rooms.get(msg.roomId);
  if (!room) {
    ws.send(JSON.stringify({ type: 'notFound' }));
    ws.close();
    return;
  }

  const peerId = typeof msg.peerId === 'string' && msg.peerId ? msg.peerId : crypto.randomUUID();
  const existingIndex = room.seats.findIndex((seat) => seat && seat.peerId === peerId);
  const result = existingIndex !== -1
    ? reclaimSeat(ws, room, existingIndex, msg.reconnectToken) ?? assignSeat(ws, room, peerId)
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
  ws.send(JSON.stringify({ type: 'chatHistory', messages: room.chatHistory }));
}

function broadcastEvent(sender, payload) {
  const room = rooms.get(sender.roomId);
  if (!room) return;
  if (payload && payload.kind === 'chat') {
    payload.username = truncate(payload.username, 40);
    payload.text = truncate(payload.text, 500);
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

const server = http.createServer((req, res) => {
  const { pathname, searchParams } = new URL(req.url, `http://${req.headers.host}`);
  if (pathname === '/chat') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(CHAT_PAGE_HTML);
    return;
  }
  if (pathname === '/rooms') {
    if (!isAuthorized(searchParams)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ok: false }));
      return;
    }
    releaseExpiredReservations();
    const list = [];
    for (const room of rooms.values()) {
      const occupants = room.seats.filter(Boolean).length;
      if (occupants === 0) continue;
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
  const closeMatch = req.method === 'POST' && pathname.match(/^\/rooms\/([^/]+)\/close$/);
  if (closeMatch) {
    if (!isAuthorized(searchParams)) {
      res.writeHead(401, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ ok: false }));
      return;
    }
    let body = '';
    let tooLarge = false;
    req.on('data', (chunk) => {
      if (tooLarge) return;
      body += chunk;
      if (body.length > MAX_CLOSE_BODY_BYTES) {
        tooLarge = true;
        res.writeHead(413, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ ok: false }));
        req.destroy();
      }
    });
    req.on('end', () => {
      if (tooLarge) return;
      let parsed;
      try { parsed = JSON.parse(body); } catch { parsed = null; }
      const room = rooms.get(closeMatch[1]);
      const hostSeat = room && room.seats[0];
      const authorized = hostSeat && parsed &&
        hostSeat.peerId === parsed.peerId && hostSeat.reconnectToken === parsed.reconnectToken;
      if (authorized) {
        for (const seat of room.seats) {
          if (seat && seat.ws) {
            try {
              seat.ws.send(JSON.stringify({ type: 'closed' }));
              seat.ws.close();
            } catch {}
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

const wss = new WebSocket.Server({ noServer: true });

server.on('upgrade', (req, socket, head) => {
  const { searchParams } = new URL(req.url, `http://${req.headers.host}`);

  if (!isAuthorized(searchParams)) {
    socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
    socket.destroy();
    return;
  }
  if (wss.clients.size >= MAX_CONNECTIONS) {
    socket.write('HTTP/1.1 503 Service Unavailable\r\n\r\n');
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
      return;
    }
    if (!msg || typeof msg !== 'object' || typeof msg.type !== 'string') return;
    switch (msg.type) {
      case 'createRoom':
        handleCreateRoom(ws, msg);
        break;
      case 'joinRoom':
        handleJoinRoom(ws, msg);
        break;
      case 'hello':
        if (msg.role === 'chat') handleChatHello(ws, msg);
        break;
      case 'event':
        broadcastEvent(ws, msg.payload);
        break;
    }
  });

  ws.on('close', () => releaseConnection(ws));
});

const heartbeatInterval = setInterval(() => {
  releaseExpiredReservations();
  const now = Date.now();
  for (const ws of wss.clients) {
    if (ws.isChatPeer && now - ws.chatConnectedAt > CHAT_MAX_SESSION_MS) {
      try { ws.send(JSON.stringify({ type: 'kicked', reason: 'session_expired' })); } catch {}
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
