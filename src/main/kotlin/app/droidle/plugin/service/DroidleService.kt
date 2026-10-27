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
        .build()

    private val baseUrl: String
        get() = service<StateManager>().settings.apiBaseUrl

    @Throws(IOException::class)
    fun fetchDailyQuiz(): List<Question> {
        val token = service<StateManager>().getAuthToken()
        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/daily-quiz")
            .get()
        if (token != null) requestBuilder.header("Authorization", "Bearer $token")

        val response = client.newCall(requestBuilder.build()).execute()
        val body = response.body?.string() ?: throw IOException("Empty response body")
        if (!response.isSuccessful) throw IOException("API error ${response.code}: $body")

        // API { date, dayNumber, questions: [...], antiCheatProtected } wrapper döndürür
        val wrapper = json.decodeFromString(DailyQuizResponse.serializer(), body)
        if (wrapper.questions.isEmpty()) throw IOException("Bugün için soru bulunamadı.")
        return wrapper.questions
    }

    @Throws(IOException::class)
    fun verifyAnswer(questionId: String, selectedOption: String): VerifyResponse {
        val token = service<StateManager>().getAuthToken()
        val payload = json.encodeToString(VerifyRequest.serializer(), VerifyRequest(questionId, selectedOption))
        val body = payload.toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url("$baseUrl/api/verify-answer")
            .post(body)
        if (token != null) requestBuilder.header("Authorization", "Bearer $token")

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string() ?: throw IOException("Empty response body")
        if (!response.isSuccessful) throw IOException("API error ${response.code}: $responseBody")

        return json.decodeFromString(VerifyResponse.serializer(), responseBody)
    }

    companion object {
        fun getInstance(): DroidleService = service()
    }
}
