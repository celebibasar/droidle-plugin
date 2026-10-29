package app.droidle.plugin.service

import app.droidle.plugin.model.DailyQuizResponse
import app.droidle.plugin.model.Question
import app.droidle.plugin.model.QuizFetchResult
import app.droidle.plugin.model.VerifyRequest
import app.droidle.plugin.model.VerifyResponse
import app.droidle.plugin.settings.DroidleSettingsState
import app.droidle.plugin.state.StateManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Service(Service.Level.APP)
class DroidleService {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val baseUrl: String
        get() = service<StateManager>().settings.apiBaseUrl

    private fun Request.Builder.applyCommonHeaders(): Request.Builder {
        header("Accept", "application/json")
        header("Content-Type", "application/json")
        // Vercel Deployment Protection bypass (geliştirme ortamı için)
        val bypass = DroidleSettingsState.getInstance().vercelBypassSecret
        if (bypass.isNotBlank()) header("x-vercel-protection-bypass", bypass)
        return this
    }

    @Throws(IOException::class)
    fun fetchDailyQuizResult(): QuizFetchResult {
        val state = service<StateManager>()
        val token = state.getAuthToken()
        val today = state.todayIstanbulString()
        val url = "$baseUrl/api/daily-quiz?date=$today"
        val requestBuilder = Request.Builder()
            .url(url)
            .applyCommonHeaders()
            .get()
        if (token != null) requestBuilder.header("Authorization", "Bearer $token")

        val response = client.newCall(requestBuilder.build()).execute()
        val body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${body.take(300)}")
        }

        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw IOException("Sunucu boş yanıt döndürdü (HTTP ${response.code}). URL: $url")
        }
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw IOException("HTML yanıtı alındı — Vercel Protection aktif olabilir. İlk 200 karakter: ${trimmed.take(200)}")
        }

        return if (trimmed.startsWith("[")) {
            val questions = json.decodeFromString(ListSerializer(Question.serializer()), trimmed)
            QuizFetchResult(questions = questions, completedToday = false, correctCount = null)
        } else {
            val wrapper = json.decodeFromString(DailyQuizResponse.serializer(), trimmed)
            if (!wrapper.completedToday && wrapper.questions.isEmpty()) {
                throw IOException("Bugün için soru bulunamadı.")
            }
            QuizFetchResult(
                questions      = wrapper.questions,
                completedToday = wrapper.completedToday,
                correctCount   = wrapper.correctCount
            )
        }
    }

    /** Backwards-compat helper used by tests / older callers */
    @Throws(IOException::class)
    fun fetchDailyQuiz(): List<Question> = fetchDailyQuizResult().questions

    @Throws(IOException::class)
    fun verifyAnswer(questionId: String, selectedOption: String): VerifyResponse {
        val token = service<StateManager>().getAuthToken()
        val payload = json.encodeToString(VerifyRequest.serializer(), VerifyRequest(questionId, selectedOption))
        val requestBody = payload.toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/verify-answer")
            .applyCommonHeaders()
            .post(requestBody)
        if (token != null) requestBuilder.header("Authorization", "Bearer $token")

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) throw IOException("API error ${response.code}: ${responseBody.take(300)}")
        if (responseBody.trim().isEmpty()) throw IOException("Sunucu boş yanıt döndürdü (verify)")

        return json.decodeFromString(VerifyResponse.serializer(), responseBody)
    }

    companion object {
        fun getInstance(): DroidleService = service()
    }
}
