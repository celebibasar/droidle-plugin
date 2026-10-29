package app.droidle.plugin.ui

import app.droidle.plugin.model.DocLink
import app.droidle.plugin.model.Question
import app.droidle.plugin.model.UserAnswerPayload
import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.state.StateManager
import app.droidle.plugin.ui.components.AutoWrappingHtmlLabel
import app.droidle.plugin.ui.components.BugdroidIcon
import app.droidle.plugin.ui.components.ModernCodeSnippet
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
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
 * Modern Result Screen adhering to Linear / JetBrains New UI aesthetics.
 *
 * Features:
 * - 5 interactive vector Android Bugdroid icons (emerald green if correct, rose red if wrong).
 * - Clicking any Bugdroid immediately reveals the Question Review Card for that question.
 * - Score summary, XP earned badge, streak badge.
 * - Category and difficulty breakdown cards.
 * - CTA button to view web leaderboard.
 */
class ResultScreen(
    private val correctCount: Int,
    private val questions: List<Question>,
    private val earnedXp: Int = 0,
    private val selectedOptions: List<Int> = emptyList(),
    private val answers: Map<String, UserAnswerPayload> = emptyMap(),
    private val project: Project? = null,
    private val onOpenWeb: () -> Unit
) : JPanel(BorderLayout(0, 0)) {

    var overrideLanguage: AppLanguage? = null

    private val bugdroidIcons = mutableListOf<BugdroidIcon>()
    private val reviewCardContainer = JPanel(BorderLayout()).apply { isOpaque = false }
    private var selectedQuestionIndex: Int = 0

    init {
        border = JBUI.Borders.empty()
        isOpaque = false
        buildUI()
    }

    private fun buildUI() {
        val total = questions.size.coerceAtLeast(1)
        val state = try { StateManager.getInstance() } catch (_: Throwable) { null }
        val lang = overrideLanguage ?: state?.language ?: AppLanguage.TR
        val streak = state?.currentStreak ?: 0
        val longest = state?.longestStreak ?: 0

        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(JBUI.scale(16), JBUI.scale(14), JBUI.scale(24), JBUI.scale(14))

            fun centered(c: JComponent) {
                c.alignmentX = Component.CENTER_ALIGNMENT
                add(c)
            }
            fun gap(px: Int = 10) {
                add(Box.createVerticalStrut(JBUI.scale(px)))
            }

            // ── Celebration Header ──────────────────────────────────────────
            val celebrationTitle = JBLabel(
                if (lang == AppLanguage.EN) "Congratulations!" else "Tebrikler!"
            ).apply {
                font = JBUI.Fonts.label(18f).deriveFont(Font.BOLD)
                foreground = JBUI.CurrentTheme.Label.foreground()
            }
            centered(celebrationTitle)
            gap(2)

            val celebrationDesc = JBLabel(
                if (lang == AppLanguage.EN) "You completed today's Android quiz."
                else "Bugünün Android testini tamamladınız."
            ).apply {
                font = JBUI.Fonts.label(12f)
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
            }
            centered(celebrationDesc)
            gap(10)

            // ── Score Headline ──────────────────────────────────────────────
            val pct = (correctCount * 100) / total
            val scoreText = if (lang == AppLanguage.EN) "$correctCount / $total Correct" else "$correctCount / $total Doğru"
            val scoreLabel = JBLabel(scoreText).apply {
                font = JBUI.Fonts.label(28f).deriveFont(Font.BOLD)
                foreground = when {
                    pct >= 80 -> JBColor(Color(0x10B981), Color(0x34D399))
                    pct >= 60 -> JBColor(Color(0xF59E0B), Color(0xFBBF24))
                    else      -> JBColor(Color(0xF43F5E), Color(0xFB7185))
                }
            }
            centered(scoreLabel)
            gap(4)

            val caption = when (lang) {
                AppLanguage.EN -> when {
                    pct == 100 -> if (total == 5) "Perfect! All 5 Correct! 🎉" else "Perfect! All $total Correct! 🎉"
                    pct >= 80  -> "Great job! Keep the streak alive! 🔥"
                    pct >= 60  -> "Good effort, sharp thinking! 💡"
                    else       -> "Done for today! Review mistakes below 👇"
                }
                AppLanguage.TR -> when {
                    pct == 100 -> if (total == 5) "Kusursuz! 5'te 5 Yaptın! 🎉" else "Kusursuz! $total'de $total Yaptın! 🎉"
                    pct >= 80  -> "Harika gidiş! Seriyi koru! 🔥"
                    pct >= 60  -> "İyi iş çıkardın, zihnini açtın! 💡"
                    else       -> "Bugün tamamlandı! Aşağıdan hatalarını incele 👇"
                }
            }
            val captionLabel = JBLabel(caption).apply {
                font = JBUI.Fonts.label(12f)
                foreground = JBUI.CurrentTheme.Label.foreground()
            }
            centered(captionLabel)
            gap(10)

            // ── Badges: XP + Streak ─────────────────────────────────────────
            val badgesRow = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(8), 0)).apply {
                isOpaque = false
                add(XpBadge(earnedXp, lang))
                add(StreakResultBadge(streak, lang))
            }
            centered(badgesRow)
            gap(16)

            // ── 5 Android Bugdroid Vector Icons Row & Instructions ──────────
            val reviewTitle = JBLabel(
                if (lang == AppLanguage.EN) "Review Questions - Click Bugdroids to see details"
                else "Soruları İncele - Detayları görmek için Bugdroid'lere tıklayın"
            ).apply {
                font = JBUI.Fonts.smallFont()
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
            }
            centered(reviewTitle)
            gap(8)

            val bugdroidsRow = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(8), 0)).apply {
                isOpaque = false
                alignmentX = Component.CENTER_ALIGNMENT
            }

            bugdroidIcons.clear()
            questions.forEachIndexed { i, q ->
                val isCorrect = determineQuestionCorrectness(i, q)
                val icon = BugdroidIcon(
                    questionIndex = i,
                    isCorrect = isCorrect,
                    isSelected = (i == selectedQuestionIndex),
                    lang = lang
                ) { clickedIndex ->
                    selectQuestion(clickedIndex)
                }
                bugdroidIcons.add(icon)
                bugdroidsRow.add(icon)
            }
            centered(bugdroidsRow)
            gap(14)

            // ── Question Review Card Container ──────────────────────────────
            centered(reviewCardContainer)
            gap(16)

            // ── Topic & Difficulty Breakdowns ───────────────────────────────
            centered(buildCategoryBreakdown(lang))
            gap(10)
            centered(buildDifficultyBreakdown(lang))
            gap(18)

            // ── CTA: Web Leaderboard ────────────────────────────────────────
            val webBtnText = if (lang == AppLanguage.EN) "Leaderboard" else "Liderlik Tablosu"
            val webBtn = object : JButton(webBtnText) {
                init {
                    isBorderPainted = false
                    isContentAreaFilled = false
                    isFocusPainted = false
                    foreground = Color.WHITE
                    font = JBUI.Fonts.label(12f).deriveFont(Font.BOLD)
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    border = JBUI.Borders.empty(JBUI.scale(8), JBUI.scale(20))
                    preferredSize = Dimension(JBUI.scale(220), JBUI.scale(36))
                    maximumSize = preferredSize
                    addActionListener { onOpenWeb() }
                }
                override fun paintComponent(g: Graphics) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val bg = if (model.isPressed) Color(0x05, 0x96, 0x69)
                    else if (model.isRollover) Color(0x0D, 0xA2, 0x70)
                    else Color(0x10, 0xB9, 0x81)
                    g2.color = bg
                    g2.fillRoundRect(0, 0, width, height, JBUI.scale(8), JBUI.scale(8))
                    super.paintComponent(g)
                }
            }
            centered(webBtn)
        }

        // Initialize review card with selectedQuestionIndex if valid, or first incorrect question
        if (questions.isNotEmpty()) {
            val validIndex = if (selectedQuestionIndex in questions.indices) {
                selectedQuestionIndex
            } else {
                val firstWrong = questions.indices.firstOrNull { !determineQuestionCorrectness(it, questions[it]) }
                firstWrong ?: 0
            }
            selectQuestion(validIndex)
        }

        val scroll = JBScrollPane(content).apply {
            border = JBUI.Borders.empty()
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        add(scroll, BorderLayout.CENTER)
    }

    /** Call this when the active language changes so the result screen re-renders dynamically. */
    fun refreshLanguage() {
        removeAll()
        buildUI()
        revalidate()
        repaint()
    }

    fun getSelectedQuestionIndex(): Int = selectedQuestionIndex
    fun getReviewCardContainer(): JPanel = reviewCardContainer
    internal fun getBugdroidIcons(): List<BugdroidIcon> = bugdroidIcons

    private fun selectQuestion(index: Int) {
        if (index !in questions.indices) return
        selectedQuestionIndex = index

        bugdroidIcons.forEachIndexed { i, icon ->
            icon.isSelected = (i == index)
            icon.repaint()
        }

        val q = questions[index]
        val isCorrect = determineQuestionCorrectness(index, q)
        val userChosenOption = determineUserChosenOption(index, q)

        reviewCardContainer.removeAll()
        reviewCardContainer.add(buildQuestionReviewCard(index, q, isCorrect, userChosenOption), BorderLayout.CENTER)
        reviewCardContainer.revalidate()
        reviewCardContainer.repaint()
    }

    private fun determineQuestionCorrectness(index: Int, q: Question): Boolean {
        val optionKeys = listOf("A", "B", "C", "D")
        val correctKey = (q.resolvedCorrectAnswer ?: "A").uppercase()
        val correctIndex = optionKeys.indexOf(correctKey)

        // 1. From answers map if present
        val ans = answers[q.id] ?: answers[index.toString()]
        if (ans?.resolvedIsCorrect != null) return ans.resolvedIsCorrect!!
        val ansOpt = ans?.resolvedSelectedOption
        if (!ansOpt.isNullOrBlank()) {
            return ansOpt.equals(correctKey, ignoreCase = true)
        }

        // 2. From selected_options index vs correct option index
        val userOptionIdx = selectedOptions.getOrNull(index)
        if (userOptionIdx != null && userOptionIdx >= 0) {
            return userOptionIdx == correctIndex
        }

        return false
    }

    private fun determineUserChosenOption(index: Int, q: Question): String? {
        val ans = answers[q.id] ?: answers[index.toString()]
        val ansOpt = ans?.resolvedSelectedOption
        if (!ansOpt.isNullOrBlank()) return ansOpt.uppercase()

        val optionKeys = listOf("A", "B", "C", "D")
        val userOptionIdx = selectedOptions.getOrNull(index)
        if (userOptionIdx != null && userOptionIdx in 0..3) {
            return optionKeys[userOptionIdx]
        }
        return null
    }

    private fun buildQuestionReviewCard(
        index: Int,
        question: Question,
        isCorrect: Boolean,
        userOption: String?
    ): JPanel {
        val state = try { StateManager.getInstance() } catch (_: Throwable) { null }
        val lang = overrideLanguage ?: state?.language ?: AppLanguage.TR
        fun vGap(h: Int): Component = Box.createRigidArea(Dimension(0, JBUI.scale(h))).apply {
            if (this is JComponent) alignmentX = Component.LEFT_ALIGNMENT
        }

        val card = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = CompoundBorder(
                RoundedLineBorder(JBColor(Color(0xE4E4E7), Color(0x323438)), JBUI.scale(12)),
                JBUI.Borders.empty(JBUI.scale(12), JBUI.scale(14))
            )
            alignmentX = Component.CENTER_ALIGNMENT
            maximumSize = Dimension(JBUI.scale(460), Int.MAX_VALUE)
        }

        // Header
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT

            val qLabel = if (lang == AppLanguage.EN) "Question ${index + 1} / ${questions.size}" else "Soru ${index + 1} / ${questions.size}"
            val title = JBLabel("$qLabel • ${question.category} • ${question.difficulty}").apply {
                font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
            }
            val statusText = if (isCorrect) {
                if (lang == AppLanguage.EN) "✓ Correct" else "✓ Doğru"
            } else {
                if (lang == AppLanguage.EN) "✗ Incorrect" else "✗ Yanlış"
            }
            val statusBadge = JBLabel(statusText).apply {
                font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
                foreground = if (isCorrect) JBColor(Color(0x059669), Color(0x34D399))
                else JBColor(Color(0xF43F5E), Color(0xFB7185))
            }
            add(title, BorderLayout.WEST)
            add(statusBadge, BorderLayout.EAST)
        }
        card.add(header)
        card.add(vGap(8))

        // Question text
        val qText = AutoWrappingHtmlLabel(TextFormatter.formatQuestionHtml(question.displayQuestion(lang))).apply {
            font = JBUI.Fonts.label(12f)
            horizontalAlignment = SwingConstants.LEFT
            alignmentX = Component.LEFT_ALIGNMENT
        }
        card.add(qText)

        // Code snippet
        if (!question.code_snippet.isNullOrBlank()) {
            card.add(vGap(8))
            val snippet = ModernCodeSnippet(project, question.code_snippet!!, question.code_language)
            snippet.alignmentX = Component.LEFT_ALIGNMENT
            card.add(snippet)
        }

        card.add(vGap(10))

        // Options breakdown
        val opts = question.displayOptions(lang)
        val correctKey = (question.resolvedCorrectAnswer ?: "A").uppercase()

        listOf("A", "B", "C", "D").forEach { key ->
            val text = opts[key] ?: return@forEach
            val isUserChoice = (userOption != null && userOption.equals(key, ignoreCase = true))
            val isOptionCorrect = key.equals(correctKey, ignoreCase = true)

            val row = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                border = CompoundBorder(
                    RoundedLineBorder(
                        when {
                            isOptionCorrect -> JBColor(Color(0x10B981), Color(0x059669))
                            isUserChoice && !isCorrect -> JBColor(Color(0xF43F5E), Color(0xBE123C))
                            else -> JBColor(Color(0xE4E4E7), Color(0x2E3035))
                        },
                        JBUI.scale(8)
                    ),
                    JBUI.Borders.empty(JBUI.scale(6), JBUI.scale(8))
                )

                val badge = JBLabel("[$key]").apply {
                    font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(11f).toInt())
                    foreground = when {
                        isOptionCorrect -> JBColor(Color(0x059669), Color(0x34D399))
                        isUserChoice && !isCorrect -> JBColor(Color(0xF43F5E), Color(0xFB7185))
                        else -> JBUI.CurrentTheme.Label.disabledForeground()
                    }
                }
                val label = AutoWrappingHtmlLabel(TextFormatter.formatOptionHtml(text)).apply {
                    font = JBUI.Fonts.smallFont()
                    horizontalAlignment = SwingConstants.LEFT
                    alignmentX = Component.LEFT_ALIGNMENT
                    foreground = when {
                        isOptionCorrect -> JBColor(Color(0x065F46), Color(0x6EE7B7))
                        isUserChoice && !isCorrect -> JBColor(Color(0x9F1239), Color(0xFDA4AF))
                        else -> JBUI.CurrentTheme.Label.foreground()
                    }
                }
                add(badge, BorderLayout.WEST)
                add(label, BorderLayout.CENTER)
            }
            card.add(row)
            card.add(vGap(4))
        }

        // Explanation text
        val explanation = question.displayExplanation(lang)
        if (!explanation.isNullOrBlank()) {
            card.add(vGap(8))
            val explBox = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                border = CompoundBorder(
                    RoundedLineBorder(JBColor(Color(0x10B981), Color(0x059669)), JBUI.scale(8)),
                    JBUI.Borders.empty(JBUI.scale(8), JBUI.scale(10))
                )

                val headerText = if (lang == AppLanguage.EN) "💡 TECHNICAL EXPLANATION" else "💡 TEKNİK AÇIKLAMA"
                val explHeader = JBLabel(headerText).apply {
                    font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
                    foreground = JBUI.CurrentTheme.Label.disabledForeground()
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                add(explHeader)
                add(vGap(4))

                val explLabel = AutoWrappingHtmlLabel(TextFormatter.formatExplanationHtml(explanation)).apply {
                    font = JBUI.Fonts.smallFont()
                    horizontalAlignment = SwingConstants.LEFT
                    alignmentX = Component.LEFT_ALIGNMENT
                    foreground = JBUI.CurrentTheme.Label.foreground()
                }
                add(explLabel)

                if (question.doc_links.isNotEmpty()) {
                    add(vGap(6))
                    val docBtnText = if (lang == AppLanguage.EN) "Official Documentation ↗" else "Resmi Dokümana Git ↗"
                    val docBtn = JButton(docBtnText).apply {
                        font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
                        isBorderPainted = false
                        isContentAreaFilled = false
                        isFocusPainted = false
                        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        alignmentX = Component.LEFT_ALIGNMENT
                        foreground = JBColor(Color(0x2563EB), Color(0x60A5FA))
                        addActionListener { BrowserUtil.browse(question.doc_links.first().url) }
                    }
                    add(docBtn)
                }
            }
            card.add(explBox)
        }

        return card
    }

    private fun buildCategoryBreakdown(lang: AppLanguage): JPanel {
        val byCat = questions.groupBy { it.category }
        val title = if (lang == AppLanguage.EN) "📊 Topic Breakdown" else "📊 Konu Dağılımı"
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = CompoundBorder(
                RoundedLineBorder(JBColor(Color(0xE4E4E7), Color(0x323438)), JBUI.scale(10)),
                JBUI.Borders.empty(JBUI.scale(10), JBUI.scale(12))
            )
            alignmentX = Component.CENTER_ALIGNMENT
            maximumSize = Dimension(JBUI.scale(460), Int.MAX_VALUE)
        }
        panel.add(JBLabel(title).apply {
            font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        panel.add(Box.createVerticalStrut(JBUI.scale(6)))

        byCat.forEach { (cat, qs) ->
            val row = JPanel(BorderLayout()).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                add(JBLabel(cat).apply { font = JBUI.Fonts.smallFont() }, BorderLayout.WEST)
                val countText = if (lang == AppLanguage.EN) {
                    if (qs.size == 1) "1 question" else "${qs.size} questions"
                } else {
                    "${qs.size} soru"
                }
                add(JBLabel(countText).apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = JBUI.CurrentTheme.Label.disabledForeground()
                }, BorderLayout.EAST)
            }
            panel.add(row)
            panel.add(Box.createVerticalStrut(JBUI.scale(3)))
        }
        return panel
    }

    private fun buildDifficultyBreakdown(lang: AppLanguage): JPanel {
        val byDiff = questions.groupBy { it.difficulty }
        val title = if (lang == AppLanguage.EN) "⚡ Difficulty Breakdown" else "⚡ Zorluk Dağılımı"
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = CompoundBorder(
                RoundedLineBorder(JBColor(Color(0xE4E4E7), Color(0x323438)), JBUI.scale(10)),
                JBUI.Borders.empty(JBUI.scale(10), JBUI.scale(12))
            )
            alignmentX = Component.CENTER_ALIGNMENT
            maximumSize = Dimension(JBUI.scale(460), Int.MAX_VALUE)
        }
        panel.add(JBLabel(title).apply {
            font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        panel.add(Box.createVerticalStrut(JBUI.scale(6)))

        listOf("Junior", "Mid", "Senior", "Expert").forEach { diff ->
            val count = byDiff[diff]?.size ?: return@forEach
            val row = JPanel(BorderLayout()).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                add(JBLabel(diff).apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = when (diff) {
                        "Junior" -> JBColor(Color(0x059669), Color(0x34D399))
                        "Mid"    -> JBColor(Color(0x2563EB), Color(0x60A5FA))
                        "Senior" -> JBColor(Color(0xD97706), Color(0xFBBF24))
                        "Expert" -> JBColor(Color(0xDC2626), Color(0xF87171))
                        else     -> JBUI.CurrentTheme.Label.foreground()
                    }
                }, BorderLayout.WEST)
                val countText = if (lang == AppLanguage.EN) {
                    if (count == 1) "1 question" else "$count questions"
                } else {
                    "$count soru"
                }
                add(JBLabel(countText).apply {
                    font = JBUI.Fonts.smallFont()
                    foreground = JBUI.CurrentTheme.Label.disabledForeground()
                }, BorderLayout.EAST)
            }
            panel.add(row)
            panel.add(Box.createVerticalStrut(JBUI.scale(3)))
        }
        return panel
    }

    internal class XpBadge(val xp: Int, val lang: AppLanguage) : JBLabel(
        if (lang == AppLanguage.EN) "✨ +$xp Earned XP" else "✨ +$xp Kazanılan XP"
    ) {
        init {
            font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
            foreground = Color(0x8B, 0x5C, 0xF6)
            border = JBUI.Borders.empty(JBUI.scale(2), JBUI.scale(8))
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val purple = Color(0x8B, 0x5C, 0xF6)
                g2.color = Color(purple.red, purple.green, purple.blue, 25)
                g2.fillRoundRect(0, 0, width, height, height, height)
                g2.color = Color(purple.red, purple.green, purple.blue, 140)
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, height, height)
                super.paintComponent(g2)
            } finally {
                g2.dispose()
            }
        }
    }

    internal class StreakResultBadge(val streak: Int, val lang: AppLanguage) : JBLabel(
        if (lang == AppLanguage.EN) "🔥 $streak Streak" else "🔥 $streak Günlük Seri"
    ) {
        init {
            font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
            foreground = Color(0xF5, 0x9E, 0x0B)
            border = JBUI.Borders.empty(JBUI.scale(2), JBUI.scale(8))
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val amber = Color(0xF5, 0x9E, 0x0B)
                g2.color = Color(amber.red, amber.green, amber.blue, 25)
                g2.fillRoundRect(0, 0, width, height, height, height)
                g2.color = Color(amber.red, amber.green, amber.blue, 140)
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, height, height)
                super.paintComponent(g2)
            } finally {
                g2.dispose()
            }
        }
    }
}
