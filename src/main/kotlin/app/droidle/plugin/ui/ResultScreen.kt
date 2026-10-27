package app.droidle.plugin.ui

import app.droidle.plugin.model.Question
import app.droidle.plugin.state.StateManager
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.border.CompoundBorder

/**
 * Results screen shown after completing all 5 questions.
 *
 * Layout:
 *   ┌──────────────────────┐
 *   │ Score ring + label   │  ← ScoreRing custom component
 *   │ 🔥 streak badge      │
 *   │ Category breakdown   │  ← compact table
 *   │ [Open on Web →]      │
 *   └──────────────────────┘
 */
class ResultScreen(
    private val correctCount: Int,
    private val questions: List<Question>,
    private val onOpenWeb: () -> Unit
) : JPanel(BorderLayout(0, 0)) {

    init {
        border   = JBUI.Borders.empty()
        isOpaque = false
        buildUI()
    }

    private fun buildUI() {
        val total    = questions.size.coerceAtLeast(1)
        val pct      = (correctCount * 100) / total
        val state    = StateManager.getInstance()
        val streak   = state.currentStreak
        val longest  = state.longestStreak

        // ── Score ring ──────────────────────────────────────────────────────
        val ring = ScoreRing(correctCount, total)

        // ── Score caption ───────────────────────────────────────────────────
        val scoreCaption = when {
            pct == 100 -> "Mükemmel! 🎉"
            pct >= 80  -> "Harika gidiş!"
            pct >= 60  -> "İyi iş çıkardın."
            pct >= 40  -> "Daha çok pratik yapabilirsin."
            else       -> "Devam et, yarın daha iyi!"
        }
        val captionLabel = JBLabel(scoreCaption).apply {
            font = JBUI.Fonts.label(13f).deriveFont(Font.BOLD)
            horizontalAlignment = SwingConstants.CENTER
        }

        // ── Streak row ──────────────────────────────────────────────────────
        val streakLabel = JBLabel(
            if (streak > 0) "🔥  $streak günlük seri" else "Seri: 0 gün"
        ).apply {
            font      = JBUI.Fonts.label(12f)
            foreground = JBColor(Color(0xF59E0B), Color(0xFBBF24))
            horizontalAlignment = SwingConstants.CENTER
        }
        val longestLabel = JBLabel("En uzun: $longest gün").apply {
            font       = JBUI.Fonts.smallFont()
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
            horizontalAlignment = SwingConstants.CENTER
        }

        // ── Category breakdown ──────────────────────────────────────────────
        val breakdownPanel = buildBreakdown()

        // ── CTA button ──────────────────────────────────────────────────────
        val webBtn = JButton("Sıralamayı Gör  →").apply {
            addActionListener { onOpenWeb() }
            alignmentX = Component.CENTER_ALIGNMENT
        }

        // ── Assembly ────────────────────────────────────────────────────────
        val content = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border   = JBUI.Borders.empty(JBUI.scale(20), JBUI.scale(16))

            fun centered(c: JComponent) { c.alignmentX = Component.CENTER_ALIGNMENT; add(c) }
            fun gap(px: Int = 10)       { add(Box.createVerticalStrut(JBUI.scale(px))) }

            centered(ring)
            gap(12)
            centered(captionLabel)
            gap(6)
            centered(streakLabel)
            gap(2)
            centered(longestLabel)
            gap(20)
            add(breakdownPanel)
            gap(20)
            centered(webBtn)
        }

        val scroll = JBScrollPane(content).apply {
            border                    = JBUI.Borders.empty()
            verticalScrollBarPolicy   = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        add(scroll, BorderLayout.CENTER)
    }

    private fun buildBreakdown(): JPanel {
        val byCategory = questions.groupBy { it.category }

        val panel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            border   = CompoundBorder(
                RoundedLineBorder(JBColor(Color(0xE4E4E7), Color(0x3F3F46)), JBUI.scale(8)),
                JBUI.Borders.empty(JBUI.scale(10), JBUI.scale(12))
            )
            alignmentX = Component.CENTER_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }

        val header = GridBagConstraints().apply {
            gridx   = 0; gridy = 0; weightx = 1.0
            anchor  = GridBagConstraints.WEST
            fill    = GridBagConstraints.HORIZONTAL
            insets  = Insets(0, 0, JBUI.scale(6), 0)
        }
        panel.add(JBLabel("Konu Dağılımı").apply {
            font       = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }, header)

        byCategory.entries.forEachIndexed { i, (cat, qs) ->
            val gbc = GridBagConstraints().apply {
                gridy   = i + 1
                anchor  = GridBagConstraints.WEST
                insets  = Insets(JBUI.scale(3), 0, JBUI.scale(3), 0)
            }

            // Category name
            gbc.gridx = 0; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL
            panel.add(JBLabel(cat).apply { font = JBUI.Fonts.smallFont() }, gbc)

            // Count pill
            gbc.gridx = 1; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE
            gbc.insets = Insets(JBUI.scale(3), JBUI.scale(8), JBUI.scale(3), 0)
            panel.add(PillLabel("${qs.size} soru"), gbc)
        }
        return panel
    }
}

// ── Score ring ─────────────────────────────────────────────────────────────────

private class ScoreRing(private val correct: Int, private val total: Int) : JComponent() {

    private val diameter = JBUI.scale(90)

    init {
        preferredSize = Dimension(diameter, diameter)
        minimumSize   = preferredSize
        maximumSize   = preferredSize
    }

    override fun paintComponent(g: Graphics) {
        val g2   = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val sw   = JBUI.scale(8).toFloat()
        val pad  = (sw / 2).toInt()
        val d    = diameter - pad * 2

        // Track (background ring)
        g2.color = JBColor(Color(0xE4E4E7), Color(0x3F3F46))
        g2.stroke = BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.drawOval(pad, pad, d, d)

        // Progress arc
        val pct  = if (total > 0) correct.toFloat() / total else 0f
        val arc  = (pct * 360).toInt()
        val color = when {
            pct >= 0.8f -> JBColor(Color(0x10B981), Color(0x10B981))
            pct >= 0.5f -> JBColor(Color(0xF59E0B), Color(0xFBBF24))
            else        -> JBColor(Color(0xEF4444), Color(0xEF4444))
        }
        g2.color  = color
        g2.stroke = BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.drawArc(pad, pad, d, d, 90, -arc)

        // Center text
        g2.stroke = BasicStroke(1f)
        val bigFont   = JBUI.Fonts.label(22f).deriveFont(Font.BOLD)
        val smallFont = JBUI.Fonts.smallFont()
        g2.font  = bigFont
        g2.color = foreground

        val text     = "$correct/$total"
        val fm       = g2.getFontMetrics(bigFont)
        val tx       = (diameter - fm.stringWidth(text)) / 2
        val ty       = (diameter + fm.ascent - fm.descent) / 2 - JBUI.scale(4)
        g2.drawString(text, tx, ty)

        val sub  = "doğru"
        g2.font  = smallFont
        g2.color = JBUI.CurrentTheme.Label.disabledForeground()
        val sfm  = g2.getFontMetrics(smallFont)
        g2.drawString(sub, (diameter - sfm.stringWidth(sub)) / 2, ty + sfm.height - JBUI.scale(2))
    }
}

// ── Pill label ─────────────────────────────────────────────────────────────────

private class PillLabel(text: String) : JBLabel(text) {
    init {
        font       = JBUI.Fonts.smallFont()
        foreground = JBColor(Color(0x047857), Color(0x6EE7B7))
        border     = CompoundBorder(
            RoundedLineBorder(JBColor(Color(0x6EE7B7), Color(0x064E3B)), JBUI.scale(10)),
            JBUI.Borders.empty(JBUI.scale(1), JBUI.scale(6))
        )
        isOpaque = false
    }
}
