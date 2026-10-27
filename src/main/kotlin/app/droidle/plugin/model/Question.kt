package app.droidle.plugin.model

import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: String,
    val question: String,
    val options: List<String>,
    val category: String,
    val difficulty: String
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
