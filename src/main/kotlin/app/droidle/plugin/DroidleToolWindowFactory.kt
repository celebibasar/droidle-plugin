package app.droidle.plugin

import app.droidle.plugin.settings.DroidleSettingsState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class DroidleToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = if (JBCefApp.isSupported()) {
            buildWebPanel()
        } else {
            buildFallbackPanel()
        }

        val content = ContentFactory.getInstance()
            .createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    private fun buildWebPanel(): JPanel {
        val settings = DroidleSettingsState.getInstance()
        val baseUrl = settings.apiBaseUrl   // https://droidle.vercel.app
        val bypass  = settings.vercelBypassSecret

        // Plugin parametresi olarak source=plugin ekle — site bunu okuyabilir
        val url = "$baseUrl?source=plugin"

        val browser = JBCefBrowserBuilder()
            .setUrl(url)
            .setEnableOpenDevToolsMenuItem(false)
            .build()

        // Vercel bypass cookie'yi js ile inject et (protection aktifse)
        if (bypass.isNotBlank()) {
            browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(b: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                    if (frame.isMain) {
                        // Bypass header cookie olarak set et
                        b.executeJavaScript(
                            "document.cookie = 'x-vercel-protection-bypass=$bypass; path=/';",
                            b.url, 0
                        )
                    }
                }
            }, browser.cefBrowser)
        }

        return JPanel(BorderLayout()).apply {
            add(browser.component, BorderLayout.CENTER)
        }
    }

    private fun buildFallbackPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(JLabel(
                "<html><center>" +
                "<b>JCEF bu ortamda desteklenmiyor.</b><br><br>" +
                "Android Studio'yu <code>--enable-jcef</code> bayrağı ile başlatmayı dene<br>" +
                "ya da Help → Find Action → 'Registry' → <code>ide.browser.jcef.enabled</code> aç." +
                "</center></html>",
                SwingConstants.CENTER
            ), BorderLayout.CENTER)
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
