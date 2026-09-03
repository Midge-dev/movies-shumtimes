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

class PairingServer(
    private val prefillNickname: String = "",
    private val prefillUrl: String = "",
    private val onSubmitted: (nickname: String, url: String) -> Unit,
) {
    private val token: String = (1..6).map { ('0'..'9').random() }.joinToString("")
    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false

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
                    val fields = parseFormFields(body)
                    val url = fields["url"]
                    if (url.isNullOrBlank()) {
                        writeHtml(output, formPage(error = "Paste a URL first"))
                    } else {
                        val nickname = fields["nickname"]?.takeIf { it.isNotBlank() } ?: "My relay"
                        writeHtml(output, successPage())
                        onSubmitted(nickname, url)
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

    private fun parseFormFields(body: String): Map<String, String> =
        body.split("&")
            .map { it.split("=", limit = 2) }
            .filter { it.size == 2 }
            .associate { it[0] to URLDecoder.decode(it[1], "UTF-8").trim() }

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
                <p>Name this relay and paste its URL — both will appear on your TV.</p>
                $errorHtml
                <form method="POST" action="/$token/submit">
                  <input type="text" name="nickname" placeholder="Nickname (e.g. Sean's relay)" value="${escapeHtmlAttr(prefillNickname)}" autofocus autocomplete="off">
                  <input type="text" name="url" placeholder="wss://your-relay-url?token=..." value="${escapeHtmlAttr(prefillUrl)}" autocomplete="off">
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
            <p>Check your TV — the relay should already be filled in. You can close this tab.</p>
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

private fun escapeHtmlAttr(s: String): String =
    s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
