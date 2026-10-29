package app.droidle.plugin.service

import app.droidle.plugin.model.*
import app.droidle.plugin.settings.DroidleSettingsState
import app.droidle.plugin.state.StateManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
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
class SupabaseService {

    private val LOG = Logger.getInstance(SupabaseService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val supabaseUrl: String
        get() = DroidleSettingsState.getInstance().supabaseUrl.trimEnd('/')

    private val anonKey: String
        get() = DroidleSettingsState.getInstance().supabaseAnonKey.trim()

    private fun Request.Builder.applySupabaseHeaders(token: String? = null): Request.Builder {
        val effectiveToken = token ?: anonKey
        header("apikey", anonKey)
        header("Authorization", "Bearer $effectiveToken")
        header("Accept", "application/json")
        return this
    }

    private fun fetchWithAnonFallback(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .applySupabaseHeaders(null) // uses anonKey
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LOG.warn("fetchWithAnonFallback HTTP ${response.code} for $url")
                    null
                } else {
                    response.body?.string()?.trim()
                }
            }
        } catch (e: Exception) {
            LOG.warn("fetchWithAnonFallback error: ${e.message}")
            null
        }
    }

    /**
     * Resolves the user ID from Supabase /auth/v1/user using the Bearer token.
     */
    fun fetchUserIdFromAuth(token: String): String? {
        if (token.isBlank()) return null
        val url = "$supabaseUrl/auth/v1/user"
        val request = Request.Builder()
            .url(url)
            .applySupabaseHeaders(token)
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim() ?: return null
                    val jsonElem = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
                    (jsonElem?.get("id") as? kotlinx.serialization.json.JsonPrimitive)?.content
                } else null
            }
        } catch (e: Exception) {
            LOG.warn("fetchUserIdFromAuth error: ${e.message}")
            null
        }
    }

    /**
     * Fetches today's submission row for the user from Supabase.
     * Returns null if unauthenticated, not found, or on database error.
     */
    fun fetchTodaySubmission(userId: String, date: String): DailySubmissionRow? {
        if (userId.isBlank() || date.isBlank()) return null
        val token = StateManager.getInstance().getAuthToken()
        val url = "$supabaseUrl/rest/v1/daily_submissions?user_id=eq.$userId&date=eq.$date&select=*"

        val request = Request.Builder()
            .url(url)
            .applySupabaseHeaders(token)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                var body: String? = null
                if (response.code == 401 && token != null) {
                    // Token expired; fallback to anonKey since daily_submissions is publicly readable
                    body = fetchWithAnonFallback(url)
                } else if (response.isSuccessful) {
                    body = response.body?.string()?.trim()
                } else {
                    LOG.warn("fetchTodaySubmission HTTP ${response.code}")
                    body = fetchWithAnonFallback(url)
                }

                if (body != null && body.startsWith("[")) {
                    val list = json.decodeFromString(ListSerializer(DailySubmissionRow.serializer()), body)
                    list.firstOrNull()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            LOG.warn("fetchTodaySubmission error: ${e.message}")
            try {
                val fallbackBody = fetchWithAnonFallback(url)
                if (fallbackBody != null && fallbackBody.startsWith("[")) {
                    val list = json.decodeFromString(ListSerializer(DailySubmissionRow.serializer()), fallbackBody)
                    list.firstOrNull()
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * UPSERT a daily submission row to Supabase.
     * Uses on_conflict=user_id,date and resolution=merge-duplicates.
     */
    fun upsertDailySubmission(payload: DailySubmissionPayload): Boolean {
        val token = StateManager.getInstance().getAuthToken() ?: return false
        val url = "$supabaseUrl/rest/v1/daily_submissions?on_conflict=user_id,date"
        val jsonPayload = json.encodeToString(DailySubmissionPayload.serializer(), payload)
        val body = jsonPayload.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .applySupabaseHeaders(token)
            .header("Prefer", "resolution=merge-duplicates")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    true
                } else {
                    val respBody = response.body?.string() ?: ""
                    LOG.warn("upsertDailySubmission failed HTTP ${response.code}: $respBody")
                    false
                }
            }
        } catch (e: Exception) {
            LOG.warn("upsertDailySubmission network error: ${e.message}")
            false
        }
    }

    /**
     * Fetches user profile from Supabase profiles table.
     */
    fun fetchProfile(userId: String): SupabaseProfile? {
        if (userId.isBlank()) return null
        val token = StateManager.getInstance().getAuthToken()
        val url = "$supabaseUrl/rest/v1/profiles?id=eq.$userId&select=*"

        val request = Request.Builder()
            .url(url)
            .applySupabaseHeaders(token)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                var body: String? = null
                if (response.code == 401 && token != null) {
                    body = fetchWithAnonFallback(url)
                } else if (response.isSuccessful) {
                    body = response.body?.string()?.trim()
                } else {
                    body = fetchWithAnonFallback(url)
                }

                if (body != null && body.startsWith("[")) {
                    val list = json.decodeFromString(ListSerializer(SupabaseProfile.serializer()), body)
                    list.firstOrNull()
                } else null
            }
        } catch (e: Exception) {
            LOG.warn("fetchProfile error: ${e.message}")
            try {
                val fallbackBody = fetchWithAnonFallback(url)
                if (fallbackBody != null && fallbackBody.startsWith("[")) {
                    val list = json.decodeFromString(ListSerializer(SupabaseProfile.serializer()), fallbackBody)
                    list.firstOrNull()
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Updates user's streak, XP, and last completed date in profiles table.
     */
    fun updateProfile(
        userId: String,
        currentStreak: Int,
        maxStreak: Int,
        totalXp: Int,
        lastCompletedDate: String
    ): Boolean {
        val token = StateManager.getInstance().getAuthToken() ?: return false
        val url = "$supabaseUrl/rest/v1/profiles?id=eq.$userId"
        val payload = ProfileUpdatePayload(
            current_streak = currentStreak,
            max_streak = maxStreak,
            total_xp = totalXp,
            last_completed_date = lastCompletedDate
        )
        val jsonPayload = json.encodeToString(ProfileUpdatePayload.serializer(), payload)
        val body = jsonPayload.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .applySupabaseHeaders(token)
            .header("Content-Type", "application/json")
            .patch(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    true
                } else {
                    val respBody = response.body?.string() ?: ""
                    LOG.warn("updateProfile failed HTTP ${response.code}: $respBody")
                    false
                }
            }
        } catch (e: Exception) {
            LOG.warn("updateProfile network error: ${e.message}")
            false
        }
    }

    /**
     * Fetches today's questions directly from Supabase questions table.
     */
    fun fetchQuestionsFromSupabase(date: String): List<Question>? {
        if (date.isBlank()) return null
        val url = "$supabaseUrl/rest/v1/questions?scheduled_date=eq.$date&status=eq.published&order=id.asc&limit=5"

        val request = Request.Builder()
            .url(url)
            .applySupabaseHeaders()
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string()?.trim() ?: return null
                if (body.startsWith("[")) {
                    val list = json.decodeFromString(ListSerializer(Question.serializer()), body)
                    if (list.isNotEmpty()) list else null
                } else null
            }
        } catch (e: Exception) {
            LOG.warn("fetchQuestionsFromSupabase error: ${e.message}")
            null
        }
    }

    companion object {
        fun getInstance(): SupabaseService = service()
    }
}
