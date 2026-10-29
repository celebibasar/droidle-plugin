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
class CompactHeader(
    private val onLanguageToggle: () -> Unit,
    private val onLoginClick: () -> Unit,
    private val onLogoutClick: () -> Unit
) : JPanel(BorderLayout()) {

    private val streakPill = StreakPill()
    private val langButton: JButton
    private val loginButton: JButton
    private val avatar: CircularAvatar

    init {
        isOpaque = false
        border = CompoundBorder(
            BorderFactory.createMatteBorder(
                0, 0, 1, 0,
                JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
            ),
            JBUI.Borders.empty(JBUI.scale(6), JBUI.scale(10))
        )

        // ── Left: Logo + Dot ──────────────────────────────────────────────────
        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(2), 0)).apply {
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
        val state = StateManager.getInstance()

        langButton = JButton(state.language.label).apply {
            toolTipText = "Dil Değiştir / Switch Language"
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

        loginButton = JButton("Giriş", AllIcons.Vcs.Vendors.Github).apply {
            toolTipText = "GitHub ile giriş yap"
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            font = JBUI.Fonts.smallFont()
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            foreground = JBColor(Color(0x18181B), Color(0xF4F4F5))
            addActionListener { onLoginClick() }
        }

        avatar = CircularAvatar(
            avatarUrl = state.getAvatarUrl(),
            username = state.getUsername(),
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
        val state = StateManager.getInstance()
        val loggedIn = state.isLoggedIn()

        streakPill.setStreak(state.activeStreak)
        langButton.text = state.language.label

        loginButton.isVisible = !loggedIn
        avatar.isVisible = loggedIn
        if (loggedIn) {
            avatar.updateProfile(state.getAvatarUrl(), state.getUsername())
        }

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
    private class StreakPill : JComponent() {
        private var streak: Int = 0

        init {
            isOpaque = false
            updateSize()
        }

        fun setStreak(count: Int) {
            streak = count
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
