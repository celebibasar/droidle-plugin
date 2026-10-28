package app.droidle.plugin.ui

import app.droidle.plugin.auth.DroidleAuthListener
import app.droidle.plugin.auth.DroidleLocalAuthServer
import app.droidle.plugin.model.Question
import app.droidle.plugin.service.DroidleService
import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.state.CategoryResult
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
 *
 * CardLayout state machine:
 *   LOADING → (server says completedToday) → DONE
 *   LOADING → (questions ready)            → QUIZ
 *   QUIZ    → (last question answered)     → RESULT
 *   QUIZ    → (error during answer verify) → ERROR
 *   ERROR   → (retry)                      → LOADING
 *
 * Sync contract (mirrors web supabase-sync.ts):
 *   - If server returns completedToday=true → syncFromServer() + show DONE
 *   - If user finishes quiz locally         → recordQuizCompletion() + show RESULT
 *   - Double-count guard lives in StateManager.recordQuizCompletion()
 *   - All dates are Europe/Istanbul via StateManager.todayIstanbul()
 */
class DroidleToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val cards = CardLayout()
    private val deck  = JPanel(cards).apply { border = JBUI.Borders.empty() }

    private lateinit var quizScreen:    QuizScreen
    private lateinit var resultScreen:  ResultScreen
    private lateinit var loadingScreen: LoadingScreen
    private lateinit var errorScreen:   ErrorScreen
    private lateinit var doneScreen:    DoneScreen

    // Toolbar refs for dynamic update
    private lateinit var loginBtn:   JButton
    private lateinit var langToggle: JButton
    private lateinit var userLabel:  JBLabel

    init {
        border = JBUI.Borders.empty()
        buildScreens()
        add(buildTopBar(), BorderLayout.NORTH)
        add(deck,          BorderLayout.CENTER)
        start()

        // Start local auth server (localhost:62330) for web→plugin token transfer
        DroidleLocalAuthServer.getInstance().start()

        // Refresh toolbar when auth state changes (login / logout from web)
        ApplicationManager.getApplication().messageBus
            .connect()
            .subscribe(DroidleAuthListener.TOPIC, DroidleAuthListener {
                SwingUtilities.invokeLater { refreshTopBar() }
            })
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private fun buildTopBar(): JPanel {
        val state = StateManager.getInstance()

        loginBtn = iconTextButton(
            icon  = AllIcons.Vcs.Vendors.Github,
            label = loginLabel(state),
            tip   = loginTip(state)
        ) { onLoginClick() }

        userLabel = JBLabel(state.getUsername() ?: "").apply {
            font       = JBUI.Fonts.smallFont()
            foreground = JBColor(Color(0x10B981), Color(0x34D399))
            isVisible  = state.isLoggedIn() && state.getUsername() != null
        }

        langToggle = JButton(state.language.label).apply {
            toolTipText         = "Dil değiştir / Switch language"
            isBorderPainted     = false
            isContentAreaFilled = false
            isFocusPainted      = false
            font                = JBUI.Fonts.smallFont()
            cursor              = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            foreground          = JBColor(Color(0x6B7280), Color(0x9CA3AF))
            addActionListener { toggleLanguage() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { isContentAreaFilled = true  }
                override fun mouseExited(e: MouseEvent)  { isContentAreaFilled = false }
            })
        }

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
                BorderFactory.createMatteBorder(
                    0, 0, 1, 0,
                    JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
                ),
                JBUI.Borders.empty(JBUI.scale(3), JBUI.scale(6))
            )
            isOpaque = false

            val right = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
                isOpaque = false
                add(langToggle)
                add(loginBtn)
                add(userLabel)
                add(leaderBtn)
            }
            add(logo,  BorderLayout.WEST)
            add(right, BorderLayout.EAST)
        }
    }

    private fun refreshTopBar() {
        val state       = StateManager.getInstance()
        loginBtn.text   = loginLabel(state)
        loginBtn.toolTipText = loginTip(state)
        langToggle.text = state.language.label
        userLabel.text  = "@${state.getUsername() ?: ""}"
        userLabel.isVisible = state.isLoggedIn() && state.getUsername() != null
        revalidate(); repaint()
    }

    private fun loginLabel(state: StateManager) =
        if (state.isLoggedIn()) "Çıkış" else "Giriş Yap"

    private fun loginTip(state: StateManager) =
        if (state.isLoggedIn()) "Oturumu kapat" else "GitHub ile giriş yap"

    private fun onLoginClick() {
        val state = StateManager.getInstance()
        if (state.isLoggedIn()) {
            state.clearAuthToken()
            state.clearUsername()
            refreshTopBar()
        } else {
            BrowserUtil.browse("https://droidle.vercel.app/login?source=plugin")
        }
    }

    /**
     * Toggle language, then re-fetch the quiz so all text is in the new language.
     * We use fetchQuizResult() (not start()) so we never short-circuit to DONE —
     * the user should be able to read the questions even if already completed.
     */
    private fun toggleLanguage() {
        val state = StateManager.getInstance()
        state.language = if (state.language == AppLanguage.TR) AppLanguage.EN else AppLanguage.TR
        refreshTopBar()
        fetchQuiz()
    }

    private fun iconTextButton(
        icon:    javax.swing.Icon,
        label:   String,
        tip:     String,
        onClick: () -> Unit
    ): JButton = JButton(label, icon).apply {
        toolTipText         = tip
        isBorderPainted     = false
        isContentAreaFilled = false
        isFocusPainted      = false
        font                = JBUI.Fonts.smallFont()
        cursor              = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        addActionListener { onClick() }
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) { isContentAreaFilled = true  }
            override fun mouseExited(e: MouseEvent)  { isContentAreaFilled = false }
        })
    }

    // ── Screen wiring ──────────────────────────────────────────────────────────

    private fun buildScreens() {
        loadingScreen = LoadingScreen()
        errorScreen   = ErrorScreen(onRetry = ::start)
        doneScreen    = DoneScreen(onOpenWeb = ::openWebsite)

        quizScreen   = QuizScreen(onComplete = ::onQuizComplete, onError = ::showError)
        resultScreen = ResultScreen(
            correctCount = 0,
            questions    = emptyList(),
            earnedXp     = 0,
            onOpenWeb    = ::openWebsite
        )

        deck.add(loadingScreen,  Screen.LOADING)
        deck.add(quizScreen,     Screen.QUIZ)
        deck.add(resultScreen,   Screen.RESULT)
        deck.add(errorScreen,    Screen.ERROR)
        deck.add(doneScreen,     Screen.DONE)
    }

    // ── Entry point ────────────────────────────────────────────────────────────

    /**
     * Initial load / retry entry point.
     * Short-circuits to DONE when local state already marks today as complete.
     * Full server fetch otherwise (which may also short-circuit if server says completedToday).
     */
    private fun start() {
        show(Screen.LOADING)

        if (StateManager.getInstance().isQuizCompletedToday()) {
            // Local state already records completion — jump to DONE with known data.
            // (Streak / score already persisted by recordQuizCompletion on that day.)
            replaceDoneScreen(todayCorrect = null, todayTotal = null, todayXp = null)
            show(Screen.DONE)
            return
        }

        fetchQuiz()
    }

    /**
     * Fetches today's quiz result from the server.
     *
     * If the server says completedToday=true (authenticated user already submitted):
     *   → calls StateManager.syncFromServer() with streak + XP from server
     *   → shows DONE screen with today's session data
     *
     * Otherwise replaces the QuizScreen and shows it.
     */
    private fun fetchQuiz() {
        show(Screen.LOADING)
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = DroidleService.getInstance().fetchDailyQuizResult()
                SwingUtilities.invokeLater {
                    if (result.completedToday) {
                        // Server is authoritative — sync local state and show DONE
                        StateManager.getInstance().syncFromServer(
                            correctCount  = result.correctCount ?: 0,
                            totalCount    = result.questions.size.takeIf { it > 0 }
                                ?: result.correctCount?.let { it + 1 } ?: 1,
                            serverStreak  = result.currentStreak,
                            earnedXp      = result.earnedXp ?: 0
                        )
                        replaceDoneScreen(
                            todayCorrect = result.correctCount,
                            todayTotal   = result.questions.size.takeIf { it > 0 },
                            todayXp      = result.earnedXp
                        )
                        show(Screen.DONE)
                    } else {
                        replaceQuizScreen(result.questions)
                        show(Screen.QUIZ)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError(e.message ?: "Bilinmeyen hata")
                }
            }
        }
    }

    // ── Screen transitions ─────────────────────────────────────────────────────

    private fun replaceQuizScreen(questions: List<Question>) {
        deck.remove(quizScreen)
        quizScreen = QuizScreen(
            questions  = questions,
            onComplete = ::onQuizComplete,
            onError    = ::showError
        )
        deck.add(quizScreen, Screen.QUIZ)
        deck.revalidate()
    }

    private fun replaceDoneScreen(
        todayCorrect: Int?,
        todayTotal:   Int?,
        todayXp:      Int?
    ) {
        deck.remove(doneScreen)
        doneScreen = DoneScreen(
            todayCorrect = todayCorrect,
            todayTotal   = todayTotal,
            todayXp      = todayXp,
            onOpenWeb    = ::openWebsite
        )
        deck.add(doneScreen, Screen.DONE)
        deck.revalidate()
    }

    /**
     * Called by QuizScreen when the last question is answered.
     *
     * @param correctCount      Number of correct answers in this session.
     * @param questions         Full question list (for ResultScreen breakdown).
     * @param earnedXp          Total XP earned (from verified server responses or local fallback).
     * @param categoryResults   Per-category stats accumulated during the quiz.
     */
    private fun onQuizComplete(
        correctCount:    Int,
        questions:       List<Question>,
        earnedXp:        Int,
        categoryResults: Map<String, CategoryResult>
    ) {
        // Persist — double-count guard is inside StateManager
        StateManager.getInstance().recordQuizCompletion(
            correctCount    = correctCount,
            totalCount      = questions.size,
            earnedXp        = earnedXp,
            categoryResults = categoryResults
        )

        deck.remove(resultScreen)
        resultScreen = ResultScreen(
            correctCount = correctCount,
            questions    = questions,
            earnedXp     = earnedXp,
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

    private fun show(name: String) { cards.show(deck, name) }

    private fun openWebsite() { BrowserUtil.browse("https://droidle.vercel.app") }
}
