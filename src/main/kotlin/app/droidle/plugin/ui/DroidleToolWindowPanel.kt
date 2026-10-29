package app.droidle.plugin.ui

import app.droidle.plugin.auth.DroidleAuthListener
import app.droidle.plugin.auth.DroidleLocalAuthServer
import app.droidle.plugin.model.*
import app.droidle.plugin.service.DroidleService
import app.droidle.plugin.service.SupabaseService
import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.state.CategoryResult
import app.droidle.plugin.state.StateManager
import app.droidle.plugin.ui.components.CompactHeader
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Screen identifiers for CardLayout navigation */
internal object Screen {
    const val LOADING = "LOADING"
    const val QUIZ    = "QUIZ"
    const val RESULT  = "RESULT"
    const val ERROR   = "ERROR"
}

/**
 * Top-level ToolWindow content for Droidle.
 *
 * Implements 100% Supabase cross-platform synchronization with the web app:
 * - Timezone rule: All dates strictly calculated in ZoneId.of("Europe/Istanbul").
 * - Startup / Login: Queries Supabase `daily_submissions` for (currentUserId, todayIstanbul).
 *   If user already solved on Web, directly opens ResultScreen with marked options.
 * - On quiz complete: UPSERTs daily_submissions and updates profiles table in Supabase.
 * - Modern JetBrains New UI / Linear aesthetic.
 */
class DroidleToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val cards = CardLayout()
    private val deck = JPanel(cards).apply { border = JBUI.Borders.empty() }

    private lateinit var header: CompactHeader
    private lateinit var loadingScreen: LoadingScreen
    private lateinit var errorScreen: ErrorScreen
    private var quizScreen: QuizScreen? = null
    private var resultScreen: ResultScreen? = null

    // Cached current daily questions
    private var cachedQuestions: List<Question> = emptyList()

    init {
        border = JBUI.Borders.empty()
        isOpaque = false

        buildScreens()

        header = CompactHeader(
            onLanguageToggle = ::toggleLanguage,
            onLoginClick = ::onLoginClick,
            onLogoutClick = ::onLogoutClick
        )
        add(header, BorderLayout.NORTH)
        add(deck, BorderLayout.CENTER)

        // Start local auth server (localhost:62330) for web→plugin token transfer
        DroidleLocalAuthServer.getInstance().start()

        // Listen for auth changes (login / logout) to re-sync remote state
        ApplicationManager.getApplication().messageBus
            .connect()
            .subscribe(DroidleAuthListener.TOPIC, DroidleAuthListener {
                SwingUtilities.invokeLater {
                    header.refresh()
                    start()
                }
            })

        start()
    }

    private fun buildScreens() {
        loadingScreen = LoadingScreen()
        errorScreen = ErrorScreen(onRetry = ::start)

        deck.add(loadingScreen, Screen.LOADING)
        deck.add(errorScreen, Screen.ERROR)
    }

    // ── Entry point & Supabase Synchronization ─────────────────────────────────

    /**
     * Initial startup & sync entry point.
     * Evaluates remote Supabase state (Single Source of Truth) in Europe/Istanbul timezone.
     */
    fun start() {
        show(Screen.LOADING)
        val state = StateManager.getInstance()
        val today = state.todayIstanbulString()
        var userId = state.getUserId()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val supabase = SupabaseService.getInstance()

                // If user is logged in but userId is not yet stored, resolve via auth endpoint
                if (userId.isNullOrBlank() && state.isLoggedIn()) {
                    val token = state.getAuthToken()
                    if (token != null) {
                        val fetchedId = supabase.fetchUserIdFromAuth(token)
                        if (!fetchedId.isNullOrBlank()) {
                            state.saveUserId(fetchedId)
                            userId = fetchedId
                        }
                    }
                }

                // 1. Fetch remote profile if logged in
                var profile: SupabaseProfile? = null
                if (!userId.isNullOrBlank()) {
                    profile = supabase.fetchProfile(userId)
                    if (profile != null) {
                        state.syncFromRemoteProfile(profile)
                        SwingUtilities.invokeLater { header.refresh() }
                    }
                }

                // 2. Fetch today's questions
                var questions: List<Question> = emptyList()
                try {
                    val apiResult = DroidleService.getInstance().fetchDailyQuizResult()
                    questions = apiResult.questions
                } catch (_: Exception) {
                    // Fallback handled below
                }

                if (questions.isEmpty()) {
                    val supaQuestions = supabase.fetchQuestionsFromSupabase(today)
                    if (supaQuestions != null && supaQuestions.isNotEmpty()) {
                        questions = supaQuestions
                    }
                }

                if (questions.isEmpty()) {
                    throw IllegalStateException("Bugün için soru bulunamadı ($today).")
                }
                cachedQuestions = questions

                // 3. Query Supabase daily_submissions for today's submission
                var remoteSubmission: DailySubmissionRow? = null
                if (!userId.isNullOrBlank()) {
                    remoteSubmission = supabase.fetchTodaySubmission(userId, today)
                }

                val reconciled = reconcileDailySubmission(remoteSubmission, questions)

                SwingUtilities.invokeLater {
                    if (remoteSubmission != null) {
                        // Supabase daily_submissions is the Single Source of Truth
                        if (reconciled.isComplete) {
                            // Sadece 5 sorunun tamamı cevaplanmışsa Sonuç Ekranını göster.
                            state.syncFromServer(
                                correctCount = reconciled.score,
                                totalCount = questions.size,
                                earnedXp = reconciled.earnedXp,
                                selectedOptions = reconciled.selectedOptions,
                                answers = reconciled.answers
                            )
                            if (!userId.isNullOrBlank() && (profile == null || profile.last_completed_date != today)) {
                                ApplicationManager.getApplication().executeOnPooledThread {
                                    supabase.updateProfile(
                                        userId = userId,
                                        currentStreak = state.currentStreak,
                                        maxStreak = state.longestStreak,
                                        totalXp = state.totalXp,
                                        lastCompletedDate = today
                                    )
                                }
                            }
                            showResultScreen(
                                correctCount = reconciled.score,
                                questions = questions,
                                earnedXp = reconciled.earnedXp,
                                selectedOptions = reconciled.selectedOptions,
                                answers = reconciled.answers
                            )
                        } else {
                            // Cevaplanan soru sayısı 5'ten azsa Sonuç Ekranını AÇMA;
                            // Yerel StateManager'ı uzaktaki kısmi durumla senkronize et ve web ile tam uyumlu
                            // olarak ilk cevaplanmamış sorudan (reconciled.firstUnansweredIndex) devam ettir.
                            state.savePartialSession(
                                today = state.todayIstanbul(),
                                score = reconciled.score,
                                earnedXp = reconciled.earnedXp,
                                selectedOptions = reconciled.selectedOptions,
                                answers = reconciled.answers
                            )
                            val resumeIndex = reconciled.firstUnansweredIndex
                            showQuizScreen(
                                questions = questions,
                                initialIndex = resumeIndex,
                                initialSelectedOptions = reconciled.selectedOptions,
                                initialAnswers = reconciled.answers
                            )
                        }
                    } else if (state.isQuizCompletedToday()) {
                        // Offline / Guest local completion fallback
                        showResultScreen(
                            correctCount = state.todayScore,
                            questions = questions,
                            earnedXp = state.todayEarnedXp,
                            selectedOptions = state.getTodaySelectedOptions(),
                            answers = state.getTodayAnswers()
                        )
                    } else if (state.hasTodayPartialProgress()) {
                        // Offline / Guest partial progress resume fallback (strictly for today's Istanbul date)
                        val localAnswers = state.getTodayAnswers()
                        val localOptions = state.getTodaySelectedOptions()
                        val resumeIndex = questions.indices.firstOrNull { i ->
                            val q = questions[i]
                            val opt = localOptions.getOrNull(i) ?: -1
                            val ans = localAnswers[q.id]?.resolvedSelectedOption ?: localAnswers[i.toString()]?.resolvedSelectedOption
                            (opt !in 0..3) && (ans == null || ans.uppercase() !in OPTION_KEYS)
                        } ?: 0
                        showQuizScreen(
                            questions = questions,
                            initialIndex = resumeIndex,
                            initialSelectedOptions = localOptions,
                            initialAnswers = localAnswers
                        )
                    } else {
                        // Fresh daily quiz
                        showQuizScreen(questions = questions)
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError(e.message ?: "Bağlantı hatası oluştu.")
                }
            }
        }
    }

    private fun showQuizScreen(
        questions: List<Question>,
        initialIndex: Int = 0,
        initialSelectedOptions: List<Int> = emptyList(),
        initialAnswers: Map<String, UserAnswerPayload> = emptyMap()
    ) {
        if (quizScreen != null) {
            quizScreen?.dispose()
            deck.remove(quizScreen)
            quizScreen = null
        }
        quizScreen = QuizScreen(
            questions = questions,
            initialIndex = initialIndex,
            initialSelectedOptions = initialSelectedOptions,
            initialAnswers = initialAnswers,
            project = project,
            onComplete = ::onQuizComplete,
            onError = ::showError
        )
        deck.add(quizScreen!!, Screen.QUIZ)
        deck.revalidate()
        show(Screen.QUIZ)
    }

    private fun showResultScreen(
        correctCount: Int,
        questions: List<Question>,
        earnedXp: Int,
        selectedOptions: List<Int>,
        answers: Map<String, UserAnswerPayload>
    ) {
        if (quizScreen != null) {
            quizScreen?.dispose()
            deck.remove(quizScreen)
            quizScreen = null
        }
        if (resultScreen != null) deck.remove(resultScreen)
        resultScreen = ResultScreen(
            correctCount = correctCount,
            questions = questions,
            earnedXp = earnedXp,
            selectedOptions = selectedOptions,
            answers = answers,
            project = project,
            onOpenWeb = { BrowserUtil.browse("https://droidle.dev/leaderboard") }
        )
        deck.add(resultScreen!!, Screen.RESULT)
        deck.revalidate()
        show(Screen.RESULT)
    }

    // ── Quiz Completion & Supabase Write State ─────────────────────────────────

    /**
     * Called when the 5th question is completed in the plugin.
     * Writes state to Supabase (daily_submissions UPSERT and profiles UPDATE) and local StateManager.
     */
    private fun onQuizComplete(
        correctCount: Int,
        questions: List<Question>,
        earnedXp: Int,
        selectedOptions: List<Int>,
        answers: Map<String, UserAnswerPayload>,
        categoryResults: Map<String, CategoryResult>
    ) {
        val state = StateManager.getInstance()
        val today = state.todayIstanbulString()
        val userId = state.getUserId()

        // 1. Persist locally - only update streak and completion date if 5 questions completed
        if (answers.size >= 5) {
            state.recordQuizCompletion(
                correctCount = correctCount,
                totalCount = questions.size,
                earnedXp = earnedXp,
                selectedOptions = selectedOptions,
                answers = answers,
                categoryResults = categoryResults
            )
        } else {
            state.savePartialSession(
                today = state.todayIstanbul(),
                score = correctCount,
                earnedXp = earnedXp,
                selectedOptions = selectedOptions,
                answers = answers
            )
        }

        // 2. Persist to Supabase if logged in (Single Source of Truth)
        if (!userId.isNullOrBlank()) {
            ApplicationManager.getApplication().executeOnPooledThread {
                val supabase = SupabaseService.getInstance()

                // A: UPSERT daily_submissions
                val subPayload = DailySubmissionPayload(
                    user_id = userId,
                    date = today,
                    score = correctCount,
                    earned_xp = earnedXp,
                    answers = answers,
                    selected_options = selectedOptions
                )
                supabase.upsertDailySubmission(subPayload)

                // B: UPDATE profiles (streak, max_streak, total_xp, last_completed_date)
                // Sadece 5. soru tamamlandığında profiles tablosunu güncelle!
                if (answers.size >= 5) {
                    val currentStreak = state.currentStreak
                    val maxStreak = state.longestStreak
                    val totalXp = state.totalXp
                    supabase.updateProfile(userId, currentStreak, maxStreak, totalXp, today)
                }
            }
        }

        // 3. Show ResultScreen with the 5 Android Bugdroids if quiz completed
        if (answers.size >= 5) {
            showResultScreen(
                correctCount = correctCount,
                questions = questions,
                earnedXp = earnedXp,
                selectedOptions = selectedOptions,
                answers = answers
            )
            header.refresh()
        }
    }

    // ── Header Actions ─────────────────────────────────────────────────────────

    private fun toggleLanguage() {
        val state = StateManager.getInstance()
        state.language = if (state.language == AppLanguage.TR) AppLanguage.EN else AppLanguage.TR
        header.refresh()
        loadingScreen.refreshLanguage()
        quizScreen?.refreshLanguage()
        resultScreen?.refreshLanguage()
    }

    private fun onLoginClick() {
        BrowserUtil.browse("https://droidle.dev/login?source=plugin")
    }

    private fun onLogoutClick() {
        val state = StateManager.getInstance()
        state.clearAuthToken()
        header.refresh()
        start()
    }

    private fun showError(msg: String) {
        errorScreen.setMessage(msg)
        show(Screen.ERROR)
    }

    fun triggerSync() {
        quizScreen?.checkRemoteSync()
    }

    private fun show(name: String) {
        cards.show(deck, name)
    }
}
