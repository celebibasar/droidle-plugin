package app.droidle.plugin.model

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
    /** Kullanıcıya gösterilecek soru metni (TR öncelikli) */
    fun displayQuestion(): String = question_tr ?: question

    /** Şık metni — verilen key için TR öncelikli seçenek haritasından döner */
    fun displayOptions(): Map<String, String> = options_tr ?: options

    /** Doğru şıkkın metnini döner */
    fun correctOptionText(): String? {
        val key = correct_answer ?: return null
        return displayOptions()[key]
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
