package app.droidle.plugin.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable
data class SupabaseProfile(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    val avatar_url: String? = null,
    val role: String? = "user",
    val current_streak: Int = 0,
    val max_streak: Int = 0,
    val total_xp: Int = 0,
    val last_completed_date: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

object UserAnswerPayloadSerializer : KSerializer<UserAnswerPayload> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("app.droidle.plugin.model.UserAnswerPayload")

    override fun deserialize(decoder: Decoder): UserAnswerPayload {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            return when (element) {
                is JsonPrimitive -> {
                    // String-only option payload (e.g. "A", "B", "C", "D")
                    UserAnswerPayload(selectedOption = element.content)
                }
                is JsonObject -> {
                    val questionId = (element["questionId"] as? JsonPrimitive)?.content
                        ?: (element["question_id"] as? JsonPrimitive)?.content
                    val selectedOption = (element["selectedOption"] as? JsonPrimitive)?.content
                        ?: (element["selected_option"] as? JsonPrimitive)?.content
                        ?: (element["selectedOptionKey"] as? JsonPrimitive)?.content
                    val isCorrect = (element["isCorrect"] as? JsonPrimitive)?.booleanOrNull
                        ?: (element["is_correct"] as? JsonPrimitive)?.booleanOrNull
                    val correctAnswer = (element["correctAnswer"] as? JsonPrimitive)?.content
                        ?: (element["correct_answer"] as? JsonPrimitive)?.content
                    val answeredAt = (element["answeredAt"] as? JsonPrimitive)?.content
                        ?: (element["answered_at"] as? JsonPrimitive)?.content
                    val category = (element["category"] as? JsonPrimitive)?.content
                    val difficulty = (element["difficulty"] as? JsonPrimitive)?.content
                    val xpEarned = (element["xpEarned"] as? JsonPrimitive)?.intOrNull
                        ?: (element["xp_earned"] as? JsonPrimitive)?.intOrNull
                    val explanation = (element["explanation"] as? JsonPrimitive)?.content
                    val explanationTr = (element["explanation_tr"] as? JsonPrimitive)?.content
                    val explanationEn = (element["explanation_en"] as? JsonPrimitive)?.content

                    val docLinksElement = element["docLinks"] ?: element["doc_links"]
                    val docLinks = if (docLinksElement is JsonArray) {
                        docLinksElement.mapNotNull { docElem ->
                            when (docElem) {
                                is JsonPrimitive -> DocLink(title = "Documentation", url = docElem.content)
                                is JsonObject -> {
                                    val title = (docElem["title"] as? JsonPrimitive)?.content ?: "Documentation"
                                    val url = (docElem["url"] as? JsonPrimitive)?.content ?: ""
                                    if (url.isNotBlank()) DocLink(title = title, url = url) else null
                                }
                                else -> null
                            }
                        }
                    } else emptyList()

                    UserAnswerPayload(
                        questionId = questionId,
                        selectedOption = selectedOption,
                        isCorrect = isCorrect,
                        correctAnswer = correctAnswer,
                        answeredAt = answeredAt,
                        category = category,
                        difficulty = difficulty,
                        xpEarned = xpEarned,
                        explanation = explanation,
                        explanation_tr = explanationTr,
                        explanation_en = explanationEn,
                        docLinks = docLinks
                    )
                }
                else -> UserAnswerPayload()
            }
        }
        return UserAnswerPayload()
    }

    override fun serialize(encoder: Encoder, value: UserAnswerPayload) {
        val jsonEncoder = encoder as? JsonEncoder
        if (jsonEncoder != null) {
            val jsonObject = buildJsonObject {
                value.questionId?.let { put("questionId", it) }
                value.resolvedSelectedOption?.let { put("selectedOption", it) }
                value.resolvedIsCorrect?.let { put("isCorrect", it) }
                value.resolvedCorrectAnswer?.let { put("correctAnswer", it) }
                value.resolvedAnsweredAt?.let { put("answeredAt", it) }
                value.category?.let { put("category", it) }
                value.difficulty?.let { put("difficulty", it) }
                value.resolvedXpEarned?.let { put("xpEarned", it) }
                value.explanation?.let { put("explanation", it) }
                value.explanation_tr?.let { put("explanation_tr", it) }
                value.explanation_en?.let { put("explanation_en", it) }
                if (value.resolvedDocLinks.isNotEmpty()) {
                    put("docLinks", JsonArray(value.resolvedDocLinks.map { doc ->
                        buildJsonObject {
                            put("title", doc.title)
                            put("url", doc.url)
                        }
                    }))
                }
            }
            jsonEncoder.encodeJsonElement(jsonObject)
        }
    }
}

@Serializable(with = UserAnswerPayloadSerializer::class)
data class UserAnswerPayload(
    val questionId: String? = null,
    val selectedOption: String? = null,
    val selected_option: String? = null,
    val isCorrect: Boolean? = null,
    val is_correct: Boolean? = null,
    val correctAnswer: String? = null,
    val correct_answer: String? = null,
    val answeredAt: String? = null,
    val answered_at: String? = null,
    val category: String? = null,
    val difficulty: String? = null,
    val xpEarned: Int? = null,
    val xp_earned: Int? = null,
    val explanation: String? = null,
    val explanation_tr: String? = null,
    val explanation_en: String? = null,
    val docLinks: List<DocLink> = emptyList(),
    val doc_links: List<DocLink> = emptyList()
) {
    val resolvedSelectedOption: String?
        get() = selectedOption ?: selected_option

    val resolvedCorrectAnswer: String?
        get() = correctAnswer ?: correct_answer

    val resolvedIsCorrect: Boolean?
        get() = isCorrect ?: is_correct

    val resolvedAnsweredAt: String?
        get() = answeredAt ?: answered_at

    val resolvedXpEarned: Int?
        get() = xpEarned ?: xp_earned

    val resolvedDocLinks: List<DocLink>
        get() = docLinks.ifEmpty { doc_links }
}

@Serializable
data class DailySubmissionRow(
    val id: String? = null,
    val user_id: String,
    val date: String,
    val score: Int = 0,
    val earned_xp: Int = 0,
    val answers: Map<String, UserAnswerPayload> = emptyMap(),
    val selected_options: List<Int> = emptyList(),
    val created_at: String? = null
) {
    /** True only if 5 questions (or totalQuestions) are answered */
    fun isComplete(totalQuestions: Int = 5): Boolean {
        if (answers.isNotEmpty()) {
            return answers.size >= totalQuestions && answers.values.all {
                val opt = it.resolvedSelectedOption
                opt != null && OPTION_KEYS.contains(opt.uppercase())
            }
        }
        if (selected_options.isNotEmpty()) {
            return selected_options.size >= totalQuestions && selected_options.all { it in 0..3 }
        }
        return false
    }
}

@Serializable
data class DailySubmissionPayload(
    val user_id: String,
    val date: String,
    val score: Int,
    val earned_xp: Int,
    val answers: Map<String, UserAnswerPayload>,
    val selected_options: List<Int>
)

@Serializable
data class ProfileUpdatePayload(
    val current_streak: Int,
    val max_streak: Int,
    val total_xp: Int,
    val last_completed_date: String
)

/** Reconciled daily state between Supabase submission row and question pool */
data class ReconciledDailyState(
    val score: Int,
    val earnedXp: Int,
    val selectedOptions: List<Int>,
    val answers: Map<String, UserAnswerPayload>,
    val isComplete: Boolean,
    val firstUnansweredIndex: Int,
    val answeredCount: Int = 0
)

val OPTION_KEYS = listOf("A", "B", "C", "D")

/**
 * Robust bidirectional reconciliation mirroring web app's reconcileDailyAnswersAndOptions.
 * Reconciles selected_options and answers map whether one or both are present.
 */
fun reconcileDailySubmission(
    sub: DailySubmissionRow?,
    questions: List<Question>
): ReconciledDailyState {
    if (questions.isEmpty()) {
        return ReconciledDailyState(
            score = 0,
            earnedXp = 0,
            selectedOptions = emptyList(),
            answers = emptyMap(),
            isComplete = false,
            firstUnansweredIndex = 0,
            answeredCount = 0
        )
    }

    val total = questions.size
    val reconciledOptions = MutableList(total) { -1 }
    val reconciledAnswers = mutableMapOf<String, UserAnswerPayload>()
    var calculatedScore = 0
    var calculatedXp = 0

    val rawOptions = sub?.selected_options ?: emptyList()
    val rawAnswers = sub?.answers ?: emptyMap()
    val hasRawAnswers = rawAnswers.isNotEmpty()

    questions.forEachIndexed { i, q ->
        // 1. Try finding answer in rawAnswers by questionId or index string
        val ansObj = rawAnswers[q.id] ?: rawAnswers[i.toString()]
        var chosenKey: String? = ansObj?.resolvedSelectedOption

        // 2. Try finding answer in rawOptions only if rawAnswers is empty
        if (!hasRawAnswers && chosenKey.isNullOrBlank()) {
            val optIdx = rawOptions.getOrNull(i)
            if (optIdx != null && optIdx in 0..3) {
                chosenKey = OPTION_KEYS[optIdx]
            }
        }

        if (!chosenKey.isNullOrBlank()) {
            val optIdx = OPTION_KEYS.indexOf(chosenKey.uppercase())
            if (optIdx in 0..3) {
                reconciledOptions[i] = optIdx

                val correctKey = q.resolvedCorrectAnswer ?: ansObj?.resolvedCorrectAnswer ?: "A"
                val isCorrect = ansObj?.resolvedIsCorrect ?: chosenKey.equals(correctKey, ignoreCase = true)
                val xp = if (isCorrect) (ansObj?.resolvedXpEarned ?: q.xpValue) else 0

                if (isCorrect) {
                    calculatedScore++
                    calculatedXp += xp
                }

                val normalizedAns = (ansObj ?: UserAnswerPayload()).copy(
                    questionId = ansObj?.questionId ?: q.id,
                    selectedOption = chosenKey,
                    isCorrect = isCorrect,
                    correctAnswer = correctKey,
                    category = ansObj?.category ?: q.category,
                    difficulty = ansObj?.difficulty ?: q.difficulty,
                    xpEarned = xp,
                    explanation = if (ansObj?.explanation.isNullOrBlank()) q.explanation else ansObj!!.explanation,
                    explanation_tr = if (ansObj?.explanation_tr.isNullOrBlank()) q.explanation_tr else ansObj!!.explanation_tr,
                    explanation_en = if (ansObj?.explanation_en.isNullOrBlank()) q.explanation_en else ansObj!!.explanation_en,
                    docLinks = if (ansObj?.resolvedDocLinks.isNullOrEmpty()) q.doc_links else ansObj!!.resolvedDocLinks
                )
                reconciledAnswers[q.id] = normalizedAns
            }
        }
    }

    // Answered count is strictly the number of scheduled questions that have a valid answer
    val answeredCount = reconciledOptions.count { it in 0..3 }

    // Complete if all scheduled questions (and at least 5) are answered with no -1
    val isComplete = (sub?.isComplete(total) == true || answeredCount >= total) && answeredCount >= 5 && !reconciledOptions.contains(-1)

    // First unanswered question index, exactly mirroring web app's questions.findIndex(q => !answers[q.id]?.selectedOption)
    val firstUnanswered = if (isComplete) {
        total - 1
    } else {
        val firstHole = reconciledOptions.indexOfFirst { it !in 0..3 }
        if (firstHole != -1) firstHole else answeredCount.coerceIn(0, total - 1)
    }

    return ReconciledDailyState(
        score = sub?.score?.takeIf { isComplete } ?: calculatedScore,
        earnedXp = sub?.earned_xp?.takeIf { isComplete && it > 0 } ?: calculatedXp,
        selectedOptions = reconciledOptions.toList(),
        answers = reconciledAnswers.toMap(),
        isComplete = isComplete,
        firstUnansweredIndex = firstUnanswered.coerceIn(0, total - 1),
        answeredCount = answeredCount
    )
}
