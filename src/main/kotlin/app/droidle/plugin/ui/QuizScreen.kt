package app.droidle.plugin.ui

import app.droidle.plugin.model.*
import app.droidle.plugin.service.DroidleService
import app.droidle.plugin.service.SupabaseService
import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.state.CategoryResult
import app.droidle.plugin.state.StateManager
import app.droidle.plugin.ui.components.AutoWrappingHtmlLabel
import app.droidle.plugin.ui.components.ModernCodeSnippet
import app.droidle.plugin.ui.components.ModernExplanationCard
import app.droidle.plugin.ui.components.ModernOptionCard
import app.droidle.plugin.ui.components.QuestionReportButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeListener
import java.time.Instant
import javax.swing.*
import javax.swing.border.CompoundBorder

/**
 * Modern Native Quiz Screen with JetBrains New UI / Linear aesthetic.
 *
 * Features:
 * - Perfectly left-aligned layout with 100% horizontal expansion (no rightward shift).
 * - 0ms Instant Feedback on option click (emerald green if correct, rose red if wrong).
 * - ModernOptionCards with keyboard badges [A], [B], [C], [D] and keyboard shortcuts.
 * - IntelliJ EditorTextField Kotlin code snippets.
 * - Technical explanation card with syntax-highlighted code chips and official doc link.
 * - Prominent full-width Next Question action button.
 * - Instant partial progress synchronization to Supabase daily_submissions on each answer.
 */
class QuizScreen(
    private val questions: List<Question> = emptyList(),
    private val initialIndex: Int = 0,
    private val initialSelectedOptions: List<Int> = emptyList(),
    private val initialAnswers: Map<String, UserAnswerPayload> = emptyMap(),
    private val project: Project? = null,
    private val onComplete: (
        correctCount: Int,
        questions: List<Question>,
        earnedXp: Int,
        selectedOptions: List<Int>,
        answers: Map<String, UserAnswerPayload>,
        categoryResults: Map<String, CategoryResult>
    ) -> Unit,
    private val onError: (String) -> Unit
) : JPanel(BorderLayout(0, 0)) {

    private var currentIndex = initialIndex.coerceIn(0, (questions.size - 1).coerceAtLeast(0))
    private var correctCount = 0
    private var earnedXp = 0
    private var answered = false
    private var selectedKey: String? = null

    // Track answers and selected options for 100% Supabase synchronization
    private val selectedOptionsList = MutableList(questions.size) { -1 }
    private val answersMap = mutableMapOf<String, UserAnswerPayload>()
    private val categoryResults = mutableMapOf<String, CategoryResult>()

    // Sync polling & focus listeners for real-time cross-device sync
    private var syncTimer: javax.swing.Timer? = null
    private var isDisposed = false
    private var isCheckingSync = false
    @Volatile private var isUpdatingLocally = false
    private var windowFocusListener: WindowFocusListener? = null
    private var windowPropertyListener: PropertyChangeListener? = null
    private var hierarchyListener: HierarchyListener? = null

    var overrideLanguage: AppLanguage? = null
    var isQuestionReportedProvider: ((String) -> Boolean)? = null

    // UI Components
    private fun currentLanguage(): AppLanguage = overrideLanguage ?: try {
        StateManager.getInstance().language
    } catch (_: Throwable) {
        AppLanguage.TR
    }

    private fun isReported(questionId: String): Boolean {
        return isQuestionReportedProvider?.invoke(questionId) ?: try {
            StateManager.getInstance().isQuestionReported(questionId)
        } catch (_: Throwable) {
            false
        }
    }

    private val dotsPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }
    private val categoryLabel = JBLabel("").apply {
        font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
    }
    private val difficultyPill = object : JBLabel("") {
        var pillColor: Color = Color(0x10, 0xB9, 0x81)

        init {
            font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
            border = JBUI.Borders.empty(JBUI.scale(2), JBUI.scale(8))
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = Color(pillColor.red, pillColor.green, pillColor.blue, 30)
                g2.fillRoundRect(0, 0, width, height, height, height)
                g2.color = Color(pillColor.red, pillColor.green, pillColor.blue, 180)
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, height, height)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }

    private val reportButton = QuestionReportButton(
        currentQuestion = questions.getOrNull(currentIndex),
        currentLang = currentLanguage(),
        project = project,
        compact = false,
        isReportedChecker = { isReported(it) }
    ) {
        explanationCard.refreshReportedState(true)
    }

    private val questionLabel = AutoWrappingHtmlLabel("").apply {
        font = JBUI.Fonts.label(13.5f).deriveFont(Font.BOLD)
        horizontalAlignment = SwingConstants.LEFT
        verticalAlignment = SwingConstants.TOP
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val questionWrapper = object : JPanel(BorderLayout()) {
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }.apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        add(questionLabel, BorderLayout.CENTER)
    }

    private val codeSnippetComponent = ModernCodeSnippet(project).apply {
        alignmentX = Component.LEFT_ALIGNMENT
    }
    private val codeSnippetBottomGap = vGap(12)

    private val optionCards = mutableListOf<ModernOptionCard>()
    private val optionsPanel = object : JPanel() {
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }.apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val explanationCard = ModernExplanationCard().apply {
        isVisible = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val nextButton = object : JButton("Sonraki Soru →") {
        init {
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            foreground = Color.WHITE
            font = JBUI.Fonts.label(13f).deriveFont(Font.BOLD)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(0, JBUI.scale(40))
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(40))
            alignmentX = Component.LEFT_ALIGNMENT
            isEnabled = false
            isVisible = false
            addActionListener { advance() }
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val bg = if (model.isPressed) Color(0x05, 0x96, 0x69)
                else if (model.isRollover) Color(0x0D, 0xA2, 0x70)
                else Color(0x10, 0xB9, 0x81)
                g2.color = bg
                g2.fillRoundRect(0, 0, width, height, JBUI.scale(10), JBUI.scale(10))
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }

    private val footerPanel = object : JPanel(BorderLayout()) {
        override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
    }.apply {
        border = CompoundBorder(
            BorderFactory.createMatteBorder(
                1, 0, 0, 0,
                JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
            ),
            JBUI.Borders.empty(JBUI.scale(10), JBUI.scale(14), JBUI.scale(10), JBUI.scale(14))
        )
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        isVisible = false
        add(nextButton, BorderLayout.CENTER)
    }

    init {
        border = JBUI.Borders.empty()
        isOpaque = false

        val optionKeys = listOf("A", "B", "C", "D")

        // Seed initial answers and options if resuming
        if (initialSelectedOptions.isNotEmpty()) {
            initialSelectedOptions.forEachIndexed { i, opt ->
                if (i < selectedOptionsList.size) selectedOptionsList[i] = opt
            }
        }
        answersMap.putAll(initialAnswers)

        // Fully reconcile any already-answered questions
        questions.forEachIndexed { i, q ->
            var ans = answersMap[q.id] ?: answersMap[i.toString()]
            var optIdx = selectedOptionsList.getOrNull(i) ?: -1

            if (optIdx !in 0..3 && ans != null && !ans.selectedOption.isNullOrBlank()) {
                optIdx = optionKeys.indexOf(ans.selectedOption!!.uppercase())
                if (optIdx in 0..3) selectedOptionsList[i] = optIdx
            }

            val chosenKey = ans?.selectedOption ?: (if (optIdx in 0..3) optionKeys[optIdx] else null)
            val correctKey = q.resolvedCorrectAnswer ?: ans?.correctAnswer ?: "A"
            val isCorrect = ans?.isCorrect ?: (chosenKey?.equals(correctKey, ignoreCase = true) == true)
            val xp = if (isCorrect) (ans?.xpEarned ?: q.xpValue) else 0

            if (optIdx in 0..3 && ans == null && chosenKey != null) {
                ans = UserAnswerPayload(
                    questionId = q.id,
                    selectedOption = chosenKey,
                    isCorrect = isCorrect,
                    correctAnswer = correctKey,
                    category = q.category,
                    difficulty = q.difficulty,
                    xpEarned = xp,
                    explanation = q.explanation,
                    explanation_tr = q.explanation_tr,
                    explanation_en = q.explanation_en,
                    docLinks = q.doc_links
                )
                answersMap[q.id] = ans
            } else if (ans != null && (ans.isCorrect == null || ans.xpEarned == null || ans.questionId == null)) {
                ans = ans.copy(
                    questionId = ans.questionId ?: q.id,
                    selectedOption = chosenKey,
                    isCorrect = isCorrect,
                    correctAnswer = correctKey,
                    category = ans.category ?: q.category,
                    difficulty = ans.difficulty ?: q.difficulty,
                    xpEarned = xp,
                    explanation = if (ans.explanation.isNullOrBlank()) q.explanation else ans.explanation,
                    explanation_tr = if (ans.explanation_tr.isNullOrBlank()) q.explanation_tr else ans.explanation_tr,
                    explanation_en = if (ans.explanation_en.isNullOrBlank()) q.explanation_en else ans.explanation_en,
                    docLinks = if (ans.docLinks.isEmpty()) q.doc_links else ans.docLinks
                )
                answersMap[q.id] = ans
            }

            if (ans != null && optIdx in 0..3) {
                if (ans.isCorrect == true) correctCount++
                if (ans.xpEarned != null) earnedXp += ans.xpEarned
                val cat = ans.category ?: q.category
                categoryResults[cat] = CategoryResult(
                    attempted = (categoryResults[cat]?.attempted ?: 0) + 1,
                    correct = (categoryResults[cat]?.correct ?: 0) + (if (ans.isCorrect == true) 1 else 0)
                )
            }
        }

        buildLayout()
        setupKeyboardNavigation()

        if (questions.isNotEmpty()) {
            showQuestion(currentIndex)
        }
    }

    private fun vGap(h: Int): Component = Box.createRigidArea(Dimension(0, JBUI.scale(h))).apply {
        if (this is JComponent) alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun buildLayout() {
        // Header
        val headerPanel = JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(
                    0, 0, 1, 0,
                    JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
                ),
                JBUI.Borders.empty(JBUI.scale(8), JBUI.scale(14))
            )
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(dotsPanel, BorderLayout.WEST)

            val metaRight = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
                isOpaque = false
                add(categoryLabel)
                add(difficultyPill)
                add(reportButton)
            }
            add(metaRight, BorderLayout.EAST)
        }

        // Body with Scrollable viewport width tracking to guarantee 100% horizontal expansion
        val bodyPanel = object : JPanel(), Scrollable {
            init {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                border = JBUI.Borders.empty(JBUI.scale(14), JBUI.scale(14), JBUI.scale(16), JBUI.scale(14))
                alignmentX = Component.LEFT_ALIGNMENT
            }
            override fun getScrollableTracksViewportWidth(): Boolean = true
            override fun getScrollableTracksViewportHeight(): Boolean = false
            override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
            override fun getScrollableBlockIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = JBUI.scale(20)
            override fun getScrollableUnitIncrement(visibleRect: Rectangle?, orientation: Int, direction: Int): Int = JBUI.scale(10)
        }

        bodyPanel.add(questionWrapper)
        bodyPanel.add(vGap(12))
        bodyPanel.add(codeSnippetComponent)
        bodyPanel.add(codeSnippetBottomGap)
        bodyPanel.add(optionsPanel)
        bodyPanel.add(vGap(12))
        bodyPanel.add(explanationCard)

        val scrollPane = JBScrollPane(bodyPanel).apply {
            border = JBUI.Borders.empty()
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        add(headerPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(footerPanel, BorderLayout.SOUTH)
    }

    private fun setupKeyboardNavigation() {
        isFocusable = true
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (answered) {
                    if (e.keyCode == KeyEvent.VK_ENTER || e.keyCode == KeyEvent.VK_SPACE) {
                        advance()
                    }
                    return
                }

                val key = when (e.keyCode) {
                    KeyEvent.VK_A, KeyEvent.VK_1 -> "A"
                    KeyEvent.VK_B, KeyEvent.VK_2 -> "B"
                    KeyEvent.VK_C, KeyEvent.VK_3 -> "C"
                    KeyEvent.VK_D, KeyEvent.VK_4 -> "D"
                    else -> null
                }
                if (key != null && currentIndex in questions.indices) {
                    val q = questions[currentIndex]
                    onOptionSelected(key, q)
                }
            }
        })
    }

    private fun showQuestion(index: Int) {
        currentIndex = index
        answered = false
        selectedKey = null

        val q = questions[index]
        val lang = currentLanguage()

        // Update progress dots
        dotsPanel.removeAll()
        questions.forEachIndexed { i, _ ->
            val dot = object : JComponent() {
                init {
                    val s = JBUI.scale(8)
                    preferredSize = Dimension(s, s)
                    minimumSize = preferredSize
                    maximumSize = preferredSize
                    isOpaque = false
                    alignmentX = Component.LEFT_ALIGNMENT
                }

                override fun paintComponent(g: Graphics) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    val s = JBUI.scale(8)
                    when {
                        i < index -> {
                            g2.color = Color(0x10, 0xB9, 0x81)
                            g2.fillOval(0, 0, s, s)
                        }
                        i == index -> {
                            g2.color = Color(0x10, 0xB9, 0x81)
                            g2.fillOval(0, 0, s, s)
                            g2.color = Color.WHITE
                            val inner = JBUI.scale(4)
                            val off = (s - inner) / 2
                            g2.fillOval(off, off, inner, inner)
                        }
                        else -> {
                            g2.color = JBColor(Color(0xD4D4D8), Color(0x3F3F46))
                            g2.fillOval(0, 0, s, s)
                        }
                    }
                }
            }
            dotsPanel.add(dot)
        }

        categoryLabel.text = q.category
        val diffColor = when (q.difficulty) {
            "Junior" -> Color(0x10, 0xB9, 0x81)
            "Mid"    -> Color(0x3B, 0x82, 0xF6)
            "Senior" -> Color(0xF5, 0x9E, 0x0B)
            "Expert" -> Color(0xEF, 0x44, 0x44)
            else     -> Color(0x10, 0xB9, 0x81)
        }
        difficultyPill.text = q.difficulty
        difficultyPill.pillColor = diffColor
        difficultyPill.foreground = diffColor

        // Format question text with syntax-highlighted code tokens and strict left-alignment
        questionLabel.text = TextFormatter.formatQuestionHtml(q.displayQuestion(lang))

        // Code snippet
        val hasSnippet = !q.code_snippet.isNullOrBlank()
        if (hasSnippet) {
            codeSnippetComponent.setCode(q.code_snippet!!, q.code_language)
            codeSnippetComponent.isVisible = true
            codeSnippetBottomGap.isVisible = true
        } else {
            codeSnippetComponent.isVisible = false
            codeSnippetBottomGap.isVisible = false
        }

        // Build option cards
        optionsPanel.removeAll()
        optionCards.clear()
        val opts = q.displayOptions(lang)

        val keys = listOf("A", "B", "C", "D")
        keys.forEachIndexed { i, key ->
            val text = opts[key] ?: return@forEachIndexed
            val card = ModernOptionCard(key = key, optionText = text) {
                onOptionSelected(key, q)
            }
            card.alignmentX = Component.LEFT_ALIGNMENT
            optionCards.add(card)
            optionsPanel.add(card)
            if (i < keys.size - 1) {
                optionsPanel.add(vGap(6))
            }
        }

        explanationCard.isVisible = false
        footerPanel.isVisible = false
        nextButton.isVisible = false
        nextButton.isEnabled = false
        nextButton.text = nextButtonLabel(index, lang)

        reportButton.updateQuestion(q, lang)

        revalidate()
        repaint()
        requestFocusInWindow()
    }

    private fun nextButtonLabel(index: Int, lang: AppLanguage): String =
        if (index < questions.size - 1)
            if (lang == AppLanguage.EN) "Next Question →" else "Sonraki Soru →"
        else
            if (lang == AppLanguage.EN) "See Results →" else "Sonuçları Gör →"

    // ── 0ms Instant Answer Evaluation ─────────────────────────────────────────

    private fun onOptionSelected(key: String, question: Question) {
        if (answered) return
        isUpdatingLocally = true
        answered = true
        selectedKey = key

        val lang = currentLanguage()
        val optionKeys = listOf("A", "B", "C", "D")
        val selectedOptionIdx = optionKeys.indexOf(key.uppercase())

        // 1. 0ms INSTANT local evaluation
        val correctKey = question.resolvedCorrectAnswer ?: "A"
        val isCorrect = key.equals(correctKey, ignoreCase = true)
        val xp = if (isCorrect) question.xpValue else 0

        if (isCorrect) {
            correctCount++
            earnedXp += xp
        }

        // Update category stats
        categoryResults[question.category] = CategoryResult(
            attempted = (categoryResults[question.category]?.attempted ?: 0) + 1,
            correct = (categoryResults[question.category]?.correct ?: 0) + (if (isCorrect) 1 else 0)
        )

        // Store session state for Supabase sync
        if (currentIndex < selectedOptionsList.size) {
            selectedOptionsList[currentIndex] = selectedOptionIdx
        }

        val explanation = question.displayExplanation(lang) ?: ""
        val answerPayload = UserAnswerPayload(
            questionId = question.id,
            selectedOption = key,
            isCorrect = isCorrect,
            correctAnswer = correctKey,
            answeredAt = Instant.now().toString(),
            category = question.category,
            difficulty = question.difficulty,
            xpEarned = xp,
            explanation = explanation,
            explanation_tr = question.explanation_tr,
            explanation_en = question.explanation_en
        )
        answersMap[question.id] = answerPayload

        // 2. 0ms INSTANT UI highlight
        optionCards.forEach { card ->
            card.applyAnswerState(
                isCorrect = card.key.equals(correctKey, ignoreCase = true),
                isSelected = card.key.equals(key, ignoreCase = true)
            )
            card.isEnabled = false
        }

        val isRep = isReported(question.id)

        explanationCard.show(
            correct = isCorrect,
            text = explanation,
            earnedXp = xp,
            docLinks = question.doc_links,
            isEnglish = (lang == AppLanguage.EN),
            isReported = isRep,
            onReportClick = {
                openReportDialog(question)
            }
        )

        footerPanel.isVisible = true
        nextButton.isVisible = true
        nextButton.isEnabled = true
        revalidate()
        repaint()

        // Smoothly ensure explanation card and next button are visible
        SwingUtilities.invokeLater {
            explanationCard.scrollRectToVisible(Rectangle(0, 0, explanationCard.width, explanationCard.height))
        }

        // 3. Background asynchronous sync (Instant progress to Supabase + server verification)
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // A: Sync instant progress to Supabase daily_submissions
                syncPartialProgressToSupabase()

                // B: Verify answer with server API for anti-cheat verification / enriched doc links
                try {
                    val response = DroidleService.getInstance().verifyAnswer(question.id, key)
                    SwingUtilities.invokeLater {
                        // Update doc links from server if available
                        if (response.docLinks.isNotEmpty()) {
                            val rep = isReported(question.id)
                            explanationCard.show(
                                correct = isCorrect,
                                text = explanation,
                                earnedXp = xp,
                                docLinks = response.docLinks,
                                isEnglish = (lang == AppLanguage.EN),
                                isReported = rep,
                                onReportClick = {
                                    openReportDialog(question)
                                }
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Ignore API verification error since local evaluation is already rendered
                }
            } finally {
                isUpdatingLocally = false
            }
        }
    }

    /**
     * Pushes partial progress to Supabase daily_submissions table so the user
     * can switch to the web app on question 3 and immediately resume from question 3.
     */
    private fun syncPartialProgressToSupabase() {
        val state = try { StateManager.getInstance() } catch (_: Throwable) { null } ?: return
        val today = state.todayIstanbulString()

        // Keep local state in sync on each partial answer
        state.savePartialSession(
            today = state.todayIstanbul(),
            score = correctCount,
            earnedXp = earnedXp,
            selectedOptions = selectedOptionsList.toList(),
            answers = answersMap.toMap()
        )

        val userId = state.getUserId() ?: return

        val payload = DailySubmissionPayload(
            user_id = userId,
            date = today,
            score = correctCount,
            earned_xp = earnedXp,
            answers = answersMap.toMap(),
            selected_options = selectedOptionsList.toList()
        )

        SupabaseService.getInstance().upsertDailySubmission(payload)
    }

    fun refreshLanguage() {
        if (questions.isEmpty()) return
        val q = questions[currentIndex]
        val lang = currentLanguage()

        questionLabel.text = TextFormatter.formatQuestionHtml(q.displayQuestion(lang))
        val opts = q.displayOptions(lang)

        optionCards.forEach { card ->
            val text = opts[card.key] ?: return@forEach
            card.updateText(text)
        }

        reportButton.updateQuestion(q, lang)

        if (answered) {
            val explanation = q.displayExplanation(lang) ?: ""
            val isCorrect = selectedKey.equals(q.resolvedCorrectAnswer, ignoreCase = true)
            val isRep = isReported(q.id)
            explanationCard.show(
                correct = isCorrect,
                text = explanation,
                earnedXp = if (isCorrect) q.xpValue else 0,
                docLinks = q.doc_links,
                isEnglish = (lang == AppLanguage.EN),
                isReported = isRep,
                onReportClick = {
                    openReportDialog(q)
                }
            )
        }

        nextButton.text = nextButtonLabel(currentIndex, lang)
        revalidate()
        repaint()
    }

    private fun openReportDialog(question: Question) {
        val dialog = QuestionReportDialog(
            project = project,
            question = question,
            lang = currentLanguage(),
            onSuccess = { qId ->
                reportButton.refreshReportedState(true)
                explanationCard.refreshReportedState(true)
            }
        )
        dialog.show()
    }

    private fun advance() {
        val next = currentIndex + 1
        if (next >= questions.size) {
            onComplete(
                correctCount,
                questions,
                earnedXp,
                selectedOptionsList.toList(),
                answersMap.toMap(),
                categoryResults.toMap()
            )
        } else {
            showQuestion(next)
        }
    }

    // ── Real-Time Cross-Device Synchronization (Web <-> Plugin) ────────────────

    override fun addNotify() {
        super.addNotify()
        startSyncPoller()
        setupFocusListeners()
    }

    override fun removeNotify() {
        stopSyncPoller()
        super.removeNotify()
    }

    fun dispose() {
        isDisposed = true
        stopSyncPoller()
    }

    fun startSyncPoller() {
        if (syncTimer != null || isDisposed) return
        syncTimer = javax.swing.Timer(3000) {
            if (!isDisposed && isShowing && !isUpdatingLocally) {
                checkRemoteSync()
            }
        }.apply {
            isRepeats = true
            start()
        }
    }

    fun stopSyncPoller() {
        syncTimer?.stop()
        syncTimer = null
        removeFocusListeners()
    }

    private fun setupFocusListeners() {
        if (windowFocusListener != null) return

        val wfl = object : WindowFocusListener {
            override fun windowGainedFocus(e: WindowEvent?) {
                if (!isDisposed && !isUpdatingLocally) {
                    checkRemoteSync()
                }
            }
            override fun windowLostFocus(e: WindowEvent?) {}
        }
        windowFocusListener = wfl

        val win = SwingUtilities.getWindowAncestor(this)
        win?.addWindowFocusListener(wfl)

        if (hierarchyListener == null) {
            val hl = HierarchyListener { e ->
                if ((e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && isShowing) {
                    val currentWin = SwingUtilities.getWindowAncestor(this)
                    if (currentWin != null && windowFocusListener != null) {
                        currentWin.removeWindowFocusListener(windowFocusListener)
                        currentWin.addWindowFocusListener(windowFocusListener)
                    }
                    if (!isDisposed && !isUpdatingLocally) {
                        checkRemoteSync()
                    }
                }
            }
            hierarchyListener = hl
            addHierarchyListener(hl)
        }

        val propListener = PropertyChangeListener { evt ->
            if (evt.propertyName == "activeWindow" && evt.newValue != null && isShowing && !isDisposed && !isUpdatingLocally) {
                checkRemoteSync()
            }
        }
        windowPropertyListener = propListener
        try {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("activeWindow", propListener)
        } catch (_: Throwable) {
            // Ignore if restricted
        }
    }

    private fun removeFocusListeners() {
        hierarchyListener?.let {
            removeHierarchyListener(it)
            hierarchyListener = null
        }
        windowPropertyListener?.let {
            try {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("activeWindow", it)
            } catch (_: Throwable) {}
            windowPropertyListener = null
        }
        val window = SwingUtilities.getWindowAncestor(this)
        windowFocusListener?.let {
            window?.removeWindowFocusListener(it)
            windowFocusListener = null
        }
    }

    fun checkRemoteSync() {
        if (isDisposed || isCheckingSync || isUpdatingLocally) return
        val state = try { StateManager.getInstance() } catch (_: Throwable) { null } ?: return
        val userId = state.getUserId()
        if (userId.isNullOrBlank()) return
        val today = state.todayIstanbulString()

        isCheckingSync = true
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val supabase = SupabaseService.getInstance()
                val remoteSub = supabase.fetchTodaySubmission(userId, today)
                if (remoteSub != null && !isDisposed) {
                    handleRemoteSubmissionUpdate(remoteSub, state)
                }
            } catch (_: Exception) {
                // Background sync failure gracefully ignored
            } finally {
                isCheckingSync = false
            }
        }
    }

    private fun computeCategoryResults(answers: Map<String, UserAnswerPayload>): Map<String, CategoryResult> {
        val results = mutableMapOf<String, CategoryResult>()
        val qMap = questions.associateBy { it.id }
        answers.values.forEach { ans ->
            val cat = ans.category ?: qMap[ans.questionId]?.category ?: "General"
            val existing = results[cat] ?: CategoryResult(0, 0)
            results[cat] = CategoryResult(
                attempted = existing.attempted + 1,
                correct = existing.correct + (if (ans.isCorrect == true) 1 else 0)
            )
        }
        return results
    }

    internal fun handleRemoteSubmissionUpdate(remoteSub: DailySubmissionRow, state: StateManager? = null) {
        val reconciled = reconcileDailySubmission(remoteSub, questions)

        // Case 1: Quiz is completed remotely (e.g. all 5 questions answered on web)
        if (reconciled.isComplete) {
            SwingUtilities.invokeLater {
                if (isDisposed) return@invokeLater
                stopSyncPoller()

                state?.syncFromServer(
                    correctCount = reconciled.score,
                    totalCount = questions.size,
                    earnedXp = reconciled.earnedXp,
                    selectedOptions = reconciled.selectedOptions,
                    answers = reconciled.answers
                )

                // Cleanly compute fresh category results without duplication
                val freshCats = computeCategoryResults(reconciled.answers)
                synchronized(categoryResults) {
                    categoryResults.clear()
                    categoryResults.putAll(freshCats)
                }

                onComplete(
                    reconciled.score,
                    questions,
                    reconciled.earnedXp,
                    reconciled.selectedOptions,
                    reconciled.answers,
                    freshCats
                )
            }
            return
        }

        // Case 2: In-progress quiz update from web
        val localAnsweredKeys = synchronized(answersMap) { answersMap.keys.toSet() }
        val remoteHasNewAnswers = reconciled.answers.keys.any { qid -> !localAnsweredKeys.contains(qid) }

        if (!remoteHasNewAnswers && reconciled.answeredCount <= localAnsweredKeys.size) {
            // No new remote answers
            return
        }

        // Guard: if user is actively viewing explanation on the current question in the plugin,
        // and remote has NOT answered questions beyond current question, do not advance.
        if (answered && reconciled.firstUnansweredIndex <= currentIndex) {
            return
        }

        SwingUtilities.invokeLater {
            if (isDisposed || isUpdatingLocally) return@invokeLater

            // Re-verify guards on EDT to prevent race conditions if user clicked while fetch was in-flight
            val currentLocalAnsweredKeys = synchronized(answersMap) { answersMap.keys.toSet() }
            if (answered && reconciled.firstUnansweredIndex <= currentIndex) {
                return@invokeLater
            }
            if (reconciled.answeredCount < currentLocalAnsweredKeys.size) {
                return@invokeLater
            }

            // Apply remote reconciled state
            synchronized(answersMap) {
                reconciled.answers.forEach { (qid, ans) ->
                    answersMap[qid] = ans
                }
            }
            reconciled.selectedOptions.forEachIndexed { i, opt ->
                if (i < selectedOptionsList.size && opt in 0..3) {
                    selectedOptionsList[i] = opt
                }
            }
            correctCount = maxOf(correctCount, reconciled.score)
            earnedXp = maxOf(earnedXp, reconciled.earnedXp)

            // Recompute category results cleanly
            val freshCats = computeCategoryResults(reconciled.answers)
            synchronized(categoryResults) {
                categoryResults.clear()
                categoryResults.putAll(freshCats)
            }

            // Persist local partial session
            state?.savePartialSession(
                today = state.todayIstanbul(),
                score = correctCount,
                earnedXp = earnedXp,
                selectedOptions = selectedOptionsList.toList(),
                answers = answersMap.toMap()
            )

            // Advance to the next unanswered question
            val nextIdx = reconciled.firstUnansweredIndex.coerceIn(0, (questions.size - 1).coerceAtLeast(0))
            showQuestion(nextIdx)
        }
    }

    internal fun getCurrentIndex(): Int = currentIndex
    internal fun getAnswersMap(): Map<String, UserAnswerPayload> = synchronized(answersMap) { answersMap.toMap() }
    internal fun getSelectedOptions(): List<Int> = selectedOptionsList.toList()
    internal fun getCorrectCount(): Int = correctCount
    internal fun getEarnedXp(): Int = earnedXp
    internal fun isAnswered(): Boolean = answered
    internal fun getCategoryResults(): Map<String, CategoryResult> = synchronized(categoryResults) { categoryResults.toMap() }
}
