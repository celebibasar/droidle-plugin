package app.droidle.plugin

import app.droidle.plugin.model.DocLink
import app.droidle.plugin.model.Question
import app.droidle.plugin.state.AppLanguage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class QuestionModelTest {

    @Test
    fun `test resolvedCorrectAnswer prefers correct_answer then correct_option`() {
        val q1 = Question(id = "1", correct_answer = "B", correct_option = "C")
        assertEquals("B", q1.resolvedCorrectAnswer)

        val q2 = Question(id = "2", correct_answer = null, correct_option = "D")
        assertEquals("D", q2.resolvedCorrectAnswer)
    }

    @Test
    fun `test multilingual question and option display`() {
        val q = Question(
            id = "q-compose",
            question = "Default Soru",
            question_tr = "Compose Recomposition nasıl optimize edilir?",
            question_en = "How to optimize Compose Recomposition?",
            options_tr = mapOf("A" to "remember ile", "B" to "global değişken ile"),
            options_en = mapOf("A" to "Using remember", "B" to "Using global var"),
            explanation_tr = "remember rekompozisyonlar arasında değeri önbelleğe alır.",
            explanation_en = "remember caches values across recompositions."
        )

        assertEquals("Compose Recomposition nasıl optimize edilir?", q.displayQuestion(AppLanguage.TR))
        assertEquals("How to optimize Compose Recomposition?", q.displayQuestion(AppLanguage.EN))

        assertEquals("remember ile", q.displayOptions(AppLanguage.TR)["A"])
        assertEquals("Using remember", q.displayOptions(AppLanguage.EN)["A"])

        assertEquals("remember rekompozisyonlar arasında değeri önbelleğe alır.", q.displayExplanation(AppLanguage.TR))
        assertEquals("remember caches values across recompositions.", q.displayExplanation(AppLanguage.EN))
    }

    @Test
    fun `test XP values per question difficulty match web specifications`() {
        assertEquals(10, Question(id = "1", difficulty = "Junior").xpValue)
        assertEquals(25, Question(id = "2", difficulty = "Mid").xpValue)
        assertEquals(50, Question(id = "3", difficulty = "Senior").xpValue)
        assertEquals(75, Question(id = "4", difficulty = "Expert").xpValue)
        assertEquals(10, Question(id = "5", difficulty = "Unknown").xpValue)
    }
}
