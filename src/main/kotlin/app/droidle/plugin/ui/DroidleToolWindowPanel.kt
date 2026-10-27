package app.droidle.plugin.ui

import app.droidle.plugin.model.Question
import app.droidle.plugin.service.DroidleService
import app.droidle.plugin.state.StateManager
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities

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
        add(deck, BorderLayout.CENTER)
        start()
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
        com.intellij.ide.BrowserUtil.browse("https://droidle.vercel.app")
    }
}
