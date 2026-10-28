package app.droidle.plugin.ui

import app.droidle.plugin.model.DocLink
import app.droidle.plugin.model.Question
import app.droidle.plugin.model.VerifyResponse
import app.droidle.plugin.service.DroidleService
import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.state.CategoryResult
import app.droidle.plugin.state.StateManager
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.CompoundBorder

/**
 * Native quiz screen.
 *
 * State machine (mirrors web src/lib/quiz-machine.ts quizReducer):
 *   DEFAULT → option clicked → [server verify] → ANSWERED → next → next question
 *   On last question → ANSWERED → onComplete(correctCount, questions, earnedXp, categoryResults)
 *
 * Key invariants:
 *  - `correctCount` and `earnedXp` are only incremented from server-verified results.
 *  - Fallback (local) evaluation uses question.correct_answer; XP uses question.xpValue.
 *  - `answered` flag is set before the network call so rapid clicks don't double-count.
 *  - `categoryResults` accumulates per-category stats for StateManager.recordQuizCompletion().
 */
class QuizScreen(
    private val questions: List<Question> = emptyList(),
    private val onComplete: (
        correctCount: Int,
        questions: List<Question>,
        earnedXp: Int,
        categoryResults: Map<String, CategoryResult>
    ) -> Unit,
    private val onError: (String) -> Unit
) : JPanel(BorderLayout(0, 0)) {

    // ── Quiz state ─────────────────────────────────────────────────────────────
    private var currentIndex    = 0
    private var correctCount    = 0
    private var earnedXp        = 0
    private var answered        = false
    private var selectedKey: String? = null

    /** category → CategoryResult, accumulated as questions are answered. */
    private val categoryResults = mutableMapOf<String, CategoryResult>()

    // ── Header ─────────────────────────────────────────────────────────────────
    private val dotsPanel     = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
    private val categoryLabel = JBLabel("").apply {
        font       = JBUI.Fonts.smallFont()
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
    }

    // ── Body ───────────────────────────────────────────────────────────────────
    private val questionLabel = JBLabel("").apply { font = JBUI.Fonts.label(13f) }

    private val codeArea = JBTextArea(4, 0).apply {
        isEditable  = false
        lineWrap    = false
        font        = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11f).toInt())
        border      = JBUI.Borders.empty(JBUI.scale(8))
        background  = JBColor(Color(0x1E1E1E), Color(0x1E1E1E))
        foreground  = JBColor(Color(0xD4D4D4), Color(0xD4D4D4))
        isVisible   = false
    }

    private val optionCards  = mutableListOf<OptionCard>()
    private val optionsPanel = JPanel().apply {
        layout   = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val explanationPanel = ExplanationPanel().apply { isVisible = false }

    // ── XP chip (shown after answer) ───────────────────────────────────────────
    private val xpChip = XpChip(0).apply { isVisible = false }

    // ── Footer ─────────────────────────────────────────────────────────────────
    private val nextButton = JButton("Sonraki Soru").apply {
        isEnabled = false
        isVisible = false
        addActionListener { advance() }
    }
    private val checkingIcon = JBLabel(AnimatedIcon.Default()).apply { isVisible = false }

    // ── Init ───────────────────────────────────────────────────────────────────
    init {
        border = JBUI.Borders.empty()
        buildLayout()
        if (questions.isNotEmpty()) showQuestion(0)
    }

    // ── Layout ─────────────────────────────────────────────────────────────────

    private fun buildLayout() {
        val headerPanel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(
                    0, 0, 1, 0,
                    JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
                ),
                JBUI.Borders.empty(JBUI.scale(8), JBUI.scale(12))
            )
            isOpaque = false
            add(dotsPanel,     BorderLayout.WEST)
            add(categoryLabel, BorderLayout.EAST)
        }

        val bodyPanel = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border   = JBUI.Borders.empty(
                JBUI.scale(14), JBUI.scale(12),
                JBUI.scale(8),  JBUI.scale(12)
            )

            add(questionLabel)
            add(Box.createVerticalStrut(JBUI.scale(12)))

            val codeScroll = JBScrollPane(codeArea).apply {
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                verticalScrollBarPolicy   = JScrollPane.VERTICAL_SCROLLBAR_NEVER
                preferredSize             = Dimension(Int.MAX_VALUE, JBUI.scale(130))
                minimumSize               = Dimension(0, JBUI.scale(60))
                alignmentX                = Component.LEFT_ALIGNMENT
                isVisible                 = false
                border                    = RoundedLineBorder(
                    JBColor(Color(0x3C3C3C), Color(0x3C3C3C)), JBUI.scale(6)
                )
            }
            add(codeScroll)
            add(Box.createVerticalStrut(JBUI.scale(10)))
            add(optionsPanel)
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(xpChip)
            add(Box.createVerticalStrut(JBUI.scale(8)))
            add(explanationPanel)
        }

        val scrollPane = JBScrollPane(bodyPanel).apply {
            border                    = JBUI.Borders.empty()
            verticalScrollBarPolicy   = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        val footerPanel = JPanel(
            FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), JBUI.scale(6))
        ).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(
                    1, 0, 0, 0,
                    JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
                ),
                JBUI.Borders.empty(JBUI.scale(4), JBUI.scale(8))
            )
            isOpaque = false
            add(checkingIcon)
            add(nextButton)
        }

        add(headerPanel, BorderLayout.NORTH)
        add(scrollPane,  BorderLayout.CENTER)
        add(footerPanel, BorderLayout.SOUTH)
    }

    // ── Question rendering ─────────────────────────────────────────────────────

    private fun showQuestion(index: Int) {
        currentIndex = index
        answered     = false
        selectedKey  = null

        val q    = questions[index]
        val lang = StateManager.getInstance().language

        // Progress dots
        dotsPanel.removeAll()
        questions.forEachIndexed { i, _ ->
            dotsPanel.add(DotIndicator(state = when {
                i < index  -> DotState.DONE
                i == index -> DotState.ACTIVE
                else       -> DotState.TODO
            }))
        }

        categoryLabel.text = q.category
        questionLabel.text = "<html><body style='width:100%'>${q.displayQuestion(lang)}</body></html>"
        questionLabel.alignmentX = Component.LEFT_ALIGNMENT

        // Code snippet
        val snippet = q.code_snippet
        if (!snippet.isNullOrBlank()) {
            codeArea.text      = snippet.trim()
            codeArea.isVisible = true
            (codeArea.parent?.parent as? JScrollPane)?.isVisible = true
        } else {
            codeArea.isVisible = false
            (codeArea.parent?.parent as? JScrollPane)?.isVisible = false
        }

        // Option cards
        optionsPanel.removeAll()
        optionCards.clear()
        val opts = q.displayOptions(lang)
        listOf("A", "B", "C", "D").forEach { key ->
            val text = opts[key] ?: return@forEach
            val card = OptionCard(key = key, text = text) { onOptionSelected(key, q) }
            optionCards.add(card)
            optionsPanel.add(card)
            optionsPanel.add(Box.createVerticalStrut(JBUI.scale(6)))
        }

        xpChip.isVisible         = false
        explanationPanel.isVisible = false
        nextButton.isVisible     = false
        nextButton.isEnabled     = false
        nextButton.text          = nextButtonLabel(index, lang)
        checkingIcon.isVisible   = false

        revalidate()
        repaint()
    }

    private fun nextButtonLabel(index: Int, lang: AppLanguage): String =
        if (index < questions.size - 1)
            if (lang == AppLanguage.EN) "Next Question  →" else "Sonraki Soru  →"
        else
            if (lang == AppLanguage.EN) "See Results  →" else "Sonuçları Gör  →"

    // ── Language refresh (no index/score reset) ────────────────────────────────

    /**
     * Call when language is toggled mid-quiz.
     * Updates visible text in-place — does NOT reset index, score, or answered state.
     */
    fun refreshLanguage() {
        if (questions.isEmpty()) return
        val q    = questions[currentIndex]
        val lang = StateManager.getInstance().language

        questionLabel.text = "<html><body style='width:100%'>${q.displayQuestion(lang)}</body></html>"

        val opts = q.displayOptions(lang)
        if (!answered) {
            // Rebuild option cards (none are in answered state yet)
            optionsPanel.removeAll()
            optionCards.clear()
            listOf("A", "B", "C", "D").forEach { key ->
                val text = opts[key] ?: return@forEach
                val card = OptionCard(key = key, text = text) { onOptionSelected(key, q) }
                optionCards.add(card)
                optionsPanel.add(card)
                optionsPanel.add(Box.createVerticalStrut(JBUI.scale(6)))
            }
        } else {
            // Card is in answered state — only update text labels, keep highlight colours
            optionCards.forEachIndexed { i, card ->
                val text = opts[card.key] ?: return@forEachIndexed
                card.updateText(text)
            }
        }

        if (nextButton.isVisible) {
            nextButton.text = nextButtonLabel(currentIndex, lang)
        }

        revalidate()
        repaint()
    }

    // ── Answer flow ────────────────────────────────────────────────────────────

    private fun onOptionSelected(key: String, question: Question) {
        if (answered) return
        answered    = true
        selectedKey = key

        optionCards.forEach { it.setEnabled(false) }
        checkingIcon.isVisible = true

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { DroidleService.getInstance().verifyAnswer(question.id, key) }
            SwingUtilities.invokeLater {
                checkingIcon.isVisible = false
                result.fold(
                    onSuccess = { applyVerifiedResult(it, key, question) },
                    onFailure = { applyLocalResult(key, question) }
                )
            }
        }
    }

    private fun applyVerifiedResult(result: VerifyResponse, selected: String, question: Question) {
        val lang = StateManager.getInstance().language
        val xp   = result.xpEarned

        if (result.isCorrect) {
            correctCount++
            earnedXp += xp
        }

        // Accumulate category breakdown
        val cat = result.category.ifBlank { question.category }
        categoryResults[cat] = CategoryResult(
            attempted = (categoryResults[cat]?.attempted ?: 0) + 1,
            correct   = (categoryResults[cat]?.correct   ?: 0) + (if (result.isCorrect) 1 else 0)
        )

        val explanation = when (lang) {
            AppLanguage.EN -> result.explanation_en ?: result.explanation_tr ?: result.explanation
            AppLanguage.TR -> result.explanation_tr ?: result.explanation
        }

        highlightCards(result.correctAnswer, selected)
        xpChip.update(xp, result.isCorrect)
        xpChip.isVisible = true
        explanationPanel.show(
            correct   = result.isCorrect,
            text      = explanation,
            docLinks  = result.docLinks
        )
        nextButton.isVisible = true
        nextButton.isEnabled = true
        revalidate(); repaint()
    }

    private fun applyLocalResult(selected: String, question: Question) {
        val correctKey = question.correct_answer ?: selected
        val isCorrect  = selected.equals(correctKey, ignoreCase = true)
        val xp         = if (isCorrect) question.xpValue else 0

        if (isCorrect) {
            correctCount++
            earnedXp += xp
        }

        val cat = question.category
        categoryResults[cat] = CategoryResult(
            attempted = (categoryResults[cat]?.attempted ?: 0) + 1,
            correct   = (categoryResults[cat]?.correct   ?: 0) + (if (isCorrect) 1 else 0)
        )

        val lang        = StateManager.getInstance().language
        val explanation = question.displayExplanation(lang) ?: ""

        highlightCards(correctKey, selected)
        xpChip.update(xp, isCorrect)
        xpChip.isVisible = true
        explanationPanel.show(correct = isCorrect, text = explanation, docLinks = question.doc_links)
        nextButton.isVisible = true
        nextButton.isEnabled = true
        revalidate(); repaint()
    }

    private fun highlightCards(correctKey: String, selectedKey: String) {
        optionCards.forEach { card ->
            card.applyAnswerState(
                isCorrect  = card.key.equals(correctKey,  ignoreCase = true),
                isSelected = card.key.equals(selectedKey, ignoreCase = true)
            )
        }
    }

    private fun advance() {
        val next = currentIndex + 1
        if (next >= questions.size) {
            onComplete(correctCount, questions, earnedXp, categoryResults)
        } else {
            showQuestion(next)
        }
    }
}

// ── Option card ────────────────────────────────────────────────────────────────

private enum class OptionState { DEFAULT, HOVER, CORRECT, WRONG }

private class OptionCard(
    val key: String,
    text: String,
    onClick: (String) -> Unit
) : JPanel(BorderLayout(JBUI.scale(10), 0)) {

    private var state: OptionState = OptionState.DEFAULT

    private val keyLabel = JBLabel(key).apply {
        font = JBUI.Fonts.label(12f).deriveFont(Font.BOLD)
        preferredSize       = Dimension(JBUI.scale(22), JBUI.scale(22))
        horizontalAlignment = SwingConstants.CENTER
        verticalAlignment   = SwingConstants.CENTER
    }
    val textLabel = JBLabel("<html><body style='width:100%'>$text</body></html>")

    init {
        border     = CompoundBorder(
            RoundedLineBorder(borderColor(), JBUI.scale(6)),
            JBUI.Borders.empty(JBUI.scale(9), JBUI.scale(10))
        )
        isOpaque   = false
        cursor     = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        alignmentX = Component.LEFT_ALIGNMENT

        add(keyLabel,  BorderLayout.WEST)
        add(textLabel, BorderLayout.CENTER)

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                if (state == OptionState.DEFAULT) { state = OptionState.HOVER; repaint() }
            }
            override fun mouseExited(e: MouseEvent) {
                if (state == OptionState.HOVER) { state = OptionState.DEFAULT; repaint() }
            }
            override fun mouseClicked(e: MouseEvent) { if (isEnabled) onClick(key) }
        })
    }

    fun applyAnswerState(isCorrect: Boolean, isSelected: Boolean) {
        state = when {
            isCorrect                -> OptionState.CORRECT
            isSelected && !isCorrect -> OptionState.WRONG
            else                     -> OptionState.DEFAULT
        }
        repaint()
    }

    fun updateText(newText: String) {
        textLabel.text = "<html><body style='width:100%'>$newText</body></html>"
        revalidate(); repaint()
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = bgColor()
        g2.fillRoundRect(0, 0, width, height, JBUI.scale(6), JBUI.scale(6))
        super.paintComponent(g)
        border             = CompoundBorder(
            RoundedLineBorder(borderColor(), JBUI.scale(6)),
            JBUI.Borders.empty(JBUI.scale(9), JBUI.scale(10))
        )
        keyLabel.foreground  = keyFg()
        textLabel.foreground = textFg()
    }

    private fun bgColor() = when (state) {
        OptionState.DEFAULT -> JBColor(Color(0xF9F9F9), Color(0x2A2A2A))
        OptionState.HOVER   -> JBColor(Color(0xEDFDF8), Color(0x1E3A2F))
        OptionState.CORRECT -> JBColor(Color(0xDCFCE7), Color(0x14532D))
        OptionState.WRONG   -> JBColor(Color(0xFEE2E2), Color(0x450A0A))
    }
    private fun borderColor() = when (state) {
        OptionState.DEFAULT -> JBColor(Color(0xE4E4E7), Color(0x3F3F46))
        OptionState.HOVER   -> JBColor(Color(0x10B981), Color(0x059669))
        OptionState.CORRECT -> JBColor(Color(0x22C55E), Color(0x16A34A))
        OptionState.WRONG   -> JBColor(Color(0xEF4444), Color(0xDC2626))
    }
    private fun keyFg() = when (state) {
        OptionState.CORRECT -> JBColor(Color(0x15803D), Color(0x4ADE80))
        OptionState.WRONG   -> JBColor(Color(0xB91C1C), Color(0xF87171))
        else                -> JBUI.CurrentTheme.Label.foreground()
    }
    private fun textFg() = when (state) {
        OptionState.DEFAULT, OptionState.HOVER -> JBUI.CurrentTheme.Label.foreground()
        else                                   -> keyFg()
    }
}

// ── XP chip (post-answer feedback) ────────────────────────────────────────────

private class XpChip(private var xp: Int) : JComponent() {

    private val green = Color(0x10, 0xB9, 0x81)
    private val red   = Color(0xEF, 0x44, 0x44)
    private var correct = false

    private val arc  = JBUI.scale(12)
    private val hPad = JBUI.scale(10)
    private val vPad = JBUI.scale(4)

    init {
        font      = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
        alignmentX = Component.LEFT_ALIGNMENT
        updateSize()
    }

    fun update(newXp: Int, isCorrect: Boolean) {
        xp      = newXp
        correct = isCorrect
        updateSize()
        repaint()
    }

    private fun label() = if (correct) "+${xp} XP" else "0 XP"

    private fun updateSize() {
        val fm = getFontMetrics(font ?: JBUI.Fonts.smallFont().deriveFont(Font.BOLD))
        val w  = fm.stringWidth(label()) + hPad * 2
        val h  = fm.height + vPad * 2
        preferredSize = Dimension(w, h)
        minimumSize   = preferredSize
    }

    override fun paintComponent(g: Graphics) {
        val g2   = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val col  = if (correct) green else red
        g2.color = Color(col.red, col.green, col.blue, 20)
        g2.fillRoundRect(0, 0, width, height, arc, arc)
        g2.color = Color(col.red, col.green, col.blue, 160)
        g2.stroke = BasicStroke(1f)
        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
        g2.color = col
        g2.font  = font ?: JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
        val fm   = g2.fontMetrics
        val text = label()
        val tx   = (width - fm.stringWidth(text)) / 2
        val ty   = (height + fm.ascent - fm.descent) / 2
        g2.drawString(text, tx, ty)
    }
}

// ── Explanation panel ──────────────────────────────────────────────────────────

private class ExplanationPanel : JPanel() {

    private val iconLabel  = JBLabel()
    private val textLabel  = JBLabel()
    private val linksPanel = JPanel().apply {
        layout   = FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)
        isOpaque = false
    }

    init {
        layout     = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque   = false
        alignmentX = Component.LEFT_ALIGNMENT

        val topRow = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            isOpaque   = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(iconLabel, BorderLayout.WEST)
            add(textLabel, BorderLayout.CENTER)
        }
        textLabel.font = JBUI.Fonts.smallFont()

        add(topRow)
        add(Box.createVerticalStrut(JBUI.scale(6)))
        add(linksPanel)
    }

    fun show(correct: Boolean, text: String, docLinks: List<DocLink> = emptyList()) {
        iconLabel.icon       = if (correct) AllIcons.General.InspectionsOK else AllIcons.General.BalloonError
        textLabel.text       = "<html><body style='width:100%'>$text</body></html>"
        textLabel.foreground = if (correct)
            JBColor(Color(0x15803D), Color(0x4ADE80))
        else
            JBColor(Color(0xB91C1C), Color(0xF87171))

        linksPanel.removeAll()
        if (docLinks.isNotEmpty()) {
            linksPanel.add(JBLabel("Kaynaklar:").apply {
                font       = JBUI.Fonts.smallFont()
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
            })
            docLinks.forEach { link ->
                linksPanel.add(JButton(link.title).apply {
                    font                = JBUI.Fonts.smallFont()
                    isBorderPainted     = false
                    isContentAreaFilled = false
                    isFocusPainted      = false
                    foreground          = JBColor(Color(0x2563EB), Color(0x60A5FA))
                    cursor              = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    toolTipText         = link.url
                    addActionListener { BrowserUtil.browse(link.url) }
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseEntered(e: MouseEvent) {
                            foreground = JBColor(Color(0x1D4ED8), Color(0x93C5FD))
                        }
                        override fun mouseExited(e: MouseEvent) {
                            foreground = JBColor(Color(0x2563EB), Color(0x60A5FA))
                        }
                    })
                })
            }
        }
        linksPanel.isVisible = docLinks.isNotEmpty()

        isVisible = true
        revalidate(); repaint()
    }
}

// ── Progress dots ──────────────────────────────────────────────────────────────

private enum class DotState { TODO, ACTIVE, DONE }

private class DotIndicator(state: DotState) : JComponent() {
    private val dotState = state
    private val size     = JBUI.scale(8)

    init {
        preferredSize = Dimension(size, size)
        minimumSize   = preferredSize
        maximumSize   = preferredSize
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = when (dotState) {
            DotState.ACTIVE -> JBColor(Color(0x10B981), Color(0x10B981))
            DotState.DONE   -> JBColor(Color(0x6EE7B7), Color(0x34D399))
            DotState.TODO   -> JBColor(Color(0xD4D4D8), Color(0x52525B))
        }
        val d   = if (dotState == DotState.ACTIVE) size else size - JBUI.scale(2)
        val off = (size - d) / 2
        g2.fillOval(off, off, d, d)
    }
}
