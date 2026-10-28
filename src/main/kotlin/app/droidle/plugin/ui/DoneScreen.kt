package app.droidle.plugin.ui

import app.droidle.plugin.state.StateManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.GeneralPath
import javax.swing.JButton
import javax.swing.JPanel

class DoneScreen(private val onRetry: () -> Unit) : JPanel(GridBagLayout()) {

    private val green = Color(0x10, 0xB9, 0x81)
    private val amber = Color(0xF5, 0x9E, 0x0B)

    init {
        border = JBUI.Borders.empty(JBUI.scale(20))
        isOpaque = false
        val state = StateManager.getInstance()
        val streak = state.currentStreak
        val total = state.totalAttempted
        val correct = state.totalCorrect

        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = GridBagConstraints.RELATIVE
            anchor = GridBagConstraints.CENTER
            fill = GridBagConstraints.NONE
        }

        // Checkmark circle
        val checkmark = object : JPanel() {
            init {
                preferredSize = Dimension(JBUI.scale(60), JBUI.scale(60))
                isOpaque = false
            }
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val size = JBUI.scale(60)
                g2.color = green
                g2.fillOval(0, 0, size, size)
                g2.color = Color.WHITE
                g2.stroke = BasicStroke(JBUI.scale(3).toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val s = JBUI.scale(1).toFloat()
                val path = GeneralPath()
                path.moveTo(15 * s, 30 * s)
                path.lineTo(25 * s, 42 * s)
                path.lineTo(45 * s, 20 * s)
                g2.draw(path)
            }
        }
        add(checkmark, gbc.also { it.insets = Insets(0, 0, JBUI.scale(14), 0) })

        // Title
        add(JBLabel("<html><b>Bugün Tamamlandı!</b></html>").apply {
            font = JBUI.Fonts.label(16f)
        }, gbc.also { it.insets = Insets(0, 0, JBUI.scale(12), 0) })

        // Streak badge
        val streakText = if (streak > 1) "🔥  $streak günlük seri" else "🎉  İlk gün tamamlandı"
        val streakBadge = object : JPanel() {
            private val label = streakText
            init {
                isOpaque = false
                val fm = getFontMetrics(JBUI.Fonts.label(11f))
                val w = fm.stringWidth(label) + JBUI.scale(24)
                val h = JBUI.scale(26)
                preferredSize = Dimension(w, h)
            }
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = Color(amber.red, amber.green, amber.blue, 30)
                g2.fillRoundRect(0, 0, width, height, height, height)
                g2.color = amber
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, height, height)
                g2.color = amber
                g2.font = JBUI.Fonts.label(11f)
                val fm = g2.fontMetrics
                val x = (width - fm.stringWidth(label)) / 2
                val y = (height + fm.ascent - fm.descent) / 2
                g2.drawString(label, x, y)
            }
        }
        add(streakBadge, gbc.also { it.insets = Insets(0, 0, JBUI.scale(16), 0) })

        // Score card
        if (total > 0) {
            val pct = (correct * 100) / total
            val scoreCard = object : JPanel(GridBagLayout()) {
                init {
                    isOpaque = false
                    preferredSize = Dimension(JBUI.scale(180), JBUI.scale(60))
                }
                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = Color(green.red, green.green, green.blue, 15)
                    g2.fillRoundRect(0, 0, width, height, JBUI.scale(10), JBUI.scale(10))
                    g2.color = Color(green.red, green.green, green.blue, 60)
                    g2.stroke = BasicStroke(1f)
                    g2.drawRoundRect(0, 0, width - 1, height - 1, JBUI.scale(10), JBUI.scale(10))
                }
            }
            val sgbc = GridBagConstraints().apply {
                gridx = 0; gridy = GridBagConstraints.RELATIVE
                anchor = GridBagConstraints.CENTER
            }
            scoreCard.add(JBLabel("<html><b>$correct / $total</b></html>").apply {
                font = JBUI.Fonts.label(20f)
                foreground = green
            }, sgbc.also { it.insets = Insets(JBUI.scale(6), JBUI.scale(16), JBUI.scale(2), JBUI.scale(16)) })
            scoreCard.add(JBLabel("doğru cevap  •  %$pct").apply {
                font = JBUI.Fonts.smallFont()
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
            }, sgbc.also { it.insets = Insets(0, JBUI.scale(16), JBUI.scale(6), JBUI.scale(16)) })
            add(scoreCard, gbc.also { it.insets = Insets(0, 0, JBUI.scale(20), 0) })
        }

        // Green CTA button
        val btn = object : JButton("Sıralamayı Gör →") {
            init {
                isContentAreaFilled = false
                isBorderPainted = false
                isFocusPainted = false
                isOpaque = false
                font = JBUI.Fonts.label(12f)
                foreground = Color.WHITE
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                preferredSize = Dimension(JBUI.scale(180), JBUI.scale(36))
            }
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val bg = if (model.isPressed) green.darker() else if (model.isRollover) Color(0x0D, 0xA2, 0x70) else green
                g2.color = bg
                g2.fillRoundRect(0, 0, width, height, JBUI.scale(8), JBUI.scale(8))
                super.paintComponent(g)
            }
        }
        btn.addActionListener { onRetry() }
        add(btn, gbc.also { it.insets = Insets(0, 0, 0, 0) })
    }
}
