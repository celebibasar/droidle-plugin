package app.droidle.plugin.ui

import app.droidle.plugin.model.Question
import app.droidle.plugin.model.VerifyResponse
import app.droidle.plugin.service.DroidleService
import app.droidle.plugin.state.StateManager
import com.intellij.icons.AllIcons
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
 * Native quiz screen — replaces WebView for the active quiz flow.
 *
 * Layout:
 *   ┌─────────────────────────────────┐
 *   │ Header: progress dots + category│
 *   ├─────────────────────────────────┤
 *   │ (scrollable)                    │
 *   │  Question text                  │
 *   │  [optional code snippet]        │
 *   │  A B C D option cards           │
 *   │  Explanation (after answer)     │
 *   ├─────────────────────────────────┤
 *   │ Footer: [Next →] / [Results →] │
 *   └─────────────────────────────────┘
 */
class QuizScreen(
    private val questions: List<Question> = emptyList(),
    private val onComplete: (correctCount: Int, questions: List<Question>) -> Unit,
    private val onError: (String) -> Unit
) : JPanel(BorderLayout(0, 0)) {

    // ── State ──────────────────────────────────────────────────────────────────
    private var currentIndex = 0
    private var correctCount = 0
    private var answered     = false
    private var selectedKey: String? = null

    // ── Header widgets ─────────────────────────────────────────────────────────
    private val dotsPanel    = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0))
    private val categoryLabel = JBLabel("").apply {
        font       = JBUI.Fonts.smallFont()
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
    }

    // ── Content widgets ────────────────────────────────────────────────────────
    private val questionLabel = JBLabel("").apply {
        font = JBUI.Fonts.label(13f)
    }
    private val codeArea = JBTextArea(4, 0).apply {
        isEditable   = false
        lineWrap     = false
        font         = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11f).toInt())
        border       = JBUI.Borders.empty(JBUI.scale(8))
        background   = JBColor(Color(0x1E1E1E), Color(0x1E1E1E))
        foreground   = JBColor(Color(0xD4D4D4), Color(0xD4D4D4))
        isVisible    = false
    }
    private val optionCards  = mutableListOf<OptionCard>()
    private val optionsPanel = JPanel().apply {
        layout  = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }
    private val explanationPanel = ExplanationPanel().apply { isVisible = false }

    // ── Footer widgets ─────────────────────────────────────────────────────────
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

        if (questions.isNotEmpty()) {
            showQuestion(0)
        }
    }

    // ── Layout assembly ────────────────────────────────────────────────────────

    private fun buildLayout() {
        // — Header —
        val headerPanel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border   = CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
                JBUI.Borders.empty(JBUI.scale(8), JBUI.scale(12))
            )
            isOpaque = false
            add(dotsPanel,     BorderLayout.WEST)
            add(categoryLabel, BorderLayout.EAST)
        }

        // — Scrollable body —
        val bodyPanel = JPanel().apply {
            layout   = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border   = JBUI.Borders.empty(JBUI.scale(14), JBUI.scale(12), JBUI.scale(8), JBUI.scale(12))

            add(questionLabel)
            add(Box.createVerticalStrut(JBUI.scale(12)))

            val codeScroll = JBScrollPane(codeArea).apply {
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                verticalScrollBarPolicy   = JScrollPane.VERTICAL_SCROLLBAR_NEVER
                maximumSize               = Dimension(Int.MAX_VALUE, JBUI.scale(120))
                alignmentX                = Component.LEFT_ALIGNMENT
                isVisible                 = false
                border                    = RoundedLineBorder(JBColor(Color(0x3C3C3C), Color(0x3C3C3C)), JBUI.scale(6))
            }
            add(codeScroll)
            add(Box.createVerticalStrut(JBUI.scale(10)))
            add(optionsPanel)
            add(Box.createVerticalStrut(JBUI.scale(10)))
            add(explanationPanel)
        }

        val scrollPane = JBScrollPane(bodyPanel).apply {
            border                    = JBUI.Borders.empty()
            verticalScrollBarPolicy   = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        // — Footer —
        val footerPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), JBUI.scale(6))).apply {
            border   = CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
                JBUI.Borders.empty(JBUI.scale(4), JBUI.scale(8))
            )
            isOpaque = false
            add(checkingIcon)
            add(nextButton)
        }

        add(headerPanel,  BorderLayout.NORTH)
        add(scrollPane,   BorderLayout.CENTER)
        add(footerPanel,  BorderLayout.SOUTH)
    }

    // ── Question rendering ─────────────────────────────────────────────────────

    private fun showQuestion(index: Int) {
        currentIndex = index
        answered     = false
        selectedKey  = null

        val q = questions[index]

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

        // Question text — HTML wrap for multi-line
        questionLabel.text = "<html><body style='width:100%'>${q.displayQuestion()}</body></html>"
        questionLabel.alignmentX = Component.LEFT_ALIGNMENT

        // Code snippet
        val snippet = q.code_snippet
        if (!snippet.isNullOrBlank()) {
            codeArea.text   = snippet.trim()
            codeArea.isVisible = true
            // find parent JBScrollPane and sync visibility
            (codeArea.parent?.parent as? JScrollPane)?.isVisible = true
        } else {
            codeArea.isVisible = false
            (codeArea.parent?.parent as? JScrollPane)?.isVisible = false
        }

        // Options
        optionsPanel.removeAll()
        optionCards.clear()
        val opts = q.displayOptions()
        listOf("A", "B", "C", "D").forEach { key ->
            val text = opts[key] ?: return@forEach
            val card = OptionCard(key = key, text = text) { onOptionSelected(key, q) }
            optionCards.add(card)
            optionsPanel.add(card)
            optionsPanel.add(Box.createVerticalStrut(JBUI.scale(6)))
        }

        explanationPanel.isVisible = false
        nextButton.isVisible  = false
        nextButton.isEnabled  = false
        nextButton.text       = if (index < questions.size - 1) "Sonraki Soru  →" else "Sonuçları Gör  →"
        checkingIcon.isVisible = false

        revalidate()
        repaint()
    }

    // ── Answer handling ────────────────────────────────────────────────────────

    private fun onOptionSelected(key: String, question: Question) {
        if (answered) return
        answered    = true
        selectedKey = key

        // Disable all cards while checking
        optionCards.forEach { it.setEnabled(false) }
        checkingIcon.isVisible = true

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { DroidleService.getInstance().verifyAnswer(question.id, key) }
            SwingUtilities.invokeLater {
                checkingIcon.isVisible = false
                result.fold(
                    onSuccess = { applyResult(it, key) },
                    onFailure = { applyLocalResult(key, question) }
                )
            }
        }
    }

    private fun applyResult(result: VerifyResponse, selected: String) {
        if (result.isCorrect) correctCount++
        val explanation = result.explanation_tr ?: result.explanation
        highlightCards(result.correctAnswer, selected)
        explanationPanel.show(correct = result.isCorrect, text = explanation)
        nextButton.isVisible = true
        nextButton.isEnabled = true
        revalidate(); repaint()
    }

    private fun applyLocalResult(selected: String, question: Question) {
        val correctKey = question.correct_answer ?: selected
        val isCorrect  = selected.equals(correctKey, ignoreCase = true)
        if (isCorrect) correctCount++
        val explanation = question.explanation_tr ?: question.explanation ?: ""
        highlightCards(correctKey, selected)
        explanationPanel.show(correct = isCorrect, text = explanation)
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
            onComplete(correctCount, questions)
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
        font      = JBUI.Fonts.label(12f).deriveFont(Font.BOLD)
        preferredSize = Dimension(JBUI.scale(22), JBUI.scale(22))
        horizontalAlignment = SwingConstants.CENTER
        verticalAlignment   = SwingConstants.CENTER
    }
    private val textLabel = JBLabel("<html><body style='width:100%'>$text</body></html>")

    init {
        border    = CompoundBorder(
            RoundedLineBorder(borderColor(), JBUI.scale(6)),
            JBUI.Borders.empty(JBUI.scale(9), JBUI.scale(10))
        )
        isOpaque  = false
        cursor    = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        alignmentX = Component.LEFT_ALIGNMENT
        // Do NOT set maximumSize here — children are not yet added, so
        // preferredSize.height == 0 and the card collapses to zero height.
        // BoxLayout will size cards naturally; max width is handled via
        // the override below.

        add(keyLabel,  BorderLayout.WEST)
        add(textLabel, BorderLayout.CENTER)

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent)  { if (state == OptionState.DEFAULT) { state = OptionState.HOVER;   repaint() } }
            override fun mouseExited(e: MouseEvent)   { if (state == OptionState.HOVER)   { state = OptionState.DEFAULT; repaint() } }
            override fun mouseClicked(e: MouseEvent)  { if (isEnabled) onClick(key) }
        })
    }

    fun applyAnswerState(isCorrect: Boolean, isSelected: Boolean) {
        state = when {
            isCorrect              -> OptionState.CORRECT
            isSelected && !isCorrect -> OptionState.WRONG
            else                   -> OptionState.DEFAULT
        }
        repaint()
    }

    // BoxLayout calls getMaximumSize() to cap card height. Override so the
    // card is always exactly its preferred height (computed after layout) and
    // fills the full available width — this prevents option D from being clipped.
    override fun getMaximumSize(): Dimension =
        Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = bgColor()
        g2.fillRoundRect(0, 0, width, height, JBUI.scale(6), JBUI.scale(6))
        super.paintComponent(g)
        // Update border colour on repaint
        border = CompoundBorder(
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
        else -> keyFg()
    }
}

// ── Explanation panel ──────────────────────────────────────────────────────────

private class ExplanationPanel : JPanel(BorderLayout(JBUI.scale(8), 0)) {

    private val iconLabel = JBLabel()
    private val textLabel = JBLabel()

    init {
        isOpaque  = false
        alignmentX = Component.LEFT_ALIGNMENT

        textLabel.apply {
            font = JBUI.Fonts.smallFont()
        }

        add(iconLabel, BorderLayout.WEST)
        add(textLabel, BorderLayout.CENTER)
    }

    fun show(correct: Boolean, text: String) {
        iconLabel.icon       = if (correct) AllIcons.General.InspectionsOK else AllIcons.General.BalloonError
        textLabel.text       = "<html><body style='width:100%'>$text</body></html>"
        textLabel.foreground = if (correct)
            JBColor(Color(0x15803D), Color(0x4ADE80))
        else
            JBColor(Color(0xB91C1C), Color(0xF87171))
        isVisible = true
        revalidate(); repaint()
    }
}

// ── Progress dot ───────────────────────────────────────────────────────────────

private enum class DotState { TODO, ACTIVE, DONE }

private class DotIndicator(state: DotState) : JComponent() {

    private val dotState = state
    private val size = JBUI.scale(8)

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
        val d = if (dotState == DotState.ACTIVE) size else size - JBUI.scale(2)
        val off = (size - d) / 2
        g2.fillOval(off, off, d, d)
    }
}
