package app.droidle.plugin.auth

import app.droidle.plugin.state.StateManager
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection
import java.net.URI

/**
 * Handles droidle:// custom URI scheme sent by the web app after OAuth.
 *
 * Flow:
 *   browser -> /login?source=plugin -> GitHub OAuth -> /auth/callback -> /plugin-success
 *   /plugin-success -> window.location.href = "droidle://auth/callback?token=ACCESS_TOKEN"
 *   macOS/Windows routes this URI here (registered via Info.plist / registry)
 *   -> saveAuthToken(token) -> refresh toolbar
 *
 * Registration:
 *   macOS: plugin bundles its own Info.plist CFBundleURLSchemes entry,
 *          OR user adds it via "Register IDE protocol handler" in settings.
 *   Windows: registry key: HKCU\Software\Classes\droidle\...
 *
 * For IntelliJ-based IDEs the recommended way is to register via
 * com.intellij.openapi.application.CustomProtocolHandler EP — but that EP
 * requires the host app to restart. As a simpler cross-platform solution we
 * use a local HTTP server on a fixed port (62330) that the web page POSTs to.
 *
 * This class provides both paths:
 *   1. processDeepLink(uri)  — called if OS deep link arrives
 *   2. A lightweight embedded HTTP listener on port 62330 for the web fallback
 */
object DroidleAuthProtocolHandler {

    private val LOG = Logger.getInstance(DroidleAuthProtocolHandler::class.java)
    const val CALLBACK_PORT = 62330

    /** Called by DroidleLocalAuthServer when the web page posts a token. */
    fun onTokenReceived(token: String, username: String?) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                StateManager.getInstance().saveAuthToken(token)
                if (username != null) {
                    StateManager.getInstance().saveUsername(username)
                }
                ApplicationManager.getApplication().invokeLater {
                    showSuccessNotification(username)
                    // Broadcast so toolbar can refresh
                    ApplicationManager.getApplication().messageBus
                        .syncPublisher(DroidleAuthListener.TOPIC)
                        .onAuthChanged()
                }
            } catch (e: Exception) {
                LOG.error("Failed to save auth token", e)
            }
        }
    }

    private fun showSuccessNotification(username: String?) {
        val display = if (username != null) "@$username" else "GitHub hesabın"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Droidle")
            ?.createNotification(
                "Droidle — Giriş Başarılı",
                "$display ile giriş yapıldı. Liderlik tablosuna katıldın! 🎉",
                NotificationType.INFORMATION
            )
            ?.notify(null)
    }
}
