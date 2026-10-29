package app.droidle.plugin

import app.droidle.plugin.model.*
import app.droidle.plugin.state.StateManager
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

class SupabaseSyncTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Test
    fun `test Europe-Istanbul timezone calculation matches web app contract`() {
        val istanbulZone = StateManager.ISTANBUL_ZONE
        assertEquals("Europe/Istanbul", istanbulZone.id)

        val today = LocalDate.now(istanbulZone)
        val formatted = today.format(StateManager.DATE_FMT)

        // Matches YYYY-MM-DD
        assertTrue(formatted.matches(Regex("""^\d{4}-\d{2}-\d{2}$""")), "Date format must be YYYY-MM-DD: $formatted")
    }

    @Test
    fun `test JWT claims extraction for userId username and avatar`() {
        // Construct sample JWT: header.payload.signature
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payloadJson = """
            {
              "sub": "74e1acee-85c8-4871-98dd-ccddb6add392",
              "email": "test@droidle.app",
              "user_metadata": {
                "user_name": "droidledev",
                "avatar_url": "https://avatars.githubusercontent.com/u/12345?v=4"
              }
            }
        """.trimIndent()
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.toByteArray())
        val fakeJwt = "$header.$payload.fakeSignature"

        val parts = fakeJwt.split(".")
        assertEquals(3, parts.size)

        val decodedPayload = String(Base64.getUrlDecoder().decode(parts[1]))
        val parsedJson = json.parseToJsonElement(decodedPayload) as kotlinx.serialization.json.JsonObject

        val sub = (parsedJson["sub"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        assertEquals("74e1acee-85c8-4871-98dd-ccddb6add392", sub)

        val userMeta = parsedJson["user_metadata"] as? kotlinx.serialization.json.JsonObject
        val userName = (userMeta?.get("user_name") as? kotlinx.serialization.json.JsonPrimitive)?.content
        assertEquals("droidledev", userName)

        val avatar = (userMeta?.get("avatar_url") as? kotlinx.serialization.json.JsonPrimitive)?.content
        assertEquals("https://avatars.githubusercontent.com/u/12345?v=4", avatar)
    }

    @Test
    fun `test DailySubmissionRow serialization and completeness checks`() {
        val row = DailySubmissionRow(
            id = "sub-123",
            user_id = "u-456",
            date = "2026-10-29",
            score = 4,
            earned_xp = 110,
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, correctAnswer = "A"),
                "q2" to UserAnswerPayload(questionId = "q2", selectedOption = "B", isCorrect = true, correctAnswer = "B"),
                "q3" to UserAnswerPayload(questionId = "q3", selectedOption = "C", isCorrect = false, correctAnswer = "B"),
                "q4" to UserAnswerPayload(questionId = "q4", selectedOption = "D", isCorrect = true, correctAnswer = "D"),
                "q5" to UserAnswerPayload(questionId = "q5", selectedOption = "A", isCorrect = true, correctAnswer = "A")
            ),
            selected_options = listOf(0, 1, 2, 3, 0),
            created_at = "2026-10-29T10:00:00Z"
        )

        assertTrue(row.isComplete(5), "Row with 5 answers and no -1 in selected_options should be complete")

        val serialized = json.encodeToString(DailySubmissionRow.serializer(), row)
        assertTrue(serialized.contains(""""user_id":"u-456""""))
        assertTrue(serialized.contains(""""score":4"""))
        assertTrue(serialized.contains(""""earned_xp":110"""))

        val deserialized = json.decodeFromString(DailySubmissionRow.serializer(), serialized)
        assertEquals(row.user_id, deserialized.user_id)
        assertEquals(row.score, deserialized.score)
        assertEquals(row.earned_xp, deserialized.earned_xp)
        assertEquals(5, deserialized.answers.size)
        assertEquals(listOf(0, 1, 2, 3, 0), deserialized.selected_options)
    }

    @Test
    fun `test partial submission detects resume index correctly`() {
        val partial = DailySubmissionRow(
            user_id = "u-456",
            date = "2026-10-29",
            score = 2,
            earned_xp = 50,
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true),
                "q2" to UserAnswerPayload(questionId = "q2", selectedOption = "B", isCorrect = true)
            ),
            selected_options = listOf(0, 1, -1, -1, -1)
        )

        assertFalse(partial.isComplete(5))

        val firstUnanswered = partial.selected_options.indexOfFirst { it < 0 }
        assertEquals(2, firstUnanswered, "User answered questions 0 and 1, next is index 2 (question 3)")
    }

    @Test
    fun `test SupabaseProfile serialization and deserialization`() {
        val jsonStr = """
            [
              {
                "id": "74e1acee-85c8-4871-98dd-ccddb6add392",
                "username": "celebibasar",
                "name": "Başar Çelebi",
                "avatar_url": "https://avatars.githubusercontent.com/u/75533431?v=4",
                "role": "admin",
                "current_streak": 3,
                "max_streak": 5,
                "total_xp": 480,
                "last_completed_date": "2026-10-28"
              }
            ]
        """.trimIndent()

        val list = json.decodeFromString(ListSerializer(SupabaseProfile.serializer()), jsonStr)
        assertEquals(1, list.size)
        val profile = list[0]

        assertEquals("74e1acee-85c8-4871-98dd-ccddb6add392", profile.id)
        assertEquals("celebibasar", profile.username)
        assertEquals(3, profile.current_streak)
        assertEquals(5, profile.max_streak)
        assertEquals(480, profile.total_xp)
        assertEquals("2026-10-28", profile.last_completed_date)
    }

    @Test
    fun `test zero score complete submission is recognized as complete`() {
        val zeroScoreRow = DailySubmissionRow(
            user_id = "u-zero",
            date = "2026-10-29",
            score = 0,
            earned_xp = 0,
            answers = emptyMap(),
            selected_options = listOf(1, 2, 3, 0, 1) // 5 options, all wrong
        )

        assertTrue(zeroScoreRow.isComplete(5), "A user who answered all 5 questions with 0 score must still be complete")
    }

    @Test
    fun `test reconcileDailySubmission with selected_options only synthesizes answers and calculates XP`() {
        val questions = listOf(
            Question(id = "q1", category = "Compose", difficulty = "Junior", correct_answer = "A"),
            Question(id = "q2", category = "Coroutines", difficulty = "Mid", correct_answer = "B"),
            Question(id = "q3", category = "Architecture", difficulty = "Senior", correct_answer = "C"),
            Question(id = "q4", category = "Internals", difficulty = "Expert", correct_answer = "D"),
            Question(id = "q5", category = "Compose", difficulty = "Junior", correct_answer = "A")
        )

        // User picked A=0, B=1, C=2, D=3, A=0 -> all 5 correct
        val sub = DailySubmissionRow(
            user_id = "u-plugin",
            date = "2026-10-29",
            score = 5,
            earned_xp = 170,
            answers = emptyMap(),
            selected_options = listOf(0, 1, 2, 3, 0)
        )

        val reconciled = reconcileDailySubmission(sub, questions)

        assertTrue(reconciled.isComplete)
        assertEquals(5, reconciled.score)
        assertEquals(170, reconciled.earnedXp) // 10 + 25 + 50 + 75 + 10 = 170 XP
        assertEquals(listOf(0, 1, 2, 3, 0), reconciled.selectedOptions)
        assertEquals(5, reconciled.answers.size)
        assertEquals("A", reconciled.answers["q1"]?.selectedOption)
        assertEquals(true, reconciled.answers["q1"]?.isCorrect)
    }

    @Test
    fun `test reconcileDailySubmission with partial answers map reconstructs options and finds resume index`() {
        val questions = listOf(
            Question(id = "q1", category = "Compose", difficulty = "Junior", correct_answer = "A"),
            Question(id = "q2", category = "Coroutines", difficulty = "Mid", correct_answer = "B"),
            Question(id = "q3", category = "Architecture", difficulty = "Senior", correct_answer = "C"),
            Question(id = "q4", category = "Internals", difficulty = "Expert", correct_answer = "D"),
            Question(id = "q5", category = "Compose", difficulty = "Junior", correct_answer = "A")
        )

        // Web user answered only question 1 and 2, selected_options is empty
        val sub = DailySubmissionRow(
            user_id = "u-web-partial",
            date = "2026-10-29",
            score = 2,
            earned_xp = 35,
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, xpEarned = 10),
                "q2" to UserAnswerPayload(questionId = "q2", selectedOption = "B", isCorrect = true, xpEarned = 25)
            ),
            selected_options = emptyList()
        )

        val reconciled = reconcileDailySubmission(sub, questions)

        assertFalse(reconciled.isComplete)
        assertEquals(listOf(0, 1, -1, -1, -1), reconciled.selectedOptions)
        assertEquals(2, reconciled.firstUnansweredIndex, "Should resume at index 2 (question 3)")
        assertEquals(2, reconciled.score)
        assertEquals(35, reconciled.earnedXp)
    }

    @Test
    fun `test partial progress resume for answers size 1 to 4 never opens result screen and sets resume index to answers size`() {
        val questions = listOf(
            Question(id = "q1", category = "Compose", difficulty = "Junior", correct_answer = "A"),
            Question(id = "q2", category = "Coroutines", difficulty = "Mid", correct_answer = "B"),
            Question(id = "q3", category = "Architecture", difficulty = "Senior", correct_answer = "C"),
            Question(id = "q4", category = "Internals", difficulty = "Expert", correct_answer = "D"),
            Question(id = "q5", category = "Compose", difficulty = "Junior", correct_answer = "A")
        )

        for (answeredCount in 1..4) {
            val answersMap = mutableMapOf<String, UserAnswerPayload>()
            val optionsList = mutableListOf<Int>()
            for (i in 0 until answeredCount) {
                val q = questions[i]
                answersMap[q.id] = UserAnswerPayload(
                    questionId = q.id,
                    selectedOption = q.resolvedCorrectAnswer,
                    isCorrect = true,
                    xpEarned = q.xpValue
                )
                optionsList.add(i % 4)
            }
            while (optionsList.size < 5) {
                optionsList.add(-1)
            }

            val sub = DailySubmissionRow(
                user_id = "user-partial-$answeredCount",
                date = "2026-10-29",
                score = answeredCount,
                earned_xp = answeredCount * 10,
                answers = answersMap,
                selected_options = optionsList
            )

            assertFalse(sub.isComplete(5), "DailySubmissionRow with answers.size=$answeredCount (<5) must NOT be complete")

            val reconciled = reconcileDailySubmission(sub, questions)
            assertFalse(reconciled.isComplete, "Reconciled state with answers.size=$answeredCount (<5) must NOT be complete")
            assertEquals(answeredCount, reconciled.firstUnansweredIndex, "Resume index must exactly match answers.size ($answeredCount)")
        }
    }

    @Test
    fun `test complete submission with answers size 5 opens result screen`() {
        val questions = listOf(
            Question(id = "q1", category = "Compose", difficulty = "Junior", correct_answer = "A"),
            Question(id = "q2", category = "Coroutines", difficulty = "Mid", correct_answer = "B"),
            Question(id = "q3", category = "Architecture", difficulty = "Senior", correct_answer = "C"),
            Question(id = "q4", category = "Internals", difficulty = "Expert", correct_answer = "D"),
            Question(id = "q5", category = "Compose", difficulty = "Junior", correct_answer = "A")
        )

        val answersMap = questions.associate { q ->
            q.id to UserAnswerPayload(
                questionId = q.id,
                selectedOption = q.resolvedCorrectAnswer,
                isCorrect = true,
                xpEarned = q.xpValue
            )
        }

        val sub = DailySubmissionRow(
            user_id = "user-complete-5",
            date = "2026-10-29",
            score = 5,
            earned_xp = 170,
            answers = answersMap,
            selected_options = listOf(0, 1, 2, 3, 0)
        )

        assertTrue(sub.isComplete(5), "DailySubmissionRow with 5 answers must be complete")

        val reconciled = reconcileDailySubmission(sub, questions)
        assertTrue(reconciled.isComplete, "Reconciled state with 5 answers must be complete")
        assertEquals(5, reconciled.score)
        assertEquals(170, reconciled.earnedXp)
        assertEquals(5, reconciled.answers.size)
    }

    @Test
    fun `test submission with 5 selected_options but fewer than 5 answers is NOT complete`() {
        val questions = listOf(
            Question(id = "q1", category = "Compose", difficulty = "Junior", correct_answer = "A"),
            Question(id = "q2", category = "Coroutines", difficulty = "Mid", correct_answer = "B"),
            Question(id = "q3", category = "Architecture", difficulty = "Senior", correct_answer = "C"),
            Question(id = "q4", category = "Internals", difficulty = "Expert", correct_answer = "D"),
            Question(id = "q5", category = "Compose", difficulty = "Junior", correct_answer = "A")
        )

        // Answers map only has 2 questions answered, even if selected_options had 5 entries
        val sub = DailySubmissionRow(
            user_id = "user-mismatch",
            date = "2026-10-29",
            score = 2,
            earned_xp = 35,
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true),
                "q2" to UserAnswerPayload(questionId = "q2", selectedOption = "B", isCorrect = true)
            ),
            selected_options = listOf(0, 1, 2, 3, 0)
        )

        assertFalse(sub.isComplete(5), "When answers map has fewer than 5 questions, submission must NOT be complete")
        val reconciled = reconcileDailySubmission(sub, questions)
        assertFalse(reconciled.isComplete, "Reconciled state must NOT be complete when answers.size < 5")
        assertEquals(2, reconciled.firstUnansweredIndex, "Resume index must be answers.size (2)")
    }

    @Test
    fun `test reconcileDailySubmission normalizes null isCorrect and xpEarned in UserAnswerPayload`() {
        val questions = listOf(
            Question(id = "q1", category = "Compose", difficulty = "Junior", correct_answer = "A"),
            Question(id = "q2", category = "Coroutines", difficulty = "Senior", correct_answer = "B"),
            Question(id = "q3", category = "Architecture", difficulty = "Mid", correct_answer = "C"),
            Question(id = "q4", category = "Internals", difficulty = "Expert", correct_answer = "D"),
            Question(id = "q5", category = "Compose", difficulty = "Junior", correct_answer = "A")
        )

        // Web saved raw payload where isCorrect, xpEarned, category are null
        val sub = DailySubmissionRow(
            user_id = "user-null-fields",
            date = "2026-10-29",
            score = 0,
            earned_xp = 0,
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = null, selectedOption = "A", isCorrect = null, xpEarned = null),
                "q2" to UserAnswerPayload(questionId = null, selectedOption = "C", isCorrect = null, xpEarned = null) // Wrong (correct is B)
            ),
            selected_options = emptyList()
        )

        val reconciled = reconcileDailySubmission(sub, questions)

        assertFalse(reconciled.isComplete)
        assertEquals(2, reconciled.answers.size)
        assertEquals(2, reconciled.firstUnansweredIndex)

        val ans1 = reconciled.answers["q1"]
        assertNotNull(ans1)
        assertEquals("q1", ans1!!.questionId)
        assertEquals("A", ans1.selectedOption)
        assertEquals(true, ans1.isCorrect)
        assertEquals(10, ans1.xpEarned) // Junior = 10 XP

        val ans2 = reconciled.answers["q2"]
        assertNotNull(ans2)
        assertEquals("q2", ans2!!.questionId)
        assertEquals("C", ans2.selectedOption)
        assertEquals(false, ans2.isCorrect)
        assertEquals(0, ans2.xpEarned)
    }

    @Test
    fun `test full rule 1 - less than 5 answers resumes quiz and exactly 5 answers shows results`() {
        val questions = listOf(
            Question(id = "q1", correct_answer = "A"),
            Question(id = "q2", correct_answer = "B"),
            Question(id = "q3", correct_answer = "C"),
            Question(id = "q4", correct_answer = "D"),
            Question(id = "q5", correct_answer = "A")
        )

        for (count in 0..4) {
            val answers = (0 until count).associate { i ->
                questions[i].id to UserAnswerPayload(
                    questionId = questions[i].id,
                    selectedOption = "A",
                    isCorrect = true,
                    xpEarned = 10
                )
            }
            val sub = DailySubmissionRow(
                user_id = "user-$count",
                date = "2026-10-29",
                answers = answers
            )
            val reconciled = reconcileDailySubmission(sub, questions)
            val answeredCount = sub.answers.size.takeIf { it > 0 } ?: reconciled.answers.size
            val shouldShowResultScreen = answeredCount >= 5
            assertFalse(shouldShowResultScreen, "Count $count (<5) must NOT open result screen")
            val resumeIndex = answeredCount.coerceIn(0, questions.size - 1)
            assertEquals(count, resumeIndex, "Count $count must resume at index $count")
        }

        // 5 answers: exactly 5 answers shows result screen
        val fullAnswers = (0 until 5).associate { i ->
            questions[i].id to UserAnswerPayload(
                questionId = questions[i].id,
                selectedOption = "A",
                isCorrect = true,
                xpEarned = 10
            )
        }
        val fullSub = DailySubmissionRow(
            user_id = "user-5",
            date = "2026-10-29",
            answers = fullAnswers
        )
        val fullReconciled = reconcileDailySubmission(fullSub, questions)
        val fullAnsweredCount = fullReconciled.answeredCount
        val shouldShowResultScreen = fullReconciled.isComplete
        assertTrue(shouldShowResultScreen, "Count 5 must open result screen")
    }

    @Test
    fun `test web-to-plugin sync resolves question 2 index when question 1 is answered on web`() {
        val questions = listOf(
            Question(id = "q1", category = "Compose", difficulty = "Junior", correct_answer = "A"),
            Question(id = "q2", category = "Coroutines", difficulty = "Mid", correct_answer = "B"),
            Question(id = "q3", category = "Architecture", difficulty = "Senior", correct_answer = "C"),
            Question(id = "q4", category = "Internals", difficulty = "Expert", correct_answer = "D"),
            Question(id = "q5", category = "Compose", difficulty = "Junior", correct_answer = "A")
        )

        // Web app answered Question 1 (index 0). It sends:
        val webSub = DailySubmissionRow(
            user_id = "user-web-sync",
            date = "2026-10-29",
            score = 1,
            earned_xp = 10,
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, xpEarned = 10)
            ),
            selected_options = listOf(0, -1, -1, -1, -1)
        )

        val reconciled = reconcileDailySubmission(webSub, questions)

        assertFalse(reconciled.isComplete, "Quiz must NOT be marked complete after answering only 1 question")
        assertEquals(1, reconciled.answeredCount, "Answered count must be exactly 1")
        // Index 1 corresponds to the 2nd question (0-indexed: 0 is Q1, 1 is Q2)
        assertEquals(1, reconciled.firstUnansweredIndex, "Plugin must resume at index 1 (Question 2), exactly matching web")
        assertEquals(listOf(0, -1, -1, -1, -1), reconciled.selectedOptions)
    }

    @Test
    fun `test stale or orphan answers do not bump resume index past question 2`() {
        val questions = listOf(
            Question(id = "q1", category = "Compose", difficulty = "Junior", correct_answer = "A"),
            Question(id = "q2", category = "Coroutines", difficulty = "Mid", correct_answer = "B"),
            Question(id = "q3", category = "Architecture", difficulty = "Senior", correct_answer = "C"),
            Question(id = "q4", category = "Internals", difficulty = "Expert", correct_answer = "D"),
            Question(id = "q5", category = "Compose", difficulty = "Junior", correct_answer = "A")
        )

        // Remote answers map has 3 entries: q1 (valid for today) plus 2 orphan/historical keys (e.g. from prior pool)
        val subWithOrphans = DailySubmissionRow(
            user_id = "user-orphan-keys",
            date = "2026-10-29",
            score = 1,
            earned_xp = 10,
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true, xpEarned = 10),
                "orphan_old_q99" to UserAnswerPayload(questionId = "orphan_old_q99", selectedOption = "B", isCorrect = true),
                "orphan_old_q100" to UserAnswerPayload(questionId = "orphan_old_q100", selectedOption = "C", isCorrect = false)
            ),
            selected_options = listOf(0, -1, -1, -1, -1)
        )

        val reconciled = reconcileDailySubmission(subWithOrphans, questions)

        assertFalse(reconciled.isComplete)
        assertEquals(1, reconciled.answeredCount, "Only the 1 question matching the active pool counts")
        // Crucial bug reproduction: The plugin must NOT resume at index 3 (Question 4); it must resume at index 1 (Question 2)
        assertEquals(1, reconciled.firstUnansweredIndex, "Plugin must resume at index 1 (Question 2), NOT index 3 (Question 4)")
    }

    @Test
    fun `test firstUnansweredIndex finds first gap when questions are answered non-sequentially`() {
        val questions = listOf(
            Question(id = "q1", correct_answer = "A"),
            Question(id = "q2", correct_answer = "B"),
            Question(id = "q3", correct_answer = "C"),
            Question(id = "q4", correct_answer = "D"),
            Question(id = "q5", correct_answer = "A")
        )

        // q1 (index 0) and q3 (index 2) are answered, but q2 (index 1) is not
        val subWithGap = DailySubmissionRow(
            user_id = "user-gap",
            date = "2026-10-29",
            answers = mapOf(
                "q1" to UserAnswerPayload(questionId = "q1", selectedOption = "A", isCorrect = true),
                "q3" to UserAnswerPayload(questionId = "q3", selectedOption = "C", isCorrect = true)
            )
        )

        val reconciled = reconcileDailySubmission(subWithGap, questions)

        assertFalse(reconciled.isComplete)
        // Must point to index 1 (Question 2), mirroring web app's questions.findIndex(q => !answers[q.id]?.selectedOption)
        assertEquals(1, reconciled.firstUnansweredIndex, "Resume index must point to first gap at index 1 (Question 2)")
    }

    @Test
    fun `test DocLinkSerializer successfully parses both string URLs and object formats without exception`() {
        val jsonPayloadWithStringLinks = """
            {
                "questionId": "q1",
                "selectedOption": "A",
                "docLinks": [
                    "https://developer.android.com/reference/kotlin/androidx/compose/runtime/State",
                    "https://kotlinlang.org/docs/coroutines-overview.html"
                ]
            }
        """.trimIndent()

        val parsedStrings = json.decodeFromString<UserAnswerPayload>(jsonPayloadWithStringLinks)
        assertEquals(2, parsedStrings.docLinks.size)
        assertEquals("https://developer.android.com/reference/kotlin/androidx/compose/runtime/State", parsedStrings.docLinks[0].url)
        assertEquals("https://kotlinlang.org/docs/coroutines-overview.html", parsedStrings.docLinks[1].url)

        val jsonPayloadWithObjectLinks = """
            {
                "questionId": "q1",
                "selectedOption": "A",
                "docLinks": [
                    {"title": "Compose State", "url": "https://developer.android.com/compose"},
                    {"title": "Coroutines", "url": "https://kotlinlang.org/coroutines"}
                ]
            }
        """.trimIndent()

        val parsedObjects = json.decodeFromString<UserAnswerPayload>(jsonPayloadWithObjectLinks)
        assertEquals(2, parsedObjects.docLinks.size)
        assertEquals("Compose State", parsedObjects.docLinks[0].title)
        assertEquals("https://developer.android.com/compose", parsedObjects.docLinks[0].url)
        assertEquals("Coroutines", parsedObjects.docLinks[1].title)
        assertEquals("https://kotlinlang.org/coroutines", parsedObjects.docLinks[1].url)
    }

    @Test
    fun `test UserAnswerPayloadSerializer successfully decodes raw string option values`() {
        val jsonPayloadWithStringAnswer = """
            {
                "id": "sub-1",
                "user_id": "u-1",
                "date": "2026-10-29",
                "answers": {
                    "q1": "A",
                    "q2": "B"
                }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<DailySubmissionRow>(jsonPayloadWithStringAnswer)
        assertEquals(2, parsed.answers.size)
        assertEquals("A", parsed.answers["q1"]?.resolvedSelectedOption)
        assertEquals("B", parsed.answers["q2"]?.resolvedSelectedOption)
    }

    @Test
    fun `test UserAnswerPayloadSerializer successfully decodes snake_case field names`() {
        val jsonPayloadWithSnakeCase = """
            {
                "id": "sub-2",
                "user_id": "u-2",
                "date": "2026-10-29",
                "answers": {
                    "q1": {
                        "question_id": "q1",
                        "selected_option": "C",
                        "is_correct": true,
                        "correct_answer": "C",
                        "xp_earned": 50
                    }
                }
            }
        """.trimIndent()

        val parsed = json.decodeFromString<DailySubmissionRow>(jsonPayloadWithSnakeCase)
        val ans = parsed.answers["q1"]
        assertNotNull(ans)
        assertEquals("C", ans?.resolvedSelectedOption)
        assertEquals("C", ans?.resolvedCorrectAnswer)
        assertEquals(true, ans?.resolvedIsCorrect)
        assertEquals(50, ans?.resolvedXpEarned)
    }

    @Test
    fun `test reconcileDailySubmission falls back to rawOptions when answers map is empty`() {
        val questions = listOf(
            Question(id = "q1", correct_answer = "A"),
            Question(id = "q2", correct_answer = "B"),
            Question(id = "q3", correct_answer = "C"),
            Question(id = "q4", correct_answer = "D"),
            Question(id = "q5", correct_answer = "A")
        )

        // answers map is empty, selected_options has Q1 answered (0 = Option A)
        val sub = DailySubmissionRow(
            user_id = "user-combo",
            date = "2026-10-29",
            answers = emptyMap(),
            selected_options = listOf(0, -1, -1, -1, -1)
        )

        val reconciled = reconcileDailySubmission(sub, questions)

        assertFalse(reconciled.isComplete)
        assertEquals(1, reconciled.answeredCount, "Q1 should be counted as answered via selected_options")
        assertEquals(listOf(0, -1, -1, -1, -1), reconciled.selectedOptions)
        // Resume index must point to Question 2 (index 1)
        assertEquals(1, reconciled.firstUnansweredIndex, "Resume index must point to Question 2 (index 1)")
    }
}



