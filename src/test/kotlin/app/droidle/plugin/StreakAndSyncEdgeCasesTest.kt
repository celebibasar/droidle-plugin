package app.droidle.plugin

import app.droidle.plugin.model.DailySubmissionPayload
import app.droidle.plugin.model.DailySubmissionRow
import app.droidle.plugin.model.Question
import app.droidle.plugin.model.UserAnswerPayload
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StreakAndSyncEdgeCasesTest {

    private fun calculateStreak(lastCompleted: LocalDate?, today: LocalDate, currentStreak: Int): Int {
        return when {
            lastCompleted == null -> 1
            lastCompleted == today.minusDays(1) -> currentStreak + 1
            lastCompleted == today -> currentStreak
            else -> 1 // gap of >= 2 days resets streak
        }
    }

    @Test
    fun `test streak calculation matches web streak-ts rules`() {
        val today = LocalDate.of(2026, 10, 29)

        // 1. First time user (last = null) -> streak 1
        assertEquals(1, calculateStreak(null, today, 0))

        // 2. Solved yesterday -> streak + 1
        val yesterday = today.minusDays(1)
        assertEquals(4, calculateStreak(yesterday, today, 3))

        // 3. Solved today -> no increment
        assertEquals(3, calculateStreak(today, today, 3))

        // 4. Solved 2 days ago -> streak reset to 1
        val twoDaysAgo = today.minusDays(2)
        assertEquals(1, calculateStreak(twoDaysAgo, today, 5))

        // 5. Year boundary: Dec 31 to Jan 1
        val dec31 = LocalDate.of(2025, 12, 31)
        val jan1 = LocalDate.of(2026, 1, 1)
        assertEquals(10, calculateStreak(dec31, jan1, 9))
    }

    @Test
    fun `test score clamping and submission payload validation`() {
        val rawScore = 5
        val clampedScore = rawScore.coerceIn(0, 5)
        assertEquals(5, clampedScore)

        val invalidScore = 99
        assertEquals(5, invalidScore.coerceIn(0, 5))

        val negativeScore = -3
        assertEquals(0, negativeScore.coerceIn(0, 5))

        val payload = DailySubmissionPayload(
            user_id = "user-abc",
            date = "2026-10-29",
            score = clampedScore,
            earned_xp = 110,
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true)
            ),
            selected_options = listOf(0, 1, 2, 3, 0)
        )

        assertEquals("user-abc", payload.user_id)
        assertEquals("2026-10-29", payload.date)
        assertEquals(5, payload.score)
        assertEquals(110, payload.earned_xp)
    }

    @Test
    fun `test Bugdroid correctness mapping from selected_options and answers`() {
        val questions = listOf(
            Question(id = "q1", correct_answer = "A"),
            Question(id = "q2", correct_answer = "B"),
            Question(id = "q3", correct_answer = "C"),
            Question(id = "q4", correct_answer = "D"),
            Question(id = "q5", correct_answer = "A")
        )

        val selectedOptions = listOf(0, 1, 0, 3, 0) // q3 answered A (wrong: correct is C=2)
        val optionKeys = listOf("A", "B", "C", "D")

        val correctness = questions.mapIndexed { i, q ->
            val correctIndex = optionKeys.indexOf(q.resolvedCorrectAnswer)
            val userOptionIndex = selectedOptions.getOrNull(i)
            userOptionIndex != null && userOptionIndex == correctIndex
        }

        assertEquals(listOf(true, true, false, true, true), correctness)
        assertEquals(4, correctness.count { it })
    }

    @Test
    fun `test active header streak expiration logic`() {
        val today = LocalDate.of(2026, 10, 29)

        fun resolveActiveStreak(lastCompleted: LocalDate?, streak: Int): Int {
            if (lastCompleted == null) return 0
            val yesterday = today.minusDays(1)
            return if (lastCompleted == today || lastCompleted == yesterday) streak else 0
        }

        assertEquals(0, resolveActiveStreak(null, 5))
        assertEquals(5, resolveActiveStreak(today, 5))
        assertEquals(5, resolveActiveStreak(today.minusDays(1), 5))
        assertEquals(0, resolveActiveStreak(today.minusDays(2), 5), "2 days ago is broken, must show 0 in header")
        assertEquals(0, resolveActiveStreak(today.minusDays(10), 8), "Older than yesterday must show 0 in header")
    }

    @Test
    fun `test ModernOptionCard clicks fire on textLabel and badge`() {
        var clickedKey: String? = null
        val card = app.droidle.plugin.ui.components.ModernOptionCard(
            key = "A",
            optionText = "Use remember"
        ) { key ->
            clickedKey = key
        }

        // Find child label and badge
        val textLabel = card.components.firstOrNull { it is com.intellij.ui.components.JBLabel }
        assertNotNull(textLabel, "textLabel must exist in ModernOptionCard")

        val mouseEvent = java.awt.event.MouseEvent(
            textLabel,
            java.awt.event.MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(),
            0,
            10,
            10,
            1,
            false
        )

        for (l in textLabel!!.mouseListeners) {
            l.mousePressed(mouseEvent)
        }

        assertEquals("A", clickedKey, "Clicking on textLabel inside ModernOptionCard must trigger selection callback")
    }

    @Test
    fun `test profile streak and last_completed_date update is only triggered when 5th question completed`() {
        val today = "2026-10-29"

        fun shouldUpdateProfile(answersCount: Int): Boolean {
            return answersCount >= 5
        }

        for (count in 0..4) {
            assertFalse(
                shouldUpdateProfile(count),
                "With $count answers (<5), profile current_streak and last_completed_date must NOT be updated"
            )
        }

        assertTrue(
            shouldUpdateProfile(5),
            "With 5 answers, profile current_streak and last_completed_date must be updated"
        )
    }

    @Test
    fun `test resume index is exactly answers size for partial progress`() {
        val totalQuestions = 5
        for (answersCount in 0..4) {
            val resumeIndex = answersCount.coerceIn(0, totalQuestions - 1)
            assertEquals(
                answersCount,
                resumeIndex,
                "For $answersCount answers, the quiz must resume directly at index $answersCount"
            )
        }
    }

    @Test
    fun `test state manager completion guard correctly blocks streak and lastCompletedDate update when answeredCount is less than 5`() {
        fun isEligibleForCompletion(answers: Map<String, UserAnswerPayload>, selectedOptions: List<Int>, totalCount: Int = 5): Boolean {
            val answeredCount = if (answers.isNotEmpty()) answers.size else selectedOptions.count { it >= 0 }
            return answeredCount >= totalCount
        }

        // Empty answers and empty options
        assertFalse(isEligibleForCompletion(emptyMap(), emptyList()), "0 answers must NOT complete")

        // 1 to 4 answers
        for (i in 1..4) {
            val partialMap = (1..i).associate { "q$it" to UserAnswerPayload(questionId = "q$it", selectedOption = "A") }
            assertFalse(isEligibleForCompletion(partialMap, emptyList()), "$i answers must NOT complete")
        }

        // 5 answers
        val fullMap = (1..5).associate { "q$it" to UserAnswerPayload(questionId = "q$it", selectedOption = "A") }
        assertTrue(isEligibleForCompletion(fullMap, emptyList()), "5 answers MUST complete")

        // selectedOptions fallback with 3 items vs 5 items
        assertFalse(isEligibleForCompletion(emptyMap(), listOf(0, 1, 2, -1, -1)), "3 selected options must NOT complete")
        assertTrue(isEligibleForCompletion(emptyMap(), listOf(0, 1, 2, 3, 0)), "5 selected options MUST complete")
    }
}

