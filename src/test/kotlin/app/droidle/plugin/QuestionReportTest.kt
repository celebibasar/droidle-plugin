package app.droidle.plugin

import app.droidle.plugin.model.*
import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.state.StateManager
import app.droidle.plugin.ui.QuizScreen
import app.droidle.plugin.ui.ResultScreen
import app.droidle.plugin.ui.components.ModernExplanationCard
import app.droidle.plugin.ui.components.QuestionReportButton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Container
import java.util.concurrent.CountDownLatch
import javax.swing.JButton

class QuestionReportTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val sampleQuestion = Question(
        id = "q-report-test-1",
        category = "Compose",
        difficulty = "Mid",
        question = "What is remember in Compose?",
        question_tr = "Compose'ta remember nedir?",
        question_en = "What is remember in Compose?",
        options_tr = mapOf("A" to "Değer saklar", "B" to "Ekranı siler"),
        options_en = mapOf("A" to "Retains value across recompositions", "B" to "Clears display"),
        correct_answer = "A",
        explanation_tr = "remember değeri recomposition boyunca korur.",
        explanation_en = "remember retains a value across recompositions.",
        doc_links = listOf(DocLink("Docs", "https://developer.android.com/compose"))
    )

    private fun findAllButtons(container: Container): List<JButton> {
        val result = mutableListOf<JButton>()
        fun recurse(c: Container) {
            for (comp in c.components) {
                if (comp is JButton) {
                    result.add(comp)
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
    fun `test ReportReason keys and translations match web app contract exactly`() {
        val reasons = ReportReason.values()
        assertEquals(5, reasons.size)

        // 1. incorrect_answer
        val incorrectAnswer = ReportReason.INCORRECT_ANSWER
        assertEquals("incorrect_answer", incorrectAnswer.key)
        assertEquals("Cevap veya şıklar yanlış", incorrectAnswer.localizedLabel(AppLanguage.TR))
        assertEquals("Incorrect answer or options", incorrectAnswer.localizedLabel(AppLanguage.EN))

        // 2. broken_link
        val brokenLink = ReportReason.BROKEN_LINK
        assertEquals("broken_link", brokenLink.key)
        assertEquals("Kaynak veya doküman linki hatalı", brokenLink.localizedLabel(AppLanguage.TR))
        assertEquals("Broken or invalid doc link", brokenLink.localizedLabel(AppLanguage.EN))

        // 3. explanation_error
        val explanationError = ReportReason.EXPLANATION_ERROR
        assertEquals("explanation_error", explanationError.key)
        assertEquals("Açıklama hatalı veya eksik", explanationError.localizedLabel(AppLanguage.TR))
        assertEquals("Inaccurate or misleading explanation", explanationError.localizedLabel(AppLanguage.EN))

        // 4. typo
        val typo = ReportReason.TYPO
        assertEquals("typo", typo.key)
        assertEquals("Yazım / çeviri hatası", typo.localizedLabel(AppLanguage.TR))
        assertEquals("Typo or translation error", typo.localizedLabel(AppLanguage.EN))

        // 5. other
        val other = ReportReason.OTHER
        assertEquals("other", other.key)
        assertEquals("Diğer bir sorun", other.localizedLabel(AppLanguage.TR))
        assertEquals("Other issue", other.localizedLabel(AppLanguage.EN))

        // fromKey resolution
        assertEquals(ReportReason.INCORRECT_ANSWER, ReportReason.fromKey("incorrect_answer"))
        assertEquals(ReportReason.BROKEN_LINK, ReportReason.fromKey("broken_link"))
        assertEquals(ReportReason.EXPLANATION_ERROR, ReportReason.fromKey("explanation_error"))
        assertEquals(ReportReason.TYPO, ReportReason.fromKey("typo"))
        assertEquals(ReportReason.OTHER, ReportReason.fromKey("other"))
        assertEquals(ReportReason.INCORRECT_ANSWER, ReportReason.fromKey("unknown_key"))
    }

    @Test
    fun `test QuestionReportPayload serialization format matches Supabase table schema`() {
        val payload = QuestionReportPayload(
            id = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            question_id = "compose-state-q1",
            user_id = "74e1acee-85c8-4871-98dd-ccddb6add392",
            reason = "broken_link",
            details = "Doc link returns 404",
            status = "pending"
        )

        val serialized = json.encodeToString(QuestionReportPayload.serializer(), payload)
        assertTrue(serialized.contains(""""id":"f47ac10b-58cc-4372-a567-0e02b2c3d479""""))
        assertTrue(serialized.contains(""""question_id":"compose-state-q1""""))
        assertTrue(serialized.contains(""""user_id":"74e1acee-85c8-4871-98dd-ccddb6add392""""))
        assertTrue(serialized.contains(""""reason":"broken_link""""))
        assertTrue(serialized.contains(""""details":"Doc link returns 404""""))
        assertTrue(serialized.contains(""""status":"pending""""))

        val deserialized = json.decodeFromString(QuestionReportPayload.serializer(), serialized)
        assertEquals(payload.id, deserialized.id)
        assertEquals(payload.question_id, deserialized.question_id)
        assertEquals(payload.user_id, deserialized.user_id)
        assertEquals(payload.reason, deserialized.reason)
        assertEquals(payload.details, deserialized.details)
        assertEquals(payload.status, deserialized.status)
    }

    @Test
    fun `test WebReportRequestPayload serialization matches web api endpoint contract`() {
        val webPayload = WebReportRequestPayload(
            questionId = "q-web-1",
            reason = "typo",
            details = "Spelling error in option B",
            userId = "user-123"
        )

        val serialized = json.encodeToString(WebReportRequestPayload.serializer(), webPayload)
        assertTrue(serialized.contains(""""questionId":"q-web-1""""))
        assertTrue(serialized.contains(""""reason":"typo""""))
        assertTrue(serialized.contains(""""details":"Spelling error in option B""""))
        assertTrue(serialized.contains(""""userId":"user-123""""))
    }

    @Test
    fun `test question report tracking logic without duplicates`() {
        val reportedSet = mutableSetOf<String>()
        fun isQuestionReported(id: String): Boolean = id.isNotBlank() && reportedSet.contains(id.trim())
        fun markQuestionAsReported(id: String) { if (id.isNotBlank()) reportedSet.add(id.trim()) }

        val qId1 = "q-test-101"
        val qId2 = "q-test-102"

        assertFalse(isQuestionReported(qId1))
        assertFalse(isQuestionReported(qId2))

        markQuestionAsReported(qId1)
        assertTrue(isQuestionReported(qId1))
        assertFalse(isQuestionReported(qId2))

        // Multiple marks of same question id
        markQuestionAsReported(qId1)
        assertEquals(1, reportedSet.size)
        assertTrue(reportedSet.contains(qId1))

        // Add second
        markQuestionAsReported(qId2)
        assertEquals(2, reportedSet.size)
        assertTrue(isQuestionReported(qId1))
        assertTrue(isQuestionReported(qId2))

        // Blank check
        assertFalse(isQuestionReported(""))
        markQuestionAsReported("")
        assertEquals(2, reportedSet.size)

        reportedSet.clear()
        assertFalse(isQuestionReported(qId1))
        assertEquals(0, reportedSet.size)
    }

    @Test
    fun `test QuestionReportButton styling and state transitions for TR and EN`() {
        val reportedIds = mutableSetOf<String>()

        val btn = QuestionReportButton(
            currentQuestion = sampleQuestion,
            currentLang = AppLanguage.TR,
            compact = false,
            isReportedChecker = { reportedIds.contains(it) }
        )

        // 1. Initial Unreported state in TR
        assertEquals("⚑ Bildir", btn.text)
        assertEquals("Soruyu bildir", btn.toolTipText)
        assertFalse(btn.getIsReported())

        // 2. Switch to EN
        btn.updateQuestion(sampleQuestion, AppLanguage.EN)
        assertEquals("⚑ Report", btn.text)
        assertEquals("Report question", btn.toolTipText)
        assertFalse(btn.getIsReported())

        // 3. Mark as reported and refresh
        reportedIds.add(sampleQuestion.id)
        btn.refreshReportedState()
        assertTrue(btn.getIsReported())
        assertEquals("✓ Reported", btn.text)
        assertEquals("Report submitted for this question", btn.toolTipText)

        // 4. Switch back to TR while reported
        btn.updateQuestion(sampleQuestion, AppLanguage.TR)
        assertTrue(btn.getIsReported())
        assertEquals("✓ Bildirildi", btn.text)
        assertEquals("Bu soru için bildirim gönderildi", btn.toolTipText)

        // 5. Compact mode
        val compactBtn = QuestionReportButton(
            currentQuestion = sampleQuestion,
            currentLang = AppLanguage.TR,
            compact = true,
            isReportedChecker = { reportedIds.contains(it) }
        )
        assertTrue(compactBtn.getIsReported())
        assertEquals("✓", compactBtn.text)
    }

    @Test
    fun `test ModernExplanationCard report pill displays and updates state`() {
        val card = ModernExplanationCard()
        var reportClicked = false

        card.show(
            correct = true,
            text = "Explanation text",
            earnedXp = 25,
            docLinks = listOf(DocLink("Doc", "https://droidle.dev")),
            isEnglish = false,
            isReported = false,
            onReportClick = { reportClicked = true }
        )

        val buttons = findAllButtons(card)
        val reportBtn = buttons.firstOrNull { it.text.contains("Soruyu Bildir") }
        assertNotNull(reportBtn, "Report button pill must be present in explanation card")
        assertEquals("Soruyu Bildir ⚑", reportBtn?.text)
        assertEquals("Soruyu bildir", reportBtn?.toolTipText)

        reportBtn?.doClick()
        assertTrue(reportClicked, "Clicking report pill should invoke onReportClick callback")

        // Update to reported state
        card.refreshReportedState(true)
        val updatedButtons = findAllButtons(card)
        val reportedBtn = updatedButtons.firstOrNull { it.text.contains("Bildirildi") }
        assertNotNull(reportedBtn, "Pill must change to '✓ Bildirildi'")
        assertEquals("✓ Bildirildi", reportedBtn?.text)
        assertEquals("Bu soru için bildirim gönderildi", reportedBtn?.toolTipText)
    }

    @Test
    fun `test ModernExplanationCard report pill in English`() {
        val card = ModernExplanationCard()
        card.show(
            correct = false,
            text = "Explanation text",
            earnedXp = 0,
            docLinks = emptyList(),
            isEnglish = true,
            isReported = false,
            onReportClick = {}
        )

        val buttons = findAllButtons(card)
        val reportBtn = buttons.firstOrNull { it.text.contains("Report Question") }
        assertNotNull(reportBtn, "Report button pill must be present in explanation card in English")
        assertEquals("Report Question ⚑", reportBtn?.text)
        assertEquals("Report question", reportBtn?.toolTipText)

        card.refreshReportedState(true)
        val updatedButtons = findAllButtons(card)
        val reportedBtn = updatedButtons.firstOrNull { it.text.contains("Reported") }
        assertNotNull(reportedBtn)
        assertEquals("✓ Reported", reportedBtn?.text)
        assertEquals("Report submitted for this question", reportedBtn?.toolTipText)
    }

    @Test
    fun `test ResultScreen review card links header and explanation report buttons`() {
        val reportedIds = mutableSetOf<String>()
        val screen = ResultScreen(
            correctCount = 1,
            questions = listOf(sampleQuestion),
            earnedXp = 25,
            selectedOptions = listOf(0),
            answers = mapOf(
                sampleQuestion.id to UserAnswerPayload(
                    questionId = sampleQuestion.id,
                    selectedOption = "A",
                    isCorrect = true,
                    xpEarned = 25
                )
            ),
            onOpenWeb = {}
        )
        screen.isQuestionReportedProvider = { reportedIds.contains(it) }
        screen.overrideLanguage = AppLanguage.TR
        screen.refreshLanguage()

        val buttons = findAllButtons(screen).filterIsInstance<QuestionReportButton>()
        assertEquals(2, buttons.size, "ResultScreen review card must contain both header and explanation report buttons")
        val headerBtn = buttons[0]
        val explBtn = buttons[1]

        assertEquals("⚑ Bildir", headerBtn.text)
        assertEquals("⚑ Bildir", explBtn.text)
        assertFalse(headerBtn.getIsReported())
        assertFalse(explBtn.getIsReported())
    }

    @Test
    fun `test QuizScreen contains report button and updates on language refresh`() {
        val reportedIds = mutableSetOf<String>()

        val screen = QuizScreen(
            questions = listOf(sampleQuestion),
            initialIndex = 0,
            onComplete = { _, _, _, _, _, _ -> },
            onError = {}
        )
        screen.overrideLanguage = AppLanguage.TR
        screen.isQuestionReportedProvider = { reportedIds.contains(it) }
        screen.refreshLanguage()

        val buttons = findAllButtons(screen)
        val reportBtn = buttons.filterIsInstance<QuestionReportButton>().firstOrNull()
        assertNotNull(reportBtn, "QuizScreen header must contain QuestionReportButton")
        assertEquals("⚑ Bildir", reportBtn?.text)

        // Switch to EN
        screen.overrideLanguage = AppLanguage.EN
        screen.refreshLanguage()
        assertEquals("⚑ Report", reportBtn?.text)
    }

    @Test
    fun `test ResultScreen review card and explanation box contain report button in TR and EN`() {
        val reportedIds = mutableSetOf<String>()

        val screen = ResultScreen(
            correctCount = 1,
            questions = listOf(sampleQuestion),
            earnedXp = 25,
            selectedOptions = listOf(0),
            answers = mapOf(
                sampleQuestion.id to UserAnswerPayload(
                    questionId = sampleQuestion.id,
                    selectedOption = "A",
                    isCorrect = true,
                    xpEarned = 25
                )
            ),
            onOpenWeb = {}
        )
        screen.isQuestionReportedProvider = { reportedIds.contains(it) }

        // Test in TR
        screen.overrideLanguage = AppLanguage.TR
        screen.refreshLanguage()

        val buttonsTr = findAllButtons(screen).filterIsInstance<QuestionReportButton>()
        assertTrue(buttonsTr.isNotEmpty(), "ResultScreen must contain QuestionReportButton")
        assertTrue(buttonsTr.all { it.text == "⚑ Bildir" }, "Buttons should be in Turkish")

        // Mark reported
        reportedIds.add(sampleQuestion.id)
        screen.refreshLanguage()
        val buttonsReported = findAllButtons(screen).filterIsInstance<QuestionReportButton>()
        assertTrue(buttonsReported.all { it.text == "✓ Bildirildi" }, "Buttons should show '✓ Bildirildi'")

        // Switch to English
        screen.overrideLanguage = AppLanguage.EN
        screen.refreshLanguage()
        val buttonsEn = findAllButtons(screen).filterIsInstance<QuestionReportButton>()
        assertTrue(buttonsEn.all { it.text == "✓ Reported" }, "Buttons should show '✓ Reported' in English")
    }

    @Test
    fun `test UUID regex validates valid UUIDs and rejects invalid strings`() {
        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")

        // Valid UUIDs
        assertTrue(uuidRegex.matches("74e1acee-85c8-4871-98dd-ccddb6add392"))
        assertTrue(uuidRegex.matches("f47ac10b-58cc-4372-a567-0e02b2c3d479"))
        assertTrue(uuidRegex.matches("A0EEBC99-9C0B-4EF8-BB6D-6BB9BD380A11"))

        // Invalid strings
        assertFalse(uuidRegex.matches("not-a-uuid"))
        assertFalse(uuidRegex.matches("user-123"))
        assertFalse(uuidRegex.matches(""))
        assertFalse(uuidRegex.matches("74e1acee-85c8-4871-98dd"))
        assertFalse(uuidRegex.matches("74e1acee-85c8-4871-98dd-ccddb6add392-extra"))
    }

    @Test
    fun `test details length truncation and boundary logic`() {
        val exactly2000 = "a".repeat(2000)
        assertEquals(2000, exactly2000.length)
        assertEquals(2000, exactly2000.take(2000).length)

        val over2000 = "b".repeat(2050)
        assertEquals(2000, over2000.take(2000).length)

        val blank = "   \n\t  "
        assertNull(blank.trim().ifBlank { null })
    }

    @Test
    fun `test QuestionReportButton handles null or blank question safely`() {
        val nullBtn = QuestionReportButton(
            currentQuestion = null,
            currentLang = AppLanguage.TR,
            compact = false
        )
        assertFalse(nullBtn.getIsReported())
        assertEquals("⚑ Bildir", nullBtn.text)

        val blankQuestion = Question(id = "")
        nullBtn.updateQuestion(blankQuestion, AppLanguage.EN)
        assertFalse(nullBtn.getIsReported())
        assertEquals("⚑ Report", nullBtn.text)
    }
}
