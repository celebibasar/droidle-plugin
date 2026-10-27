package app.droidle.plugin.ui

import app.droidle.plugin.auth.DroidleAuthListener
import app.droidle.plugin.auth.DroidleLocalAuthServer
import app.droidle.plugin.model.Question
import app.droidle.plugin.service.DroidleService
import app.droidle.plugin.state.AppLanguage
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
 */
class DroidleToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val cards   = CardLayout()
    private val deck    = JPanel(cards).apply { border = JBUI.Borders.empty() }

    private lateinit var quizScreen:    QuizScreen
    private lateinit var resultScreen:  ResultScreen
    private lateinit var loadingScreen: LoadingScreen
    private lateinit var errorScreen:   ErrorScreen
    private lateinit var doneScreen:    DoneScreen

    // Toolbar refs for dynamic update
    private lateinit var loginBtn:    JButton
    private lateinit var langToggle:  JButton
    private lateinit var userLabel:   JBLabel

    init {
        border = JBUI.Borders.empty()
        buildScreens()
        add(buildTopBar(), BorderLayout.NORTH)
        add(deck, BorderLayout.CENTER)
        start()

        // Start local auth server (localhost:62330) for web→plugin token transfer
        DroidleLocalAuthServer.getInstance().start()

        // Listen for auth changes to refresh toolbar
        ApplicationManager.getApplication().messageBus
            .connect()
            .subscribe(DroidleAuthListener.TOPIC, DroidleAuthListener {
                SwingUtilities.invokeLater { refreshTopBar() }
            })
    }

    // ── Top bar ───────────────────────────────────────────────────────────────

    private fun buildTopBar(): JPanel {
        val state = StateManager.getInstance()

        // Login / user display
        loginBtn = iconTextButton(
            icon  = AllIcons.Vcs.Vendors.Github,
            label = loginLabel(state),
            tip   = loginTip(state)
        ) { onLoginClick() }

        // User label (shown only when logged in)
        userLabel = JBLabel(state.getUsername() ?: "").apply {
            font       = JBUI.Fonts.smallFont()
            foreground = JBColor(Color(0x10B981), Color(0x34D399))
            isVisible  = state.isLoggedIn() && state.getUsername() != null
        }

        // Language toggle
        langToggle = JButton(state.language.label).apply {
            toolTipText      = "Dil değiştir / Switch language"
            isBorderPainted  = false
            isContentAreaFilled = false
            isFocusPainted   = false
            font             = JBUI.Fonts.smallFont()
            cursor           = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            foreground       = JBColor(Color(0x6B7280), Color(0x9CA3AF))
            addActionListener { toggleLanguage() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) { isContentAreaFilled = true }
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
                BorderFactory.createMatteBorder(0, 0, 1, 0,
                    JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
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
        val state = StateManager.getInstance()
        loginBtn.text    = loginLabel(state)
        loginBtn.toolTipText = loginTip(state)
        langToggle.text  = state.language.label
        userLabel.text   = "@${state.getUsername() ?: ""}"
        userLabel.isVisible = state.isLoggedIn() && state.getUsername() != null
        revalidate(); repaint()
    }

    private fun loginLabel(state: StateManager): String =
        if (state.isLoggedIn()) "Çıkış" else "Giriş Yap"

    private fun loginTip(state: StateManager): String =
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

    private fun toggleLanguage() {
        val state = StateManager.getInstance()
        state.language = if (state.language == AppLanguage.TR) AppLanguage.EN else AppLanguage.TR
        refreshTopBar()
        // Reload quiz so it appears in the new language
        start()
    }

    /** Small borderless button with icon + text label */
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
            override fun mouseEntered(e: MouseEvent) { isContentAreaFilled = true }
            override fun mouseExited(e: MouseEvent)  { isContentAreaFilled = false }
        })
    }

    // ── Screen wiring ──────────────────────────────────────────────────────────

    private fun buildScreens() {
        loadingScreen = LoadingScreen()
        errorScreen   = ErrorScreen(onRetry = ::start)
        doneScreen    = DoneScreen(onRetry = ::openWebsite)

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
