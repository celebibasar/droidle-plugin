package app.droidle.plugin.ui.components

import app.droidle.plugin.state.AppLanguage
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities

object AvatarImageCache {
    private val memoryCache = ConcurrentHashMap<String, Image>()

    fun getOrLoad(url: String, onLoaded: (Image) -> Unit): Image? {
        val cached = memoryCache[url]
        if (cached != null) return cached

        try {
            ApplicationManager.getApplication()?.executeOnPooledThread {
                try {
                    val img = ImageIO.read(URL(url))
                    if (img != null) {
                        memoryCache[url] = img
                        SwingUtilities.invokeLater { onLoaded(img) }
                    }
                } catch (_: Exception) {
                    // Ignore avatar image network failure
                }
            }
        } catch (_: Throwable) {
            // Ignore if ApplicationManager is not initialized
        }
        return null
    }
}

/**
 * Modern circular user avatar with fallback initial badge and profile/logout popup.
 */
class CircularAvatar(
    var avatarUrl: String?,
    var username: String?,
    var lang: AppLanguage = AppLanguage.TR,
    private val onLogout: () -> Unit
) : JComponent() {

    private val sizePx = JBUI.scale(26)
    private var isHovered = false

    init {
        preferredSize = Dimension(sizePx, sizePx)
        minimumSize   = preferredSize
        maximumSize   = preferredSize
        cursor        = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isOpaque      = false

        updateToolTip()

        if (!avatarUrl.isNullOrBlank()) {
            AvatarImageCache.getOrLoad(avatarUrl!!) { repaint() }
        }

        val showAvatarMenu = { e: MouseEvent? ->
            showMenu(e?.component ?: this@CircularAvatar, e?.x ?: 0, e?.y ?: sizePx)
        }

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                isHovered = true
                repaint()
            }
            override fun mouseExited(e: MouseEvent?) {
                isHovered = false
                repaint()
            }
            override fun mouseClicked(e: MouseEvent?) {
                showAvatarMenu(e)
            }
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showAvatarMenu(e)
                }
            }
            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showAvatarMenu(e)
                }
            }
        })
    }

    internal fun updateToolTip() {
        val isEn = lang == AppLanguage.EN
        val cleanUser = username?.trim()?.takeIf { it.isNotEmpty() }
        toolTipText = if (isEn) {
            cleanUser?.let { "Profile (@$it)" } ?: "Profile"
        } else {
            cleanUser?.let { "Profil (@$it)" } ?: "Profil"
        }
    }

    fun updateProfile(newAvatarUrl: String?, newUsername: String?, newLang: AppLanguage = lang) {
        avatarUrl = newAvatarUrl
        username = newUsername
        lang = newLang
        updateToolTip()
        if (!avatarUrl.isNullOrBlank()) {
            AvatarImageCache.getOrLoad(avatarUrl!!) { repaint() }
        }
        repaint()
    }

    fun getLeaderboardText(language: AppLanguage = lang): String =
        if (language == AppLanguage.EN) "Leaderboard" else "Liderlik Tablosu"

    fun getLogoutText(language: AppLanguage = lang): String =
        if (language == AppLanguage.EN) "Sign Out" else "Çıkış Yap"

    fun getProfileFallbackText(language: AppLanguage = lang): String =
        if (language == AppLanguage.EN) "My Profile" else "Profilim"

    private fun showMenu(invoker: Component, x: Int, y: Int) {
        val menu = createPopupMenu()
        menu.show(invoker, x, y)
    }

    internal fun createPopupMenu(): JPopupMenu {
        val menu = JPopupMenu()

        val cleanUser = username?.trim()?.takeIf { it.isNotEmpty() }
        val userItem = JMenuItem(cleanUser?.let { "@$it" } ?: getProfileFallbackText(lang)).apply {
            isEnabled = false
            font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
        }
        menu.add(userItem)
        menu.addSeparator()

        val leaderboardItem = JMenuItem(getLeaderboardText(lang)).apply {
            font = JBUI.Fonts.smallFont()
            addActionListener { BrowserUtil.browse("https://droidle.dev/leaderboard") }
        }
        menu.add(leaderboardItem)

        val logoutItem = JMenuItem(getLogoutText(lang)).apply {
            font = JBUI.Fonts.smallFont()
            addActionListener { onLogout() }
        }
        menu.add(logoutItem)

        return menu
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

            val img = avatarUrl?.let { AvatarImageCache.getOrLoad(it) { repaint() } }

            val circle = Ellipse2D.Float(1f, 1f, (width - 2).toFloat(), (height - 2).toFloat())

            if (img != null) {
                val clip = g2.clip
                g2.clip(circle)
                g2.drawImage(img, 0, 0, width, height, null)
                g2.clip = clip

                // Subtle border
                g2.color = if (isHovered) Color(0x10, 0xB9, 0x81) else JBColor(Color(0xE4E4E7), Color(0x3F3F46))
                g2.stroke = BasicStroke(JBUI.scale(1).toFloat())
                g2.draw(circle)
            } else {
                // Fallback initial
                g2.color = JBColor(Color(0xECFDF5), Color(0x134E32))
                g2.fill(circle)

                g2.color = if (isHovered) Color(0x10, 0xB9, 0x81) else JBColor(Color(0x059669), Color(0x34D399))
                g2.stroke = BasicStroke(JBUI.scale(1).toFloat())
                g2.draw(circle)

                val initial = username?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "D"
                g2.font = JBUI.Fonts.label(11f).deriveFont(Font.BOLD)
                val fm = g2.fontMetrics
                val tx = (width - fm.stringWidth(initial)) / 2
                val ty = (height + fm.ascent - fm.descent) / 2
                g2.drawString(initial, tx, ty)
            }
        } finally {
            g2.dispose()
        }
    }
}
