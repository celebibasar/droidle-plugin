package app.droidle.plugin.ui.components

import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.state.StateManager
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.CompoundBorder

/**
 * Modern Compact Header adhering to JetBrains New UI / Linear aesthetic.
 *
 * Left: Minimalist `droidle.` logo + glowing emerald dot.
 * Right: Pill-shaped 🔥 Streak badge, TR | EN language toggle, and circular GitHub avatar.
 */
open class CompactHeader(
    private val onLanguageToggle: () -> Unit,
    private val onLoginClick: () -> Unit,
    private val onLogoutClick: () -> Unit
) : JPanel(BorderLayout()) {

    var overrideLanguage: AppLanguage? = null
    var overrideLoggedIn: Boolean? = null
    var overrideUsername: String? = null
    var overrideAvatarUrl: String? = null
    var overrideStreak: Int? = null

    internal val streakPill = StreakPill()
    internal val langButton: JButton
    internal val loginButton: JButton
    internal val avatar: CircularAvatar
    internal val leftPanel: JPanel

    init {
        isOpaque = false
        border = CompoundBorder(
            BorderFactory.createMatteBorder(
                0, 0, 1, 0,
                JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
            ),
            JBUI.Borders.empty(JBUI.scale(6), JBUI.scale(10))
        )

        val state = try { StateManager.getInstance() } catch (_: Throwable) { null }
        val initialLang = overrideLanguage ?: state?.language ?: AppLanguage.TR

        // ── Left: Logo + Dot ──────────────────────────────────────────────────
        leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0)).apply {
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    BrowserUtil.browse("https://droidle.vercel.app")
                }
            })

            val logoText = JBLabel("droidle").apply {
                font = JBUI.Fonts.label(13f).deriveFont(Font.BOLD)
                foreground = JBUI.CurrentTheme.Label.foreground()
            }
            val dot = GlowingEmeraldDot()

            add(logoText)
            add(dot)
        }

        // ── Right: Streak + Lang + Avatar / Login ─────────────────────────────
        langButton = JButton(initialLang.label).apply {
            toolTipText = if (initialLang == AppLanguage.EN) "Switch Language" else "Dil Değiştir"
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = JBColor(Color(0x71717A), Color(0xA1A1AA))
            preferredSize = Dimension(JBUI.scale(32), JBUI.scale(24))
            addActionListener { onLanguageToggle() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?) {
                    foreground = JBColor(Color(0x18181B), Color(0xF4F4F5))
                }
                override fun mouseExited(e: MouseEvent?) {
                    foreground = JBColor(Color(0x71717A), Color(0xA1A1AA))
                }
            })
        }

        loginButton = JButton(if (initialLang == AppLanguage.EN) "Sign In" else "Giriş Yap", AllIcons.Vcs.Vendors.Github).apply {
            toolTipText = if (initialLang == AppLanguage.EN) "Sign in with GitHub" else "GitHub ile giriş yap"
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            font = JBUI.Fonts.smallFont()
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = JBColor(Color(0x18181B), Color(0xF4F4F5))
            addActionListener { onLoginClick() }
        }

        avatar = CircularAvatar(
            avatarUrl = overrideAvatarUrl ?: state?.getAvatarUrl(),
            username = overrideUsername ?: state?.getUsername(),
            lang = initialLang,
            onLogout = onLogoutClick
        )

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
            isOpaque = false
            add(streakPill)
            add(langButton)
            add(loginButton)
            add(avatar)
        }

        add(leftPanel, BorderLayout.WEST)
        add(rightPanel, BorderLayout.EAST)

        refresh()
    }

    fun refresh() {
        val state = try { StateManager.getInstance() } catch (_: Throwable) { null }
        val currentLang = overrideLanguage ?: state?.language ?: AppLanguage.TR
        val loggedIn = overrideLoggedIn ?: (state?.isLoggedIn() ?: false)
        val currentStreak = overrideStreak ?: (state?.activeStreak ?: 0)
        val currentUsername = overrideUsername ?: state?.getUsername()
        val currentAvatarUrl = overrideAvatarUrl ?: state?.getAvatarUrl()

        streakPill.setStreak(currentStreak, currentLang)
        langButton.text = currentLang.label
        langButton.toolTipText = if (currentLang == AppLanguage.EN) "Switch Language" else "Dil Değiştir"

        loginButton.text = if (currentLang == AppLanguage.EN) "Sign In" else "Giriş Yap"
        loginButton.toolTipText = if (currentLang == AppLanguage.EN) "Sign in with GitHub" else "GitHub ile giriş yap"
        loginButton.isVisible = !loggedIn

        avatar.isVisible = loggedIn
        avatar.updateProfile(currentAvatarUrl, currentUsername, currentLang)

        leftPanel.toolTipText = if (currentLang == AppLanguage.EN) "Open droidle web app" else "droidle web uygulamasını aç"

        revalidate()
        repaint()
    }

    /**
     * Glowing emerald dot next to droidle logo.
     */
    private class GlowingEmeraldDot : JComponent() {
        init {
            val s = JBUI.scale(8)
            preferredSize = Dimension(s, s)
            minimumSize   = preferredSize
            maximumSize   = preferredSize
            isOpaque      = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val dotSize = JBUI.scale(5)
                val x = (width - dotSize) / 2
                val y = (height - dotSize) / 2 + JBUI.scale(1)

                // Emerald core
                g2.color = Color(0x10, 0xB9, 0x81)
                g2.fillOval(x, y, dotSize, dotSize)
            } finally {
                g2.dispose()
            }
        }
    }

    /**
     * Pill-shaped streak badge: 🔥 3
     */
    internal class StreakPill : JComponent() {
        private var streak: Int = 0

        init {
            isOpaque = false
            updateSize()
        }

        fun setStreak(count: Int, lang: AppLanguage = AppLanguage.TR) {
            streak = count
            toolTipText = if (lang == AppLanguage.EN) {
                if (count == 1) "1 day streak" else "$count days streak"
            } else {
                "$count günlük seri"
            }
            updateSize()
            repaint()
        }

        private fun labelText() = "🔥 $streak"

        private fun updateSize() {
            val fm = getFontMetrics(JBUI.Fonts.smallFont().deriveFont(Font.BOLD))
            val text = labelText()
            val w = fm.stringWidth(text) + JBUI.scale(14)
            val h = JBUI.scale(22)
            preferredSize = Dimension(w, h)
            minimumSize   = preferredSize
            maximumSize   = preferredSize
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

                val amber = Color(0xF5, 0x9E, 0x0B)
                val arc = height

                // Background pill
                g2.color = Color(amber.red, amber.green, amber.blue, 24)
                g2.fillRoundRect(0, 0, width, height, arc, arc)

                // 1px subtle amber border
                g2.color = Color(amber.red, amber.green, amber.blue, 120)
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)

                // Text
                g2.color = amber
                g2.font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
                val fm = g2.fontMetrics
                val text = labelText()
                val tx = (width - fm.stringWidth(text)) / 2
                val ty = (height + fm.ascent - fm.descent) / 2
                g2.drawString(text, tx, ty)
            } finally {
                g2.dispose()
            }
        }
    }
}
