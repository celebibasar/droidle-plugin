package app.droidle.plugin

import app.droidle.plugin.model.DocLink
import app.droidle.plugin.model.Question
import app.droidle.plugin.model.UserAnswerPayload
import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.ui.ResultScreen
import com.intellij.ui.components.JBLabel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JButton

class ResultScreenLocalizationTest {

    private val sampleQuestions = listOf(
        Question(
            id = "q1",
            scheduled_date = "2026-10-29",
            category = "Compose",
            difficulty = "Junior",
            question = "Default Q1",
            question_tr = "Compose'ta Recomposition nedir?",
            question_en = "What is Recomposition in Compose?",
            options_tr = mapOf("A" to "Yeniden çizim", "B" to "Bellek temizleme"),
            options_en = mapOf("A" to "Re-execution", "B" to "Memory cleanup"),
            correct_answer = "A",
            explanation_tr = "Recomposition Composable fonksiyonların yeniden çağrılmasıdır.",
            explanation_en = "Recomposition is re-executing Composable functions.",
            doc_links = listOf(DocLink("Docs", "https://developer.android.com/compose"))
        ),
        Question(
            id = "q2",
            scheduled_date = "2026-10-29",
            category = "Coroutines",
            difficulty = "Mid",
            question = "Default Q2",
            question_tr = "Dispatchers.IO ne için kullanılır?",
            question_en = "What is Dispatchers.IO used for?",
            options_tr = mapOf("A" to "UI işlemleri", "B" to "Disk ve Ağ G/Ç"),
            options_en = mapOf("A" to "UI operations", "B" to "Disk and Network I/O"),
            correct_answer = "B",
            explanation_tr = "Dispatchers.IO disk ve network operasyonları için optimize edilmiştir.",
            explanation_en = "Dispatchers.IO is optimized for disk and network operations.",
            doc_links = listOf(DocLink("Docs", "https://kotlinlang.org/coroutines"))
        )
    )

    private fun findAllLabels(container: Container): List<String> {
        val result = mutableListOf<String>()
        fun recurse(c: Container) {
            for (comp in c.components) {
                if (comp is JBLabel) {
                    result.add(comp.text)
                }
                if (comp is Container) {
                    recurse(comp)
                }
            }
        }
        recurse(container)
        return result
    }

    private fun findAllButtons(container: Container): List<String> {
        val result = mutableListOf<String>()
        fun recurse(c: Container) {
            for (comp in c.components) {
                if (comp is JButton) {
                    result.add(comp.text)
                }
                if (comp is Container) {
                    recurse(comp)
                }
            }
        }
        recurse(container)
        return result
    }

    @Test
    fun `test ResultScreen default Turkish labels and elements`() {
        val screen = ResultScreen(
            correctCount = 2,
            questions = sampleQuestions,
            earnedXp = 35,
            selectedOptions = listOf(0, 1),
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, correctAnswer = "A", xpEarned = 10),
                "q2" to UserAnswerPayload(questionId = "q2", selectedOption = "B", isCorrect = true, correctAnswer = "B", xpEarned = 25)
            ),
            onOpenWeb = {}
        )
        screen.overrideLanguage = AppLanguage.TR
        screen.refreshLanguage()

        val allLabels = findAllLabels(screen)
        val allButtons = findAllButtons(screen)

        // Celebration & score labels in TR
        assertTrue(allLabels.any { it.contains("Tebrikler!") }, "Celebration title should be in TR")
        assertTrue(allLabels.any { it.contains("Bugünün Android testini tamamladınız.") }, "Celebration desc should be in TR")
        assertTrue(allLabels.any { it.contains("2 / 2 Doğru") }, "Score should indicate 'Doğru' in TR")
        assertTrue(allLabels.any { it.contains("✨ +35 Kazanılan XP") }, "XP badge should be in TR")
        assertTrue(allLabels.any { it.contains("Günlük Seri") }, "Streak badge should be in TR")

        // Review instructions in TR
        assertTrue(allLabels.any { it.contains("Soruları İncele - Detayları görmek için Bugdroid'lere tıklayın") }, "Review instruction should be in TR")

        // Leaderboard button in TR
        assertTrue(allButtons.any { it == "Liderlik Tablosu" }, "Leaderboard button should be in TR")

        // Review card elements in TR
        assertTrue(allLabels.any { it.contains("Soru 1 / 2") }, "Question label should be in TR")
        assertTrue(allLabels.any { it.contains("✓ Doğru") }, "Correct status badge should be in TR")
        assertTrue(allLabels.any { it.contains("Compose'ta Recomposition nedir?") }, "Question text should be in TR")
        assertTrue(allLabels.any { it.contains("💡 TEKNİK AÇIKLAMA") }, "Technical explanation header should be in TR")
        assertTrue(allLabels.any { it.contains("Recomposition Composable fonksiyonların yeniden çağrılmasıdır.") }, "Explanation text should be in TR")
        assertTrue(allButtons.any { it.contains("Resmi Dokümana Git ↗") }, "Doc link button should be in TR")

        // Breakdown in TR
        assertTrue(allLabels.any { it.contains("📊 Konu Dağılımı") }, "Topic breakdown header should be in TR")
        assertTrue(allLabels.any { it.contains("⚡ Zorluk Dağılımı") }, "Difficulty breakdown header should be in TR")
    }

    @Test
    fun `test ResultScreen dynamic refreshLanguage to English`() {
        val screen = ResultScreen(
            correctCount = 1,
            questions = sampleQuestions,
            earnedXp = 10,
            selectedOptions = listOf(0, 0),
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, correctAnswer = "A", xpEarned = 10),
                "q2" to UserAnswerPayload(questionId = "q2", selectedOption = "A", isCorrect = false, correctAnswer = "B", xpEarned = 0)
            ),
            onOpenWeb = {}
        )

        // Initially in TR
        screen.overrideLanguage = AppLanguage.TR
        screen.refreshLanguage()
        var allButtons = findAllButtons(screen)
        assertTrue(allButtons.any { it == "Liderlik Tablosu" })

        // Switch to English and refresh
        screen.overrideLanguage = AppLanguage.EN
        screen.refreshLanguage()

        val allLabels = findAllLabels(screen)
        allButtons = findAllButtons(screen)

        // Celebration & score labels in EN
        assertTrue(allLabels.any { it.contains("Congratulations!") }, "Celebration title should be in EN")
        assertTrue(allLabels.any { it.contains("You completed today's Android quiz.") }, "Celebration desc should be in EN")
        assertTrue(allLabels.any { it.contains("1 / 2 Correct") }, "Score should indicate 'Correct' in EN")
        assertTrue(allLabels.any { it.contains("✨ +10 Earned XP") }, "XP badge should be in EN")
        assertTrue(allLabels.any { it.contains("Streak") }, "Streak badge should be in EN")

        // Review instructions in EN
        assertTrue(allLabels.any { it.contains("Review Questions - Click Bugdroids to see details") }, "Review instruction should be in EN")

        // Leaderboard button in EN
        assertTrue(allButtons.any { it == "Leaderboard" }, "Leaderboard button should be in EN")

        // Initial card shows question 2 (first wrong) or question 1
        // Let's verify review card contains English question label and localized explanation
        assertTrue(allLabels.any { it.contains("Question ") }, "Question label should be in EN")
        assertTrue(allLabels.any { it.contains("💡 TECHNICAL EXPLANATION") }, "Technical explanation header should be in EN")
        assertTrue(allButtons.any { it.contains("Official Documentation ↗") }, "Doc link button should be in EN")

        // Breakdown in EN
        assertTrue(allLabels.any { it.contains("📊 Topic Breakdown") }, "Topic breakdown header should be in EN")
        assertTrue(allLabels.any { it.contains("⚡ Difficulty Breakdown") }, "Difficulty breakdown header should be in EN")
        assertTrue(allLabels.any { it.contains("1 question") }, "Count text should say '1 question' in EN")
    }

    @Test
    fun `test ResultScreen incorrect question shows localized incorrect status`() {
        val screen = ResultScreen(
            correctCount = 0,
            questions = sampleQuestions,
            earnedXp = 0,
            selectedOptions = listOf(1, 0),
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "B", isCorrect = false, correctAnswer = "A", xpEarned = 0),
                "q2" to UserAnswerPayload(questionId = "q2", selectedOption = "A", isCorrect = false, correctAnswer = "B", xpEarned = 0)
            ),
            onOpenWeb = {}
        )

        // In TR
        screen.overrideLanguage = AppLanguage.TR
        screen.refreshLanguage()
        var allLabels = findAllLabels(screen)
        assertTrue(allLabels.any { it.contains("✗ Yanlış") }, "Incorrect status should be '✗ Yanlış' in TR")

        // In EN
        screen.overrideLanguage = AppLanguage.EN
        screen.refreshLanguage()
        allLabels = findAllLabels(screen)
        assertTrue(allLabels.any { it.contains("✗ Incorrect") }, "Incorrect status should be '✗ Incorrect' in EN")
    }

    @Test
    fun `test ResultScreen reversible language toggling TR to EN and back to TR`() {
        val screen = ResultScreen(
            correctCount = 2,
            questions = sampleQuestions,
            earnedXp = 35,
            selectedOptions = listOf(0, 1),
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, correctAnswer = "A", xpEarned = 10),
                "q2" to UserAnswerPayload(questionId = "q2", selectedOption = "B", isCorrect = true, correctAnswer = "B", xpEarned = 25)
            ),
            onOpenWeb = {}
        )

        // 1. TR
        screen.overrideLanguage = AppLanguage.TR
        screen.refreshLanguage()
        assertTrue(findAllLabels(screen).any { it.contains("Tebrikler!") })
        assertTrue(findAllButtons(screen).any { it == "Liderlik Tablosu" })

        // 2. Switch to EN
        screen.overrideLanguage = AppLanguage.EN
        screen.refreshLanguage()
        assertTrue(findAllLabels(screen).any { it.contains("Congratulations!") })
        assertTrue(findAllButtons(screen).any { it == "Leaderboard" })

        // 3. Switch back to TR
        screen.overrideLanguage = AppLanguage.TR
        screen.refreshLanguage()
        assertTrue(findAllLabels(screen).any { it.contains("Tebrikler!") })
        assertTrue(findAllButtons(screen).any { it == "Liderlik Tablosu" })
        assertFalse(findAllButtons(screen).any { it == "Leaderboard" })
    }

    @Test
    fun `test bugdroid icon click in English updates review card with localized question details`() {
        val screen = ResultScreen(
            correctCount = 1,
            questions = sampleQuestions,
            earnedXp = 10,
            selectedOptions = listOf(0, 0),
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, correctAnswer = "A", xpEarned = 10),
                "q2" to UserAnswerPayload(questionId = "q2", selectedOption = "A", isCorrect = false, correctAnswer = "B", xpEarned = 0)
            ),
            onOpenWeb = {}
        )
        screen.overrideLanguage = AppLanguage.EN
        screen.refreshLanguage()

        val icons = screen.getBugdroidIcons()
        assertEquals(2, icons.size)

        // Click on Question 2 (index 1)
        val icon2 = icons[1]
        val mouseEvent = java.awt.event.MouseEvent(
            icon2,
            java.awt.event.MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(),
            0,
            10, 10, 1, false
        )
        icon2.mouseListeners.forEach { it.mousePressed(mouseEvent) }

        assertEquals(1, screen.getSelectedQuestionIndex())
        val cardLabels = findAllLabels(screen.getReviewCardContainer())
        assertTrue(cardLabels.any { it.contains("Question 2 / 2") }, "Should show Question 2 / 2 in English")
        assertTrue(cardLabels.any { it.contains("✗ Incorrect") }, "Should show ✗ Incorrect in English")
        assertTrue(cardLabels.any { it.contains("What is Dispatchers.IO used for?") }, "Should show question 2 English text")
        assertTrue(cardLabels.any { it.contains("💡 TECHNICAL EXPLANATION") }, "Should show English technical explanation header")
    }

    @Test
    fun `test bugdroid tooltips in TR and EN`() {
        val screen = ResultScreen(
            correctCount = 1,
            questions = sampleQuestions,
            earnedXp = 10,
            selectedOptions = listOf(0, 0),
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, correctAnswer = "A", xpEarned = 10),
                "q2" to UserAnswerPayload(questionId = "q2", selectedOption = "A", isCorrect = false, correctAnswer = "B", xpEarned = 0)
            ),
            onOpenWeb = {}
        )

        screen.overrideLanguage = AppLanguage.TR
        screen.refreshLanguage()
        val trIcons = screen.getBugdroidIcons()
        assertEquals("1. Soru (Doğru)", trIcons[0].toolTipText)
        assertEquals("2. Soru (Yanlış)", trIcons[1].toolTipText)

        screen.overrideLanguage = AppLanguage.EN
        screen.refreshLanguage()
        val enIcons = screen.getBugdroidIcons()
        assertEquals("Question 1 (Correct)", enIcons[0].toolTipText)
        assertEquals("Question 2 (Incorrect)", enIcons[1].toolTipText)
    }

    @Test
    fun `test ResultScreen handles snake_case selected_option and case-insensitivity`() {
        val screen = ResultScreen(
            correctCount = 1,
            questions = sampleQuestions,
            earnedXp = 10,
            selectedOptions = emptyList(),
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selected_option = "a", correctAnswer = "a", xpEarned = 10),
                "q2" to UserAnswerPayload(questionId = "q2", selected_option = "a", correctAnswer = "B", xpEarned = 0)
            ),
            onOpenWeb = {}
        )

        screen.overrideLanguage = AppLanguage.EN
        screen.refreshLanguage()
        val icons = screen.getBugdroidIcons()
        assertTrue(icons[0].isCorrect, "q1 should be resolved as correct even with lowercase 'a' and selected_option")
        assertFalse(icons[1].isCorrect, "q2 should be resolved as incorrect")
    }

    @Test
    fun `test ResultScreen perfect score caption adapts to total question count`() {
        val screen = ResultScreen(
            correctCount = 2,
            questions = sampleQuestions,
            earnedXp = 35,
            onOpenWeb = {}
        )

        screen.overrideLanguage = AppLanguage.EN
        screen.refreshLanguage()
        val enLabels = findAllLabels(screen)
        assertTrue(enLabels.any { it.contains("Perfect! All 2 Correct! 🎉") }, "Should say 'All 2 Correct' for 2 questions")

        screen.overrideLanguage = AppLanguage.TR
        screen.refreshLanguage()
        val trLabels = findAllLabels(screen)
        assertTrue(trLabels.any { it.contains("Kusursuz! 2'de 2 Yaptın! 🎉") }, "Should say '2'de 2' for 2 questions")
    }
}
