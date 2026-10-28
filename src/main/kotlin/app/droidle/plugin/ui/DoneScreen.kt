package app.droidle.plugin.ui

import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.state.StateManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.GeneralPath
import javax.swing.JButton
import javax.swing.JPanel

/**
 * "Already completed today" screen.
 *
 * Shows today's session result (passed in from onQuizComplete or from server sync).
 * Falls back to lifetime stats only when session data is not available.
 *
 * Mirrors the web "completed today" state in src/app/page.tsx.
 */
class DoneScreen(
    /** Today's correct count — from quiz session or server sync. null = unknown. */
    private val todayCorrect: Int? = null,
    /** Today's total questions. null = unknown. */
    private val todayTotal: Int? = null,
    /** XP earned today. null = unknown. */
    private val todayXp: Int? = null,
    private val onOpenWeb: () -> Unit
) : JPanel(GridBagLayout()) {

    private val green = Color(0x10, 0xB9, 0x81)
    private val amber = Color(0xF5, 0x9E, 0x0B)
    private val purple = Color(0x7C, 0x3A, 0xED)

    init {
        border   = JBUI.Borders.empty(JBUI.scale(20))
        isOpaque = false
        buildUI()
    }

    private fun buildUI() {
        val state  = StateManager.getInstance()
        val lang   = state.language
        val streak = state.currentStreak

        val gbc = GridBagConstraints().apply {
            gridx  = 0
            gridy  = GridBagConstraints.RELATIVE
            anchor = GridBagConstraints.CENTER
            fill   = GridBagConstraints.NONE
        }

        // ── Checkmark circle ─────────────────────────────────────────────────
        val checkmark = CheckmarkCircle()
        add(checkmark, gbc.also { it.insets = Insets(0, 0, JBUI.scale(14), 0) })

        // ── Title ─────────────────────────────────────────────────────────────
        val titleText = when (lang) {
            AppLanguage.EN -> "Today's Challenge Done!"
            AppLanguage.TR -> "Bugün Tamamlandı!"
        }
        add(JBLabel("<html><b>$titleText</b></html>").apply {
            font = JBUI.Fonts.label(16f)
        }, gbc.also { it.insets = Insets(0, 0, JBUI.scale(12), 0) })

        // ── Streak badge ──────────────────────────────────────────────────────
        val streakText = when {
            streak > 1 && lang == AppLanguage.EN -> "🔥  $streak day streak"
            streak > 1                           -> "🔥  $streak günlük seri"
            lang == AppLanguage.EN               -> "🎉  First day done!"
            else                                 -> "🎉  İlk gün tamamlandı"
        }
        add(
            BadgePanel(streakText, amber),
            gbc.also { it.insets = Insets(0, 0, JBUI.scale(10), 0) }
        )

        // ── Score card (today's session result) ───────────────────────────────
        val correct = todayCorrect
        val total   = todayTotal
        if (correct != null && total != null && total > 0) {
            val pct       = (correct * 100) / total
            val subText   = when (lang) {
                AppLanguage.EN -> "correct answers  •  $pct%"
                AppLanguage.TR -> "doğru cevap  •  %$pct"
            }
            add(
                ScoreCard(correct, total, subText, green),
                gbc.also { it.insets = Insets(0, 0, JBUI.scale(8), 0) }
            )
        }

        // ── XP badge (if known) ───────────────────────────────────────────────
        val xp = todayXp
        if (xp != null && xp > 0) {
            val xpText = when (lang) {
                AppLanguage.EN -> "✨  +$xp XP earned today"
                AppLanguage.TR -> "✨  +$xp XP kazanıldı"
            }
            add(
                BadgePanel(xpText, purple),
                gbc.also { it.insets = Insets(0, 0, JBUI.scale(16), 0) }
            )
        } else {
            gbc.insets = Insets(0, 0, JBUI.scale(16), 0)
        }

        // ── CTA button ────────────────────────────────────────────────────────
        val btnText = when (lang) {
            AppLanguage.EN -> "View Leaderboard →"
            AppLanguage.TR -> "Sıralamayı Gör →"
        }
        add(
            GreenButton(btnText, green) { onOpenWeb() },
            gbc.also { it.insets = Insets(0, 0, 0, 0) }
        )
    }
}

// ── Inner helpers ──────────────────────────────────────────────────────────────

private class CheckmarkCircle : JPanel() {
    private val green = Color(0x10, 0xB9, 0x81)
    init {
        preferredSize = Dimension(JBUI.scale(60), JBUI.scale(60))
        isOpaque      = false
    }
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2   = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val size = JBUI.scale(60)
        g2.color = green
        g2.fillOval(0, 0, size, size)
        g2.color  = Color.WHITE
        g2.stroke = BasicStroke(JBUI.scale(3).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val s = JBUI.scale(1).toFloat()
        val path = GeneralPath()
        path.moveTo(15 * s, 30 * s)
        path.lineTo(25 * s, 42 * s)
        path.lineTo(45 * s, 20 * s)
        g2.draw(path)
    }
}

private class BadgePanel(private val label: String, private val color: Color) : JPanel() {
    init {
        isOpaque = false
        val fm = getFontMetrics(JBUI.Fonts.label(11f))
        val w  = fm.stringWidth(label) + JBUI.scale(24)
        val h  = JBUI.scale(26)
        preferredSize = Dimension(w, h)
    }
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.color  = Color(color.red, color.green, color.blue, 30)
        g2.fillRoundRect(0, 0, width, height, height, height)
        g2.color  = Color(color.red, color.green, color.blue, 160)
        g2.stroke = BasicStroke(1f)
        g2.drawRoundRect(0, 0, width - 1, height - 1, height, height)
        g2.color = color
        g2.font  = JBUI.Fonts.label(11f)
        val fm   = g2.fontMetrics
        val x    = (width  - fm.stringWidth(label)) / 2
        val y    = (height + fm.ascent - fm.descent) / 2
        g2.drawString(label, x, y)
    }
}

private class ScoreCard(
    correct: Int,
    total: Int,
    subText: String,
    private val green: Color
) : JPanel(GridBagLayout()) {
    init {
        isOpaque      = false
        preferredSize = Dimension(JBUI.scale(200), JBUI.scale(64))

        val sgbc = GridBagConstraints().apply {
            gridx = 0; gridy = GridBagConstraints.RELATIVE
            anchor = GridBagConstraints.CENTER
        }
        add(JBLabel("<html><b>$correct / $total</b></html>").apply {
            font       = JBUI.Fonts.label(20f)
            foreground = JBColor(green, green)
        }, sgbc.also { it.insets = Insets(JBUI.scale(6), JBUI.scale(16), JBUI.scale(2), JBUI.scale(16)) })
        add(JBLabel(subText).apply {
            font       = JBUI.Fonts.smallFont()
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }, sgbc.also { it.insets = Insets(0, JBUI.scale(16), JBUI.scale(6), JBUI.scale(16)) })
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color  = Color(green.red, green.green, green.blue, 15)
        g2.fillRoundRect(0, 0, width, height, JBUI.scale(10), JBUI.scale(10))
        g2.color  = Color(green.red, green.green, green.blue, 60)
        g2.stroke = BasicStroke(1f)
        g2.drawRoundRect(0, 0, width - 1, height - 1, JBUI.scale(10), JBUI.scale(10))
    }
}

private class GreenButton(text: String, private val green: Color, onClick: () -> Unit) : JButton(text) {
    init {
        isContentAreaFilled = false
        isBorderPainted     = false
        isFocusPainted      = false
        isOpaque            = false
        font                = JBUI.Fonts.label(12f)
        foreground          = Color.WHITE
        cursor              = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        preferredSize       = Dimension(JBUI.scale(200), JBUI.scale(36))
        addActionListener { onClick() }
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val bg = when {
            model.isPressed  -> green.darker()
            model.isRollover -> Color(0x0D, 0xA2, 0x70)
            else             -> green
        }
        g2.color = bg
        g2.fillRoundRect(0, 0, width, height, JBUI.scale(8), JBUI.scale(8))
        super.paintComponent(g)
    }
}
