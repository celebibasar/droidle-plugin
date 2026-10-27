package app.droidle.plugin.auth

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * Tiny HTTP server on localhost:62330 that receives the auth token from the
 * web page (/plugin-success) via a POST request, avoiding the need to register
 * a custom URI scheme at OS level.
 *
 * The web page does:
 *   fetch('http://localhost:62330/auth', { method: 'POST', body: JSON.stringify({ token, username }) })
 *
 * If the IDE isn't running / port is in use, the request silently fails and
 * the user sees the "IDE not open" fallback message on /plugin-success.
 *
 * Lifecycle: started on IDE startup, stopped on IDE shutdown.
 */
@Service(Service.Level.APP)
class DroidleLocalAuthServer : AutoCloseable {

    private val LOG = Logger.getInstance(DroidleLocalAuthServer::class.java)
    private var server: HttpServer? = null

    fun start() {
        if (server != null) return
        try {
            val s = HttpServer.create(InetSocketAddress("127.0.0.1", DroidleAuthProtocolHandler.CALLBACK_PORT), 0)
            s.createContext("/auth") { exchange ->
                // CORS — allow the Vercel app to POST here
                exchange.responseHeaders.add("Access-Control-Allow-Origin", "https://droidle.vercel.app")
                exchange.responseHeaders.add("Access-Control-Allow-Methods", "POST, OPTIONS")
                exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type")

                if (exchange.requestMethod == "OPTIONS") {
                    exchange.sendResponseHeaders(204, -1)
                    exchange.close()
                    return@createContext
                }

                if (exchange.requestMethod != "POST") {
                    exchange.sendResponseHeaders(405, -1)
                    exchange.close()
                    return@createContext
                }

                try {
                    val body = exchange.requestBody.bufferedReader().readText()
                    // Simple JSON parse without a dependency
                    val token    = Regex(""""token"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
                    val username = Regex(""""username"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)

                    if (token != null) {
                        DroidleAuthProtocolHandler.onTokenReceived(token, username)
                        val response = """{"ok":true}"""
                        exchange.responseHeaders.add("Content-Type", "application/json")
                        exchange.sendResponseHeaders(200, response.length.toLong())
                        exchange.responseBody.use { it.write(response.toByteArray()) }
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
            s.executor = null
            s.start()
            server = s
            LOG.info("Droidle auth server started on port ${DroidleAuthProtocolHandler.CALLBACK_PORT}")
        } catch (e: Exception) {
            LOG.warn("Could not start Droidle auth server (port may be in use)", e)
        }
    }

    override fun close() {
        server?.stop(0)
        server = null
    }

    companion object {
        fun getInstance(): DroidleLocalAuthServer = service()
    }
}
