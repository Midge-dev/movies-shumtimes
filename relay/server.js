const http = require('http');
const WebSocket = require('ws');

const PORT = process.env.PORT || 8080;
const TOKEN = process.env.RELAY_TOKEN || null;
const MAX_CLIENTS = 2; // watch-together is always exactly 2 people

// A plain WebSocket.Server with no request handler leaves regular HTTP GETs
// (e.g. Render's health check) hanging forever, so the WS server is attached
// to an http.Server that answers those directly.
const server = http.createServer((req, res) => {
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

  if (wss.clients.size >= MAX_CLIENTS) {
    socket.write('HTTP/1.1 403 Forbidden\r\n\r\n');
    socket.destroy();
    return;
  }

  wss.handleUpgrade(req, socket, head, (ws) => {
    wss.emit('connection', ws, req);
  });
});

wss.on('connection', (ws) => {
  // Single implicit room: whatever one client sends (play/pause/seek +
  // position + timestamp) gets rebroadcast verbatim to the other client.
  ws.on('message', (data) => {
    for (const client of wss.clients) {
      if (client !== ws && client.readyState === WebSocket.OPEN) {
        client.send(data.toString());
      }
    }
  });
});

server.listen(PORT, () => {
  console.log(`Shumtimes relay listening on port ${PORT}`);
});
