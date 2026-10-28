package app.droidle.plugin.ui

import app.droidle.plugin.model.Question
import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.state.StateManager
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.CompoundBorder

/**
 * Result screen — shown after quiz completion.
 *
 * Displays:
 *  - Score ring (correct/total)
 *  - Score caption (language-aware)
 *  - XP earned badge (mirrors web difficulty-based XP)
 *  - Streak badge with flame
 *  - Longest streak label
 *  - Category breakdown (per-question grouping)
 *  - Difficulty breakdown (Junior/Mid/Senior/Expert)
 *  - CTA button → web leaderboard
 */
class ResultScreen(
    private val correctCount: Int,
    private val questions: List<Question>,
    private val earnedXp: Int = 0,
    private val onOpenWeb: () -> Unit
) : JPanel(BorderLayout(0, 0)) {

    init {
        border   = JBUI.Borders.empty()
        isOpaque = false
        buildUI()
    }

    private fun buildUI() {
        val total  = questions.size.coerceAtLeast(1)
        val pct    = (correctCount * 100) / total
        val state  = StateManager.getInstance()
        val lang   = state.language
        val streak = state.currentStreak
        val longest = state.longestStreak

        // ── Score ring ──────────────────────────────────────────────────────
        val ring = ScoreRing(correctCount, total)

        // ── Score caption ───────────────────────────────────────────────────
        val scoreCaption = when (lang) {
            AppLanguage.EN -> when {
                pct == 100 -> "Perfect! 🎉"
                pct >= 80  -> "Great job!"
                pct >= 60  -> "Good work."
                pct >= 40  -> "Keep practicing."
                else       -> "Keep going, better tomorrow!"
            }
            AppLanguage.TR -> when {
                pct == 100 -> "Mükemmel! 🎉"
                pct >= 80  -> "Harika gidiş!"
                pct >= 60  -> "İyi iş çıkardın."
                pct >= 40  -> "Daha çok pratik yapabilirsin."
                else       -> "Devam et, yarın daha iyi!"
            }
        }
        val captionLabel = JBLabel(scoreCaption).apply {
            font = JBUI.Fonts.label(14f).deriveFont(Font.BOLD)
            horizontalAlignment = SwingConstants.CENTER
        }

        // ── XP earned badge ─────────────────────────────────────────────────
        val xpBadge = XpEarnedBadge(earnedXp, lang)

        // ── Streak badge ────────────────────────────────────────────────────
        val streakBadge = StreakBadge(streak, lang)

        val longestText = when (lang) {
            AppLanguage.EN -> "Longest streak: $longest days"
            AppLanguage.TR -> "En uzun seri: $longest gün"
        }
        val longestLabel = JBLabel(longestText).apply {
            font       = JBUI.Fonts.smallFont()
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
            horizontalAlignment = SwingConstants.CENTER
        }

        // ── Breakdown panels ────────────────────────────────────────────────
        val categoryPanel   = buildCategoryBreakdown(lang)
        val difficultyPanel = buildDifficultyBreakdown(lang)

        // ── CTA button ──────────────────────────────────────────────────────
        val webBtnText = when (lang) {
            AppLanguage.EN -> "View Web Leaderboard  →"
            AppLanguage.TR -> "Web'de Sıralamayı Gör  →"
        }
        val webBtn = ResultButton(webBtnText) { onOpenWeb() }

        // ── Assembly ────────────────────────────────────────────────────────
        val content = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border   = JBUI.Borders.empty(JBUI.scale(20), JBUI.scale(16))

            fun centered(c: JComponent) { c.alignmentX = Component.CENTER_ALIGNMENT; add(c) }
            fun gap(px: Int = 10)       { add(Box.createVerticalStrut(JBUI.scale(px))) }

            centered(ring)
            gap(14)
            centered(captionLabel)
            gap(8)
            centered(xpBadge)
            gap(10)
            centered(streakBadge)
            gap(4)
            centered(longestLabel)
            gap(20)
            add(categoryPanel)
            gap(10)
            add(difficultyPanel)
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

    // ── Category breakdown ─────────────────────────────────────────────────────

    private fun buildCategoryBreakdown(lang: AppLanguage): JPanel {
        val byCategory = questions.groupBy { it.category }

        val title = if (lang == AppLanguage.EN) "📊  By Topic" else "📊  Konu Dağılımı"

        return buildBreakdownCard(title, JBColor(Color(0xA7F3D0), Color(0x134E32))) { outer ->
            byCategory.entries.forEachIndexed { i, (cat, qs) ->
                val correct = qs.count { it.correct_answer != null } // server validates, fallback
                val gbc = GridBagConstraints().apply {
                    gridy  = i + 1
                    anchor = GridBagConstraints.WEST
                    insets = Insets(JBUI.scale(3), 0, JBUI.scale(3), 0)
                }
                gbc.gridx   = 0; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL
                outer.add(JBLabel(cat).apply { font = JBUI.Fonts.smallFont() }, gbc)

                gbc.gridx  = 1; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE
                gbc.insets = Insets(JBUI.scale(3), JBUI.scale(8), JBUI.scale(3), 0)
                val pillText = if (lang == AppLanguage.EN)
                    "${qs.size} q" else "${qs.size} soru"
                outer.add(CountPill(pillText), gbc)
            }
        }
    }

    // ── Difficulty breakdown ───────────────────────────────────────────────────

    private fun buildDifficultyBreakdown(lang: AppLanguage): JPanel {
        val order  = listOf("Junior", "Mid", "Senior", "Expert")
        val byDiff = questions.groupBy { it.difficulty }
            .entries
            .sortedBy { order.indexOf(it.key).takeIf { i -> i >= 0 } ?: 99 }

        val title = if (lang == AppLanguage.EN) "⚡  By Difficulty" else "⚡  Zorluk Dağılımı"

        return buildBreakdownCard(title, JBColor(Color(0xFDE68A), Color(0x78350F))) { outer ->
            byDiff.forEachIndexed { i, (diff, qs) ->
                val xpPer = qs.firstOrNull()?.xpValue ?: 10
                val gbc = GridBagConstraints().apply {
                    gridy  = i + 1
                    anchor = GridBagConstraints.WEST
                    insets = Insets(JBUI.scale(3), 0, JBUI.scale(3), 0)
                }

                gbc.gridx   = 0; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL
                outer.add(DifficultyLabel(diff), gbc)

                gbc.gridx  = 1; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE
                gbc.insets = Insets(JBUI.scale(3), JBUI.scale(8), JBUI.scale(3), 0)
                val xpText = "${qs.size}× ${xpPer}XP"
                outer.add(CountPill(xpText, amber = true), gbc)
            }
        }
    }

    // ── Shared card builder ────────────────────────────────────────────────────

    private fun buildBreakdownCard(
        title: String,
        borderColor: Color,
        fillRows: (GridBagPanel) -> Unit
    ): JPanel {
        val outer = GridBagPanel().apply {
            border = CompoundBorder(
                RoundedLineBorder(borderColor, JBUI.scale(10)),
                JBUI.Borders.empty(JBUI.scale(12), JBUI.scale(14))
            )
        }

        val header = GridBagConstraints().apply {
            gridx     = 0; gridy = 0
            gridwidth = 2
            weightx   = 1.0
            anchor    = GridBagConstraints.WEST
            fill      = GridBagConstraints.HORIZONTAL
            insets    = Insets(0, 0, JBUI.scale(8), 0)
        }
        outer.add(JBLabel(title).apply {
            font       = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
            foreground = JBUI.CurrentTheme.Label.foreground()
        }, header)

        fillRows(outer)
        return outer
    }
}

// ── GridBagPanel (opaque=false, rounded bg) ────────────────────────────────────

private class GridBagPanel : JPanel(GridBagLayout()) {
    init {
        isOpaque   = false
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    }
}

// ── XP earned badge ────────────────────────────────────────────────────────────

private class XpEarnedBadge(private val xp: Int, private val lang: AppLanguage) : JComponent() {

    private val purple = Color(0x7C, 0x3A, 0xED)
    private val text   = if (lang == AppLanguage.EN) "✨  +$xp XP earned" else "✨  +$xp XP kazanıldı"
    private val arc    = JBUI.scale(14)
    private val hPad   = JBUI.scale(12)
    private val vPad   = JBUI.scale(5)

    init {
        font = JBUI.Fonts.label(11f)
        val fm = getFontMetrics(font)
        val w  = fm.stringWidth(text) + hPad * 2
        val h  = fm.height + vPad * 2
        preferredSize = Dimension(w, h)
        minimumSize   = preferredSize
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.color = Color(purple.red, purple.green, purple.blue, 20)
        g2.fillRoundRect(0, 0, width, height, arc, arc)
        g2.color = Color(purple.red, purple.green, purple.blue, 150)
        g2.stroke = BasicStroke(1f)
        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        g2.color = purple
        g2.font  = font ?: JBUI.Fonts.label(11f)
        val fm   = g2.fontMetrics
        val tx   = (width - fm.stringWidth(text)) / 2
        val ty   = (height + fm.ascent - fm.descent) / 2
        g2.drawString(text, tx, ty)
    }
}

// ── Score ring ─────────────────────────────────────────────────────────────────

private class ScoreRing(private val correct: Int, private val total: Int) : JComponent() {

    private val diameter = JBUI.scale(96)

    init {
        preferredSize = Dimension(diameter, diameter)
        minimumSize   = preferredSize
        maximumSize   = preferredSize
    }

    override fun paintComponent(g: Graphics) {
        val g2  = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val sw  = JBUI.scale(9).toFloat()
        val pad = (sw / 2 + 1).toInt()
        val d   = diameter - pad * 2

        g2.stroke = BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.color  = JBColor(Color(0xE4E4E7), Color(0x3F3F46))
        g2.drawOval(pad, pad, d, d)

        val pct = if (total > 0) correct.toFloat() / total else 0f
        val arc = (pct * 360).toInt()
        g2.color = when {
            pct >= 0.8f -> JBColor(Color(0x10B981), Color(0x10B981))
            pct >= 0.5f -> JBColor(Color(0xF59E0B), Color(0xFBBF24))
            else        -> JBColor(Color(0xEF4444), Color(0xEF4444))
        }
        g2.drawArc(pad, pad, d, d, 90, -arc)

        g2.stroke = BasicStroke(1f)
        val bigFont   = JBUI.Fonts.label(22f).deriveFont(Font.BOLD)
        val smallFont = JBUI.Fonts.smallFont()
        val text      = "$correct/$total"
        val fm        = g2.getFontMetrics(bigFont)
        g2.font  = bigFont
        g2.color = foreground
        val tx   = (diameter - fm.stringWidth(text)) / 2
        val ty   = (diameter + fm.ascent - fm.descent) / 2 - JBUI.scale(5)
        g2.drawString(text, tx, ty)

        val sub = "doğru"
        g2.font  = smallFont
        g2.color = JBUI.CurrentTheme.Label.disabledForeground()
        val sfm  = g2.getFontMetrics(smallFont)
        g2.drawString(sub, (diameter - sfm.stringWidth(sub)) / 2, ty + sfm.height)
    }
}

// ── Streak badge ───────────────────────────────────────────────────────────────

private class StreakBadge(private val streak: Int, private val lang: AppLanguage) : JComponent() {

    private val amber = Color(0xF5, 0x9E, 0x0B)
    private val text  = when {
        streak > 1 && lang == AppLanguage.EN -> "🔥  $streak day streak"
        streak > 1                           -> "🔥  $streak günlük seri"
        lang == AppLanguage.EN               -> "🎉  First day done!"
        else                                 -> "🎉  İlk gün tamamlandı"
    }
    private val arc  = JBUI.scale(14)
    private val hPad = JBUI.scale(12)
    private val vPad = JBUI.scale(5)

    init {
        font = JBUI.Fonts.label(11f)
        val fm = getFontMetrics(font)
        val w  = fm.stringWidth(text) + hPad * 2
        val h  = fm.height + vPad * 2
        preferredSize = Dimension(w, h)
        minimumSize   = preferredSize
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.color = Color(amber.red, amber.green, amber.blue, 25)
        g2.fillRoundRect(0, 0, width, height, arc, arc)
        g2.color = Color(amber.red, amber.green, amber.blue, 180)
        g2.stroke = BasicStroke(1f)
        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        g2.color = amber
        g2.font  = font ?: JBUI.Fonts.label(11f)
        val fm = g2.fontMetrics
        val tx = (width - fm.stringWidth(text)) / 2
        val ty = (height + fm.ascent - fm.descent) / 2
        g2.drawString(text, tx, ty)
    }
}

// ── Difficulty label (colored by level) ───────────────────────────────────────

private class DifficultyLabel(diff: String) : JBLabel(diff) {
    init {
        font       = JBUI.Fonts.smallFont()
        foreground = when (diff) {
            "Junior" -> JBColor(Color(0x059669), Color(0x34D399))
            "Mid"    -> JBColor(Color(0x2563EB), Color(0x60A5FA))
            "Senior" -> JBColor(Color(0xD97706), Color(0xFBBF24))
            "Expert" -> JBColor(Color(0xDC2626), Color(0xF87171))
            else     -> JBUI.CurrentTheme.Label.foreground()
        }
    }
}

// ── Count pill ─────────────────────────────────────────────────────────────────

private class CountPill(text: String, amber: Boolean = false) : JBLabel(text) {
    init {
        font = JBUI.Fonts.smallFont()
        if (amber) {
            foreground = JBColor(Color(0x92400E), Color(0xFCD34D))
            border     = CompoundBorder(
                RoundedLineBorder(JBColor(Color(0xFDE68A), Color(0x78350F)), JBUI.scale(10)),
                JBUI.Borders.empty(JBUI.scale(1), JBUI.scale(6))
            )
        } else {
            foreground = JBColor(Color(0x047857), Color(0x6EE7B7))
            border     = CompoundBorder(
                RoundedLineBorder(JBColor(Color(0x6EE7B7), Color(0x064E3B)), JBUI.scale(10)),
                JBUI.Borders.empty(JBUI.scale(1), JBUI.scale(6))
            )
        }
        isOpaque = false
    }
}

// ── Result CTA button ──────────────────────────────────────────────────────────

private class ResultButton(text: String, onClick: () -> Unit) : JButton(text) {
    private val green   = Color(0x10, 0xB9, 0x81)
    private var hovered = false
    private val arc     = JBUI.scale(7)

    init {
        isBorderPainted     = false
        isContentAreaFilled = false
        isFocusPainted      = false
        foreground          = Color.WHITE
        font                = JBUI.Fonts.label(12f).deriveFont(Font.BOLD)
        cursor              = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        border              = JBUI.Borders.empty(JBUI.scale(7), JBUI.scale(18))
        addActionListener { onClick() }
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { hovered = true;  repaint() }
            override fun mouseExited(e: MouseEvent)  { hovered = false; repaint() }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = if (hovered) Color(0x0D, 0xA2, 0x70) else green
        g2.fillRoundRect(0, 0, width, height, arc, arc)
        super.paintComponent(g)
    }
}
