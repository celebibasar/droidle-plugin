package app.droidle.plugin

import app.droidle.plugin.model.DailySubmissionRow
import app.droidle.plugin.model.Question
import app.droidle.plugin.model.UserAnswerPayload
import app.droidle.plugin.ui.QuizScreen
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities

class QuizScreenSyncTest {

    private val sampleQuestions = listOf(
        Question(
            id = "q1",
            scheduled_date = "2026-10-29",
            category = "Compose",
            difficulty = "Junior",
            question = "Sample Question 1",
            options = mapOf("A" to "Option A", "B" to "Option B", "C" to "Option C", "D" to "Option D"),
            correct_answer = "A",
            explanation = "Explanation 1"
        ),
        Question(
            id = "q2",
            scheduled_date = "2026-10-29",
            category = "Coroutines",
            difficulty = "Mid",
            question = "Sample Question 2",
            options = mapOf("A" to "Option A", "B" to "Option B", "C" to "Option C", "D" to "Option D"),
            correct_answer = "B",
            explanation = "Explanation 2"
        ),
        Question(
            id = "q3",
            scheduled_date = "2026-10-29",
            category = "Architecture",
            difficulty = "Senior",
            question = "Sample Question 3",
            options = mapOf("A" to "Option A", "B" to "Option B", "C" to "Option C", "D" to "Option D"),
            correct_answer = "B",
            explanation = "Explanation 3"
        ),
        Question(
            id = "q4",
            scheduled_date = "2026-10-29",
            category = "Internals",
            difficulty = "Expert",
            question = "Sample Question 4",
            options = mapOf("A" to "Option A", "B" to "Option B", "C" to "Option C", "D" to "Option D"),
            correct_answer = "C",
            explanation = "Explanation 4"
        ),
        Question(
            id = "q5",
            scheduled_date = "2026-10-29",
            category = "Compose",
            difficulty = "Junior",
            question = "Sample Question 5",
            options = mapOf("A" to "Option A", "B" to "Option B", "C" to "Option C", "D" to "Option D"),
            correct_answer = "A",
            explanation = "Explanation 5"
        )
    )

    @Test
    fun `test handleRemoteSubmissionUpdate advances question when remote answers new question`() {
        val completedLatch = CountDownLatch(1)
        var completedCalled = false

        val screen = QuizScreen(
            questions = sampleQuestions,
            initialIndex = 0,
            project = null,
            onComplete = { _, _, _, _, _, _ ->
                completedCalled = true
                completedLatch.countDown()
            },
            onError = {}
        )

        val frame = JFrame()
        frame.contentPane.add(screen)
        frame.setSize(400, 600)
        frame.doLayout()

        // Remote submission answered Question 1 (index 0) on Web
        val remoteSub = DailySubmissionRow(
            id = "sub-web-q1",
            user_id = "test-user",
            date = "2026-10-29",
            score = 1,
            earned_xp = 10,
            selected_options = listOf(0, -1, -1, -1, -1),
            answers = mapOf(
                "q1" to UserAnswerPayload(
                    questionId = "q1",
                    selectedOption = "A",
                    isCorrect = true,
                    correctAnswer = "A",
                    xpEarned = 10
                )
            )
        )

        screen.handleRemoteSubmissionUpdate(remoteSub, null)

        // Wait for SwingUtilities.invokeLater to execute
        val syncLatch = CountDownLatch(1)
        SwingUtilities.invokeLater { syncLatch.countDown() }
        syncLatch.await(2, TimeUnit.SECONDS)

        assertFalse(completedCalled, "Quiz should not be completed yet")
        // QuizScreen should have advanced to question 2 (index 1)
        assertEquals(1, screen.getCurrentIndex(), "Should advance to question 2 (index 1)")
        assertEquals(1, screen.getCorrectCount(), "Correct count should be 1")
        assertEquals(10, screen.getEarnedXp(), "Earned XP should be 10")
        assertEquals(0, screen.getSelectedOptions().firstOrNull(), "Q1 option index should be 0")
        assertFalse(screen.isAnswered(), "New question 2 should not be marked answered yet")

        screen.dispose()
    }

    @Test
    fun `test handleRemoteSubmissionUpdate completes quiz when all 5 questions answered remotely`() {
        val completedLatch = CountDownLatch(1)
        var completedScore = 0
        var completedXp = 0
        var completedAnswersCount = 0

        val screen = QuizScreen(
            questions = sampleQuestions,
            initialIndex = 0,
            project = null,
            onComplete = { score, _, xp, _, answers, _ ->
                completedScore = score
                completedXp = xp
                completedAnswersCount = answers.size
                completedLatch.countDown()
            },
            onError = {}
        )

        val frame = JFrame()
        frame.contentPane.add(screen)
        frame.setSize(400, 600)

        val fullRemoteSub = DailySubmissionRow(
            id = "sub-web-full",
            user_id = "test-user",
            date = "2026-10-29",
            score = 5,
            earned_xp = 170,
            selected_options = listOf(0, 1, 1, 2, 0),
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, xpEarned = 10),
                "q2" to UserAnswerPayload(questionId = "q2", selectedOption = "B", isCorrect = true, xpEarned = 25),
                "q3" to UserAnswerPayload(questionId = "q3", selectedOption = "B", isCorrect = true, xpEarned = 50),
                "q4" to UserAnswerPayload(questionId = "q4", selectedOption = "C", isCorrect = true, xpEarned = 75),
                "q5" to UserAnswerPayload(questionId = "q5", selectedOption = "A", isCorrect = true, xpEarned = 10)
            )
        )

        screen.handleRemoteSubmissionUpdate(fullRemoteSub, null)

        assertTrue(completedLatch.await(3, TimeUnit.SECONDS), "onComplete should be invoked")
        assertEquals(5, completedScore)
        assertEquals(170, completedXp)
        assertEquals(5, completedAnswersCount)

        screen.dispose()
    }

    @Test
    fun `test handleRemoteSubmissionUpdate does not advance when user is viewing explanation and remote has no newer answers`() {
        // Resume on Question 2 (index 1) with Q1 already answered
        val screen = QuizScreen(
            questions = sampleQuestions,
            initialIndex = 0,
            initialSelectedOptions = listOf(0, -1, -1, -1, -1),
            initialAnswers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, xpEarned = 10)
            ),
            project = null,
            onComplete = { _, _, _, _, _, _ -> },
            onError = {}
        )

        val frame = JFrame()
        frame.contentPane.add(screen)
        frame.setSize(400, 600)

        // Remote comes in with the exact same answer (Q1) that local already has
        val sameRemoteSub = DailySubmissionRow(
            id = "sub-web-q1-echo",
            user_id = "test-user",
            date = "2026-10-29",
            score = 1,
            earned_xp = 10,
            selected_options = listOf(0, -1, -1, -1, -1),
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, xpEarned = 10)
            )
        )

        screen.handleRemoteSubmissionUpdate(sameRemoteSub, null)

        val syncLatch = CountDownLatch(1)
        SwingUtilities.invokeLater { syncLatch.countDown() }
        syncLatch.await(1, TimeUnit.SECONDS)

        // Should not trigger any disruptive changes
        assertEquals(0, screen.getCurrentIndex())
        screen.dispose()
    }

    @Test
    fun `test dispose stops poller without exception`() {
        val screen = QuizScreen(
            questions = sampleQuestions,
            initialIndex = 0,
            project = null,
            onComplete = { _, _, _, _, _, _ -> },
            onError = {}
        )

        screen.startSyncPoller()
        assertDoesNotThrow { screen.dispose() }
        assertDoesNotThrow { screen.dispose() } // multiple dispose calls safe
    }

    @Test
    fun `test handleRemoteSubmissionUpdate recomputes categoryResults cleanly without duplicate counts across sync ticks`() {
        val screen = QuizScreen(
            questions = sampleQuestions,
            initialIndex = 0,
            project = null,
            onComplete = { _, _, _, _, _, _ -> },
            onError = {}
        )

        val frame = JFrame()
        frame.contentPane.add(screen)
        frame.setSize(400, 600)

        // Tick 1: Q1 (Compose) answered
        val sub1 = DailySubmissionRow(
            id = "sub-1",
            user_id = "u1",
            date = "2026-10-29",
            score = 1,
            earned_xp = 10,
            selected_options = listOf(0, -1, -1, -1, -1),
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", category = "Compose", selectedOption = "A", isCorrect = true, xpEarned = 10)
            )
        )
        screen.handleRemoteSubmissionUpdate(sub1, null)
        var latch = CountDownLatch(1)
        SwingUtilities.invokeLater { latch.countDown() }
        latch.await(2, TimeUnit.SECONDS)

        var cats = screen.getCategoryResults()
        assertEquals(1, cats["Compose"]?.attempted)
        assertEquals(1, cats["Compose"]?.correct)

        // Tick 2: Q2 (Coroutines) answered alongside Q1
        val sub2 = DailySubmissionRow(
            id = "sub-2",
            user_id = "u1",
            date = "2026-10-29",
            score = 2,
            earned_xp = 35,
            selected_options = listOf(0, 1, -1, -1, -1),
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", category = "Compose", selectedOption = "A", isCorrect = true, xpEarned = 10),
                "q2" to UserAnswerPayload(questionId = "q2", category = "Coroutines", selectedOption = "B", isCorrect = true, xpEarned = 25)
            )
        )
        screen.handleRemoteSubmissionUpdate(sub2, null)
        latch = CountDownLatch(1)
        SwingUtilities.invokeLater { latch.countDown() }
        latch.await(2, TimeUnit.SECONDS)

        cats = screen.getCategoryResults()
        // Compose must remain 1 (not duplicated to 2!), Coroutines must be 1
        assertEquals(1, cats["Compose"]?.attempted, "Compose attempted count should not be duplicated across ticks")
        assertEquals(1, cats["Compose"]?.correct, "Compose correct count should not be duplicated across ticks")
        assertEquals(1, cats["Coroutines"]?.attempted)
        assertEquals(1, cats["Coroutines"]?.correct)

        screen.dispose()
    }

    @Test
    fun `test handleRemoteSubmissionUpdate preserves local answer if local option was chosen while sync was in flight`() {
        val screen = QuizScreen(
            questions = sampleQuestions,
            initialIndex = 0,
            initialSelectedOptions = listOf(0, -1, -1, -1, -1),
            initialAnswers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, xpEarned = 10)
            ),
            project = null,
            onComplete = { _, _, _, _, _, _ -> },
            onError = {}
        )

        val frame = JFrame()
        frame.contentPane.add(screen)
        frame.setSize(400, 600)

        // Stale remote arrives with 0 answers (from before local answered Q1)
        val staleRemoteSub = DailySubmissionRow(
            id = "sub-stale",
            user_id = "test-user",
            date = "2026-10-29",
            score = 0,
            earned_xp = 0,
            selected_options = listOf(-1, -1, -1, -1, -1),
            answers = emptyMap()
        )

        screen.handleRemoteSubmissionUpdate(staleRemoteSub, null)

        val syncLatch = CountDownLatch(1)
        SwingUtilities.invokeLater { syncLatch.countDown() }
        syncLatch.await(1, TimeUnit.SECONDS)

        // Local answer Q1 must be preserved and not wiped out by stale remote
        assertEquals(1, screen.getCorrectCount(), "Local correct count must be preserved")
        assertEquals(10, screen.getEarnedXp(), "Local earned XP must be preserved")
        assertEquals(1, screen.getAnswersMap().size, "Answers map must preserve local answer")

        screen.dispose()
    }
}
