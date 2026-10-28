package app.droidle.plugin.model

import app.droidle.plugin.state.AppLanguage
import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: String,
    val question: String,
    val question_tr: String? = null,
    val question_en: String? = null,
    val options: Map<String, String> = emptyMap(),
    val options_tr: Map<String, String>? = null,
    val options_en: Map<String, String>? = null,
    val correct_answer: String? = null,
    val explanation: String? = null,
    val explanation_tr: String? = null,
    val explanation_en: String? = null,
    val category: String,
    val difficulty: String,
    val doc_links: List<DocLink> = emptyList(),
    val code_snippet: String? = null,
    val code_language: String? = null,
    val tags: List<String> = emptyList()
) {
    fun displayQuestion(lang: AppLanguage = AppLanguage.TR): String = when (lang) {
        AppLanguage.EN -> question_en ?: question_tr ?: question
        AppLanguage.TR -> question_tr ?: question
    }

    fun displayOptions(lang: AppLanguage = AppLanguage.TR): Map<String, String> = when (lang) {
        AppLanguage.EN -> options_en ?: options_tr ?: options
        AppLanguage.TR -> options_tr ?: options
    }

    fun displayExplanation(lang: AppLanguage = AppLanguage.TR): String? = when (lang) {
        AppLanguage.EN -> explanation_en ?: explanation_tr ?: explanation
        AppLanguage.TR -> explanation_tr ?: explanation
    }

    fun correctOptionText(lang: AppLanguage = AppLanguage.TR): String? {
        val key = correct_answer ?: return null
        return displayOptions(lang)[key]
    }

    /** XP earned for this question difficulty — mirrors web DIFFICULTY_XP const. */
    val xpValue: Int get() = when (difficulty) {
        "Junior" -> 10
        "Mid"    -> 25
        "Senior" -> 50
        "Expert" -> 75
        else     -> 10
    }
}

@Serializable
data class DailyQuizResponse(
    val date: String = "",
    val dayNumber: Int = 0,
    val questions: List<Question> = emptyList(),
    val antiCheatProtected: Boolean = false,
    /** Server sets true when the authenticated user already completed today. */
    val completedToday: Boolean = false,
    /** Correct answer count from the server-side submission record. */
    val correctCount: Int? = null,
    /** Server-computed current streak for the authenticated user. */
    val currentStreak: Int? = null,
    /** Earned XP for today's session from the server. */
    val earnedXp: Int? = null
)

/**
 * Internal wrapper returned by fetchDailyQuizResult().
 * Merges both list-format (old) and object-format (new) API responses.
 */
data class QuizFetchResult(
    val questions: List<Question>,
    val completedToday: Boolean,
    val correctCount: Int?,
    val currentStreak: Int? = null,
    val earnedXp: Int? = null,
    val date: String = ""
)

@Serializable
data class VerifyRequest(
    val questionId: String,
    val selectedOption: String
)

@Serializable
data class DocLink(
    val title: String,
    val url: String
)

@Serializable
data class VerifyResponse(
    val isCorrect: Boolean,
    val correctAnswer: String,
    val explanation: String,
    val explanation_tr: String? = null,
    val explanation_en: String? = null,
    val xpEarned: Int = 0,
    val difficulty: String = "",
    val category: String = "",
    val docLinks: List<DocLink> = emptyList()
)
