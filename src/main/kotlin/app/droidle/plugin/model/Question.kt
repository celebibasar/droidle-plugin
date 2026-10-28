package app.droidle.plugin.model

import app.droidle.plugin.state.AppLanguage
import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: String,
    val question: String,
    val question_tr: String? = null,
    val question_en: String? = null,
    val options: Map<String, String> = emptyMap(),    // { "A": "...", "B": "...", ... }
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
    /** Kullanıcıya gösterilecek soru metni — dile göre */
    fun displayQuestion(lang: AppLanguage = AppLanguage.TR): String = when (lang) {
        AppLanguage.EN -> question_en ?: question_tr ?: question
        AppLanguage.TR -> question_tr ?: question
    }

    /** Şık metni — dile göre seçenek haritası */
    fun displayOptions(lang: AppLanguage = AppLanguage.TR): Map<String, String> = when (lang) {
        AppLanguage.EN -> options_en ?: options_tr ?: options
        AppLanguage.TR -> options_tr ?: options
    }

    /** Explanation metni — dile göre */
    fun displayExplanation(lang: AppLanguage = AppLanguage.TR): String? = when (lang) {
        AppLanguage.EN -> explanation_en ?: explanation_tr ?: explanation
        AppLanguage.TR -> explanation_tr ?: explanation
    }

    /** Doğru şıkkın metnini döner */
    fun correctOptionText(lang: AppLanguage = AppLanguage.TR): String? {
        val key = correct_answer ?: return null
        return displayOptions(lang)[key]
    }
}

@Serializable
data class DailyQuizResponse(
    val date: String = "",
    val dayNumber: Int = 0,
    val questions: List<Question> = emptyList(),
    val antiCheatProtected: Boolean = false
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
