package com.moviesshumtimes.tv.data.pairing

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

// A tiny, hand-rolled, LAN-only HTTP server — the whole surface needed is
// exactly two routes (serve a form, accept its POST), so a real server
// dependency would be overkill. Lets a phone on the same Wi-Fi "paste" the
// long relay URL into this TV's Settings screen without typing it on a
// remote — the closest local-only analog to Plex's own PIN-link flow.
class PairingServer(private val onSubmitted: (String) -> Unit) {
    // A per-session random path segment, not just "/" — avoids a stray
    // request from something else on the LAN (or a port-scanner) landing on
    // the form by accident.
    private val token: String = (1..6).map { ('0'..'9').random() }.joinToString("")
    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

    // Returns the URL to show/QR-encode, or null if no LAN IPv4 address
    // could be found (e.g. TV isn't actually connected to a network).
    fun start(): String? {
        val ip = localIpv4Address() ?: return null
        val socket = ServerSocket(0)
        serverSocket = socket
        running = true
        thread(name = "pairing-server", isDaemon = true) {
            while (running) {
                val client = try {
                    socket.accept()
                } catch (e: Exception) {
                    break
                }
                thread(isDaemon = true) { runCatching { handle(client) } }
            }
        }
        return "http://$ip:${socket.localPort}/$token"
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrElse(0) { "" }
            val path = parts.getOrElse(1) { "/" }

            var contentLength = 0
            while (true) {
                val header = reader.readLine() ?: break
                if (header.isEmpty()) break
                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val output = socket.getOutputStream()
            when {
                method == "GET" && path == "/$token" -> writeHtml(output, formPage())
                method == "POST" && path == "/$token/submit" -> {
                    val body = readBody(reader, contentLength)
                    val value = parseFormUrl(body)
                    if (value.isNullOrBlank()) {
                        writeHtml(output, formPage(error = "Paste a URL first"))
                    } else {
                        writeHtml(output, successPage())
                        onSubmitted(value)
                    }
                }
                else -> writeText(output, 404, "Not found")
            }
        }
    }

    private fun readBody(reader: BufferedReader, contentLength: Int): String {
        val buffer = CharArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val n = reader.read(buffer, read, contentLength - read)
            if (n <= 0) break
            read += n
        }
        return String(buffer, 0, read)
    }

    private fun parseFormUrl(body: String): String? =
        body.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.getOrNull(0) == "url" }
            ?.getOrNull(1)
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?.trim()

    private fun formPage(error: String? = null): String {
        val errorHtml = error?.let { "<p class=\"error\">${it}</p>" } ?: ""
        return """
            <!doctype html>
            <html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Movies Shumtimes — Pair</title>
            <style>$PAIRING_PAGE_CSS</style>
            </head><body>
              <div class="card">
                <h1>Movies Shumtimes</h1>
                <p>Paste the relay URL below — it'll appear in Settings on your TV.</p>
                $errorHtml
                <form method="POST" action="/$token/submit">
                  <input type="text" name="url" placeholder="wss://your-relay-url?token=..." autofocus autocomplete="off">
                  <button type="submit">Send to TV</button>
                </form>
              </div>
            </body></html>
        """.trimIndent()
    }

    private fun successPage(): String = """
        <!doctype html>
        <html><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Movies Shumtimes — Pair</title>
        <style>$PAIRING_PAGE_CSS</style>
        </head><body>
          <div class="card">
            <h1>Sent ✓</h1>
            <p>Check your TV — the relay URL should already be filled in. You can close this tab.</p>
          </div>
        </body></html>
    """.trimIndent()

    private fun localIpv4Address(): String? =
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()
            ?.hostAddress
}

// Dark background, two-tone NeonPurple accents — matches AppColors.kt so
// the pairing page reads as part of the same app even though it's served
// from a plain HTTP page, not Compose.
private const val PAIRING_PAGE_CSS = """
    :root { color-scheme: dark; }
    * { box-sizing: border-box; }
    body {
      margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
      background: #0D0D12; color: #F2F2F5; font-family: -apple-system, system-ui, sans-serif;
      padding: 24px;
    }
    .card {
      width: 100%; max-width: 420px; background: #17171D; border-radius: 16px; padding: 32px;
      border: 2px solid transparent;
      background-image: linear-gradient(#17171D, #17171D), linear-gradient(135deg, #E795FC, #AD2BD7);
      background-origin: border-box; background-clip: padding-box, border-box;
    }
    h1 { font-size: 20px; margin: 0 0 8px; }
    p { color: #C7C7D1; font-size: 14px; margin: 0 0 20px; line-height: 1.5; }
    input[type=text] {
      width: 100%; padding: 14px; border-radius: 10px; border: 1px solid #2A2A33;
      background: #2A2A33; color: #F2F2F5; font-size: 16px; margin-bottom: 16px;
    }
    input[type=text]:focus { outline: none; border-color: #AD2BD7; }
    button {
      width: 100%; padding: 14px; border-radius: 10px; border: none; font-size: 16px; font-weight: 600;
      color: #0D0D12; cursor: pointer;
      background: linear-gradient(135deg, #E795FC, #AD2BD7);
    }
    .error { color: #FF8A8A; font-size: 13px; margin: -8px 0 16px; }
"""

private fun writeHtml(output: OutputStream, html: String) {
    val bytes = html.toByteArray(Charsets.UTF_8)
    output.write(
        "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n",
    )
    output.write(bytes)
    output.flush()
}

private fun writeText(output: OutputStream, code: Int, text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    output.write(
        "HTTP/1.1 $code\r\nContent-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n",
    )
    output.write(bytes)
    output.flush()
}

private fun OutputStream.write(s: String) = write(s.toByteArray(Charsets.UTF_8))
