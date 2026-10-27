package app.droidle.plugin.service

import app.droidle.plugin.model.DailyQuizResponse
import app.droidle.plugin.model.Question
import app.droidle.plugin.model.VerifyRequest
import app.droidle.plugin.model.VerifyResponse
import app.droidle.plugin.state.StateManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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

    @Throws(IOException::class)
    fun fetchDailyQuiz(): List<Question> {
        val token = service<StateManager>().getAuthToken()
        val url = "$baseUrl/api/daily-quiz"
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .get()
        if (token != null) requestBuilder.header("Authorization", "Bearer $token")

        val response = client.newCall(requestBuilder.build()).execute()
        val body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code} — ${body.take(200)}")
        }

        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw IOException("Sunucu boş yanıt döndürdü (HTTP ${response.code}). URL: $url")
        }
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw IOException("Beklenmeyen yanıt formatı (ilk 100 karakter): ${trimmed.take(100)}")
        }

        return if (trimmed.startsWith("[")) {
            // Eski format: bare array
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Question.serializer()), trimmed)
        } else {
            // Yeni format: { questions: [...], ... } wrapper
            val wrapper = json.decodeFromString(DailyQuizResponse.serializer(), trimmed)
            if (wrapper.questions.isEmpty()) throw IOException("Bugün için soru bulunamadı.")
            wrapper.questions
        }
    }

    @Throws(IOException::class)
    fun verifyAnswer(questionId: String, selectedOption: String): VerifyResponse {
        val token = service<StateManager>().getAuthToken()
        val payload = json.encodeToString(VerifyRequest.serializer(), VerifyRequest(questionId, selectedOption))
        val body = payload.toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/verify-answer")
            .header("Accept", "application/json")
            .post(body)
        if (token != null) requestBuilder.header("Authorization", "Bearer $token")

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string() ?: ""
        if (!response.isSuccessful) throw IOException("API error ${response.code}: ${responseBody.take(200)}")
        if (responseBody.trim().isEmpty()) throw IOException("Sunucu boş yanıt döndürdü (verify)")

        return json.decodeFromString(VerifyResponse.serializer(), responseBody)
    }

    companion object {
        fun getInstance(): DroidleService = service()
    }
}
