package app.droidle.plugin.auth

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Tiny HTTP server on localhost:62330 (IPv4 127.0.0.1 + IPv6 ::1) that receives
 * the auth token from the web page (/plugin-success) via POST or GET.
 */
@Service(Service.Level.APP)
class DroidleLocalAuthServer : Disposable, AutoCloseable {

    private val LOG = Logger.getInstance(DroidleLocalAuthServer::class.java)
    private var serverV4: HttpServer? = null
    private var serverV6: HttpServer? = null
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun start() {
        if (serverV4 != null || serverV6 != null) return

        serverV4 = bindLoopback("127.0.0.1")
        serverV6 = bindLoopback("::1")
    }

    private fun bindLoopback(host: String): HttpServer? {
        return try {
            val s = HttpServer.create(InetSocketAddress(host, DroidleAuthProtocolHandler.CALLBACK_PORT), 0)
            s.createContext("/auth") { exchange -> handleAuthExchange(exchange) }
            s.executor = null
            s.start()
            LOG.info("Droidle auth server started on $host:${DroidleAuthProtocolHandler.CALLBACK_PORT}")
            s
        } catch (e: Exception) {
            LOG.warn("Could not start Droidle auth server on $host:${DroidleAuthProtocolHandler.CALLBACK_PORT} (port may be in use)", e)
            null
        }
    }

    private fun handleAuthExchange(exchange: HttpExchange) {
        // CORS + Private Network Access (required by Chrome/Brave/Edge when https://droidle.dev calls http://localhost)
        val origin = exchange.requestHeaders.getFirst("Origin")
        val effectiveOrigin = if (!origin.isNullOrBlank() &&
            (origin.endsWith("droidle.dev") || origin.endsWith(".vercel.app") || origin.startsWith("http://localhost"))
        ) {
            origin
        } else {
            "https://droidle.dev"
        }

        exchange.responseHeaders.add("Access-Control-Allow-Origin", effectiveOrigin)
        exchange.responseHeaders.add("Vary", "Origin")
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept")
        exchange.responseHeaders.add("Access-Control-Allow-Private-Network", "true")

        if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            return
        }

        if (!exchange.requestMethod.equals("POST", ignoreCase = true) &&
            !exchange.requestMethod.equals("GET", ignoreCase = true)
        ) {
            exchange.sendResponseHeaders(405, -1)
            exchange.close()
            return
        }

        try {
            var token: String? = null
            var username: String? = null
            var userId: String? = null
            var avatarUrl: String? = null

            if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
                val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()
                val obj = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                if (obj != null) {
                    token     = (obj["token"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
                    username  = (obj["username"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
                    userId    = (obj["userId"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
                    avatarUrl = (obj["avatarUrl"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
                } else {
                    token     = Regex(""""token"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                    username  = Regex(""""username"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                    userId    = Regex(""""userId"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                    avatarUrl = Regex(""""avatarUrl"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                }
            } else {
                val query = exchange.requestURI.rawQuery.orEmpty()
                val params = query.split("&").mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx > 0) {
                        val k = URLDecoder.decode(part.substring(0, idx), StandardCharsets.UTF_8)
                        val v = URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8)
                        k to v
                    } else null
                }.toMap()
                token     = params["token"]?.takeIf { it.isNotBlank() }
                username  = params["username"]?.takeIf { it.isNotBlank() }
                userId    = params["userId"]?.takeIf { it.isNotBlank() }
                avatarUrl = params["avatarUrl"]?.takeIf { it.isNotBlank() }
            }

            if (!token.isNullOrBlank()) {
                DroidleAuthProtocolHandler.onTokenReceived(
                    token = token,
                    username = username,
                    userId = userId,
                    avatarUrl = avatarUrl
                )
                val response = """{"ok":true}"""
                val bytes = response.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } else {
                exchange.sendResponseHeaders(400, -1)
            }
        } catch (e: Exception) {
            LOG.warn("Auth token parse error", e)
            exchange.sendResponseHeaders(500, -1)
        } finally {
            exchange.close()
        }
    }

    override fun dispose() {
        close()
    }

    @Synchronized
    override fun close() {
        runCatching { serverV4?.stop(0) }
        runCatching { serverV6?.stop(0) }
        serverV4 = null
        serverV6 = null
    }

    companion object {
        fun getInstance(): DroidleLocalAuthServer = service()
    }
}
