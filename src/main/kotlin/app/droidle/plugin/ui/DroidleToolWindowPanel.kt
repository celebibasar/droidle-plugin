package app.droidle.plugin.ui

import app.droidle.plugin.model.Question
import app.droidle.plugin.service.DroidleService
import app.droidle.plugin.state.StateManager
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.CompoundBorder

/** Screen identifiers for CardLayout navigation */
internal object Screen {
    const val LOADING  = "LOADING"
    const val QUIZ     = "QUIZ"
    const val RESULT   = "RESULT"
    const val ERROR    = "ERROR"
    const val DONE     = "DONE"    // already completed today
}

/**
 * Top-level ToolWindow content.
 * Owns the CardLayout state machine and coordinates all child screens.
 * Keeps UI logic out of the factory; each screen is self-contained.
 */
class DroidleToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val cards   = CardLayout()
    private val deck    = JPanel(cards).apply { border = JBUI.Borders.empty() }

    private lateinit var quizScreen:   QuizScreen
    private lateinit var resultScreen: ResultScreen
    private lateinit var loadingScreen: LoadingScreen
    private lateinit var errorScreen:  ErrorScreen
    private lateinit var doneScreen:   DoneScreen

    init {
        border = JBUI.Borders.empty()
        buildScreens()
        add(buildTopBar(), BorderLayout.NORTH)
        add(deck, BorderLayout.CENTER)
        start()
    }

    // ── Top bar: GitHub login + Leaderboard ────────────────────────────────────

    private fun buildTopBar(): JPanel {
        val loginBtn = iconTextButton(
            icon  = AllIcons.Vcs.Vendors.Github,
            label = "Giriş Yap",
            tip   = "GitHub ile giriş yap"
        ) { BrowserUtil.browse("https://droidle.vercel.app/api/auth/signin/github") }

        val leaderBtn = iconTextButton(
            icon  = AllIcons.Actions.ListChanges,
            label = "Liderlik",
            tip   = "Liderlik tablosunu gör"
        ) { BrowserUtil.browse("https://droidle.vercel.app/leaderboard") }

        val logo = JBLabel("🤖 Droidle").apply {
            font       = JBUI.Fonts.label(11f).deriveFont(java.awt.Font.BOLD)
            foreground = JBColor(Color(0x10B981), Color(0x34D399))
            border     = JBUI.Borders.emptyLeft(JBUI.scale(4))
        }

        return JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0,
                    JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
                JBUI.Borders.empty(JBUI.scale(3), JBUI.scale(6))
            )
            isOpaque = false

            val right = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
                isOpaque = false
                add(loginBtn)
                add(leaderBtn)
            }
            add(logo,  BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }
    }

    /** Small borderless button with icon + text label */
    private fun iconTextButton(
        icon:    javax.swing.Icon,
        label:   String,
        tip:     String,
        onClick: () -> Unit
    ): JButton = JButton(label, icon).apply {
        toolTipText   = tip
        isBorderPainted = false
        isContentAreaFilled = false
        isFocusPainted = false
        font          = JBUI.Fonts.smallFont()
        cursor        = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        addActionListener { onClick() }
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                isContentAreaFilled = true
            }
            override fun mouseExited(e: MouseEvent) {
                isContentAreaFilled = false
            }
        })
    }

    // ── Screen wiring ──────────────────────────────────────────────────────────

    private fun buildScreens() {
        loadingScreen = LoadingScreen()
        errorScreen   = ErrorScreen(onRetry = ::start)
        doneScreen    = DoneScreen(onRetry = ::openWebsite)

        // Quiz and Result are re-created each session; placeholders added once
        quizScreen   = QuizScreen(onComplete = ::onQuizComplete, onError = ::showError)
        resultScreen = ResultScreen(
            correctCount = 0,
            questions    = emptyList(),
            onOpenWeb    = ::openWebsite
        )

        deck.add(loadingScreen,  Screen.LOADING)
        deck.add(quizScreen,     Screen.QUIZ)
        deck.add(resultScreen,   Screen.RESULT)
        deck.add(errorScreen,    Screen.ERROR)
        deck.add(doneScreen,     Screen.DONE)
    }

    // ── Entry point ────────────────────────────────────────────────────────────

    private fun start() {
        show(Screen.LOADING)

        // Already done today? Skip quiz.
        if (StateManager.getInstance().isQuizCompletedToday()) {
            show(Screen.DONE)
            return
        }

        fetchQuiz()
    }

    private fun fetchQuiz() {
        show(Screen.LOADING)
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val questions = DroidleService.getInstance().fetchDailyQuiz()
                SwingUtilities.invokeLater {
                    replaceQuizScreen(questions)
                    show(Screen.QUIZ)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater { showError(e.message ?: "Bilinmeyen hata") }
            }
        }
    }

    // ── Screen transitions ─────────────────────────────────────────────────────

    private fun replaceQuizScreen(questions: List<Question>) {
        deck.remove(quizScreen)
        quizScreen = QuizScreen(questions = questions, onComplete = ::onQuizComplete, onError = ::showError)
        deck.add(quizScreen, Screen.QUIZ)
        deck.revalidate()
    }

    private fun onQuizComplete(correctCount: Int, questions: List<Question>) {
        StateManager.getInstance().recordQuizCompletion(correctCount, questions.size)
        deck.remove(resultScreen)
        resultScreen = ResultScreen(
            correctCount = correctCount,
            questions    = questions,
            onOpenWeb    = ::openWebsite
        )
        deck.add(resultScreen, Screen.RESULT)
        deck.revalidate()
        show(Screen.RESULT)
    }

    private fun showError(msg: String) {
        errorScreen.setMessage(msg)
        show(Screen.ERROR)
    }

    private fun show(name: String) {
        cards.show(deck, name)
    }

    private fun openWebsite() {
        BrowserUtil.browse("https://droidle.vercel.app")
    }
}
