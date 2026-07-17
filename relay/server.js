const http = require('http');
const WebSocket = require('ws');

const PORT = process.env.PORT || 8080;
const TOKEN = process.env.RELAY_TOKEN || null;
const MAX_CLIENTS = 2; // watch-together is always exactly 2 people

// A plain WebSocket.Server with no request handler leaves regular HTTP GETs
// (e.g. Render's health check) hanging forever, so the WS server is attached
// to an http.Server that answers those directly.
const server = http.createServer((req, res) => {
  // TEMPORARY diagnostic route — reports only whether RELAY_TOKEN made it
  // into the process env, never the value itself. Remove once the token
  // gate is confirmed working.
  if (req.url === '/debug-token') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ tokenConfigured: Boolean(TOKEN), tokenLength: TOKEN ? TOKEN.length : 0 }));
    return;
  }
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('shumtimes relay ok\n');
});

const wss = new WebSocket.Server({ server });

wss.on('connection', (ws, req) => {
  const { searchParams } = new URL(req.url, `http://${req.headers.host}`);

  // TEMPORARY diagnostic — echoes what the server actually saw for this
  // upgrade request, to rule out a proxy stripping the query string before
  // it reaches the app. Remove once the token gate is confirmed working.
  ws.send(JSON.stringify({
    debugRawUrl: req.url,
    debugReceivedToken: searchParams.get('token'),
    debugTokenConfigured: Boolean(TOKEN),
    debugMatches: searchParams.get('token') === TOKEN,
  }));

  if (TOKEN && searchParams.get('token') !== TOKEN) {
    // TEMPORARY: testing whether a standard close code (vs our custom 4001)
    // propagates through the proxy in front of this Render service.
    ws.close(1008, 'invalid token');
    return;
  }

  if (wss.clients.size > MAX_CLIENTS) {
    ws.close(4002, 'room full');
    return;
  }

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
