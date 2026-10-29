package app.droidle.plugin.state

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul")
private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE

data class DroidleSettings(
    val apiBaseUrl: String = "https://droidle.dev"
)

enum class AppLanguage(val code: String, val label: String) {
    TR("tr", "TR"),
    EN("en", "EN");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            values().firstOrNull { it.code == code } ?: TR
    }
}

/**
 * Persistent plugin state — syncs with Europe/Istanbul timezone to match web.
 *
 * Key design decisions mirroring the web app (src/lib/streak.ts):
 *  - Dates are always in Europe/Istanbul (UTC+3), not system local time.
 *  - Streak logic: null → 1; yesterday → streak+1; today → no change; gap → 1.
 *  - XP is tracked per-session and accumulated in totalXp.
 *  - Category/difficulty breakdown is tracked alongside quiz results.
 *  - Server completedToday flag is authoritative — local state defers to it.
 */
@Service(Service.Level.APP)
class StateManager {

    private val props = PropertiesComponent.getInstance()

    // ── Settings ──────────────────────────────────────────────────────────────

    var settings: DroidleSettings
        get() = DroidleSettings(apiBaseUrl = props.getValue(KEY_API_URL, "https://droidle.dev"))
        set(value) { props.setValue(KEY_API_URL, value.apiBaseUrl) }

    // ── Streak (Europe/Istanbul, mirrors web calculateStreak()) ───────────────

    val currentStreak: Int get() = props.getInt(KEY_STREAK, 0)
    val longestStreak: Int get() = props.getInt(KEY_LONGEST_STREAK, 0)

    /**
     * Resolves the active streak count to display in the header.
     * Mirrors web app's getActiveHeaderStreak():
     * If lastCompletedDate is today or yesterday -> currentStreak.
     * If lastCompletedDate < yesterday or null -> 0 (streak broken).
     */
    val activeStreak: Int
        get() {
            val last = lastCompletedDate ?: return 0
            val today = todayIstanbul()
            val yesterday = today.minusDays(1)
            return if (last == today || last == yesterday) currentStreak else 0
        }

    /** Today's date in Europe/Istanbul — always matches web's getIstanbulDateString(). */
    fun todayIstanbul(): LocalDate = LocalDate.now(ISTANBUL_ZONE)
    fun todayIstanbulString(): String = todayIstanbul().format(DATE_FMT)

    val lastCompletedDate: LocalDate?
        get() = props.getValue(KEY_LAST_DATE)?.let {
            runCatching { LocalDate.parse(it, DATE_FMT) }.getOrNull()
        }

    val todayScore: Int
        get() = if (props.getValue(KEY_TODAY_DATE) == todayIstanbulString()) props.getInt(KEY_TODAY_SCORE, 0) else 0

    val todayEarnedXp: Int
        get() = if (props.getValue(KEY_TODAY_DATE) == todayIstanbulString()) props.getInt(KEY_TODAY_XP, 0) else 0

    fun getTodaySelectedOptions(): List<Int> {
        if (props.getValue(KEY_TODAY_DATE) != todayIstanbulString()) return emptyList()
        val raw = props.getValue(KEY_TODAY_OPTIONS) ?: return emptyList()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun getTodayAnswers(): Map<String, app.droidle.plugin.model.UserAnswerPayload> {
        if (props.getValue(KEY_TODAY_DATE) != todayIstanbulString()) return emptyMap()
        val raw = props.getValue(KEY_TODAY_ANSWERS) ?: return emptyMap()
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            json.decodeFromString<Map<String, app.droidle.plugin.model.UserAnswerPayload>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun hasTodayPartialProgress(): Boolean {
        if (props.getValue(KEY_TODAY_DATE) != todayIstanbulString()) return false
        return getTodayAnswers().isNotEmpty() || getTodaySelectedOptions().any { it in 0..3 }
    }

    fun clearTodaySession() {
        props.unsetValue(KEY_TODAY_DATE)
        props.unsetValue(KEY_TODAY_SCORE)
        props.unsetValue(KEY_TODAY_XP)
        props.unsetValue(KEY_TODAY_OPTIONS)
        props.unsetValue(KEY_TODAY_ANSWERS)
    }

    /**
     * Records quiz completion with Istanbul-aware streak logic and today's session persistence.
     * Mirrors web src/lib/streak.ts#calculateStreak() exactly:
     *   null → 1 | yesterday → streak+1 | today → no-op | gap → 1
     */
    fun recordQuizCompletion(
        correctCount: Int,
        totalCount: Int,
        earnedXp: Int = 0,
        selectedOptions: List<Int> = emptyList(),
        answers: Map<String, app.droidle.plugin.model.UserAnswerPayload> = emptyMap(),
        categoryResults: Map<String, CategoryResult> = emptyMap()
    ) {
        val today = todayIstanbul()
        val last  = lastCompletedDate

        // Persist today's specific session results
        saveTodaySession(today, correctCount, earnedXp, selectedOptions, answers)

        // Only update streak and completion date if all questions (5) are completed
        val answeredCount = if (answers.isNotEmpty()) answers.size else selectedOptions.count { it >= 0 }
        if (answeredCount < totalCount) {
            return
        }

        // Prevent double-counting lifetime stats if already recorded today
        if (last == today) {
            return
        }

        val newStreak = when {
            last == null               -> 1
            last == today.minusDays(1) -> currentStreak + 1
            else                       -> 1   // gap of ≥ 1 day → reset
        }

        props.setValue(KEY_LAST_DATE, today.format(DATE_FMT))
        props.setValue(KEY_STREAK, newStreak.toString())
        if (newStreak > longestStreak) props.setValue(KEY_LONGEST_STREAK, newStreak.toString())

        props.setValue(KEY_TOTAL_CORRECT,   (totalCorrect   + correctCount).toString())
        props.setValue(KEY_TOTAL_ATTEMPTED, (totalAttempted + totalCount  ).toString())
        props.setValue(KEY_TOTAL_XP,        (totalXp        + earnedXp    ).toString())
        props.setValue(KEY_TOTAL_PLAYED,    (totalPlayed    + 1            ).toString())

        // Accumulate category breakdown
        categoryResults.forEach { (cat, res) ->
            val attKey = "droidle.cat.${cat}.attempted"
            val corKey = "droidle.cat.${cat}.correct"
            props.setValue(attKey, (props.getInt(attKey, 0) + res.attempted).toString())
            props.setValue(corKey, (props.getInt(corKey, 0) + res.correct  ).toString())
        }
    }

    /**
     * Force-syncs state from server response (used when server says completedToday=true).
     * Does NOT re-increment lifetime counters if already completed today.
     */
    fun syncFromServer(
        correctCount: Int,
        totalCount: Int,
        serverStreak: Int? = null,
        earnedXp: Int = 0,
        selectedOptions: List<Int> = emptyList(),
        answers: Map<String, app.droidle.plugin.model.UserAnswerPayload> = emptyMap()
    ) {
        val today = todayIstanbul()
        val last  = lastCompletedDate

        saveTodaySession(today, correctCount, earnedXp, selectedOptions, answers)

        // Mark as completed today if not already and all questions completed
        val answeredCount = if (answers.isNotEmpty()) answers.size else selectedOptions.count { it >= 0 }
        if (last != today && answeredCount >= totalCount) {
            val newStreak = serverStreak ?: when {
                last == null               -> 1
                last == today.minusDays(1) -> currentStreak + 1
                else                       -> 1
            }
            props.setValue(KEY_LAST_DATE, today.format(DATE_FMT))
            props.setValue(KEY_STREAK, newStreak.toString())
            if (newStreak > longestStreak) props.setValue(KEY_LONGEST_STREAK, newStreak.toString())
            props.setValue(KEY_TOTAL_CORRECT,   (totalCorrect   + correctCount).toString())
            props.setValue(KEY_TOTAL_ATTEMPTED, (totalAttempted + totalCount  ).toString())
            props.setValue(KEY_TOTAL_XP,        (totalXp        + earnedXp    ).toString())
            props.setValue(KEY_TOTAL_PLAYED,    (totalPlayed    + 1            ).toString())
        }
    }

    fun savePartialSession(
        today: LocalDate,
        score: Int,
        earnedXp: Int,
        selectedOptions: List<Int>,
        answers: Map<String, app.droidle.plugin.model.UserAnswerPayload>
    ) {
        saveTodaySession(today, score, earnedXp, selectedOptions, answers)
    }

    private fun saveTodaySession(
        today: LocalDate,
        score: Int,
        earnedXp: Int,
        selectedOptions: List<Int>,
        answers: Map<String, app.droidle.plugin.model.UserAnswerPayload>
    ) {
        props.setValue(KEY_TODAY_DATE, today.format(DATE_FMT))
        props.setValue(KEY_TODAY_SCORE, score.toString())
        props.setValue(KEY_TODAY_XP, earnedXp.toString())
        if (selectedOptions.isNotEmpty()) {
            props.setValue(KEY_TODAY_OPTIONS, selectedOptions.joinToString(","))
        } else {
            props.unsetValue(KEY_TODAY_OPTIONS)
        }
        if (answers.isNotEmpty()) {
            try {
                val json = kotlinx.serialization.json.Json { encodeDefaults = true }
                val jsonStr = json.encodeToString(answers)
                props.setValue(KEY_TODAY_ANSWERS, jsonStr)
            } catch (_: Exception) {
                props.unsetValue(KEY_TODAY_ANSWERS)
            }
        } else {
            props.unsetValue(KEY_TODAY_ANSWERS)
        }
    }

    val totalCorrect:   Int get() = props.getInt(KEY_TOTAL_CORRECT,   0)
    val totalAttempted: Int get() = props.getInt(KEY_TOTAL_ATTEMPTED, 0)
    val totalXp:        Int get() = props.getInt(KEY_TOTAL_XP,        0)
    val totalPlayed:    Int get() = props.getInt(KEY_TOTAL_PLAYED,    0)

    /** True when Istanbul-local date matches recorded completion date. */
    fun isQuizCompletedToday(): Boolean =
        lastCompletedDate == todayIstanbul() && props.getValue(KEY_TODAY_DATE) == todayIstanbulString()

    /** Returns how many total correct answers in a category (for stats display). */
    fun categoryCorrect(cat: String): Int  = props.getInt("droidle.cat.${cat}.correct",   0)
    fun categoryAttempted(cat: String): Int = props.getInt("droidle.cat.${cat}.attempted", 0)

    // ── Notification guard ────────────────────────────────────────────────────

    fun wasNotifiedToday(): Boolean =
        props.getValue(KEY_LAST_NOTIFIED) == todayIstanbul().format(DATE_FMT)

    fun markNotifiedToday() {
        props.setValue(KEY_LAST_NOTIFIED, todayIstanbul().format(DATE_FMT))
    }

    fun getUserId(): String? {
        val existing = props.getValue(KEY_USER_ID)
        if (!existing.isNullOrBlank()) return existing

        val token = getAuthToken() ?: return null
        extractAndStoreJwtClaims(token)
        return props.getValue(KEY_USER_ID)
    }
    fun saveUserId(id: String) { props.setValue(KEY_USER_ID, id) }
    fun clearUserId()          { props.unsetValue(KEY_USER_ID) }

    fun getAvatarUrl(): String? = props.getValue(KEY_AVATAR_URL)
    fun saveAvatarUrl(url: String) { props.setValue(KEY_AVATAR_URL, url) }
    fun clearAvatarUrl()          { props.unsetValue(KEY_AVATAR_URL) }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun getAuthToken(): String? = PasswordSafe.instance.getPassword(credentialAttributes())

    fun saveAuthToken(token: String) {
        PasswordSafe.instance.set(credentialAttributes(), Credentials("droidle", token))
        extractAndStoreJwtClaims(token)
    }

    fun clearAuthToken() {
        PasswordSafe.instance.set(credentialAttributes(), null)
        clearUserId()
        clearUsername()
        clearAvatarUrl()
        clearTodaySession()
    }

    private fun credentialAttributes() = CredentialAttributes("app.droidle.plugin", "github_token")

    private fun extractAndStoreJwtClaims(token: String) {
        try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val decoded = String(java.util.Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val element = json.parseToJsonElement(decoded) as? kotlinx.serialization.json.JsonObject ?: return

                val sub = (element["sub"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (!sub.isNullOrBlank()) saveUserId(sub)

                val userMetadata = element["user_metadata"] as? kotlinx.serialization.json.JsonObject
                val rawUsername = (userMetadata?.get("user_name") as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: (userMetadata?.get("preferred_username") as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (!rawUsername.isNullOrBlank() && getUsername() == null) saveUsername(rawUsername)

                val rawAvatar = (userMetadata?.get("avatar_url") as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: (userMetadata?.get("picture") as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (!rawAvatar.isNullOrBlank()) saveAvatarUrl(rawAvatar)
            }
        } catch (_: Exception) {
            // Ignore JWT decode failure
        }
    }

    // ── Username / Profile ────────────────────────────────────────────────────

    fun getUsername(): String? = props.getValue(KEY_USERNAME)

    fun saveUsername(username: String) { props.setValue(KEY_USERNAME, username) }
    fun clearUsername()                { props.unsetValue(KEY_USERNAME) }

    fun isLoggedIn(): Boolean = getAuthToken() != null

    /** Syncs remote profile data from Supabase */
    fun syncFromRemoteProfile(profile: app.droidle.plugin.model.SupabaseProfile) {
        if (!profile.username.isNullOrBlank()) saveUsername(profile.username)
        if (!profile.avatar_url.isNullOrBlank()) saveAvatarUrl(profile.avatar_url)
        props.setValue(KEY_STREAK, profile.current_streak.toString())
        props.setValue(KEY_LONGEST_STREAK, profile.max_streak.toString())
        props.setValue(KEY_TOTAL_XP, profile.total_xp.toString())
        if (!profile.last_completed_date.isNullOrBlank()) {
            props.setValue(KEY_LAST_DATE, profile.last_completed_date)
        }
    }

    // ── Language ──────────────────────────────────────────────────────────────

    var language: AppLanguage
        get() = AppLanguage.fromCode(props.getValue(KEY_LANGUAGE))
        set(value) { props.setValue(KEY_LANGUAGE, value.code) }

    // ── Keys ──────────────────────────────────────────────────────────────────

    companion object {
        val ISTANBUL_ZONE: ZoneId = ZoneId.of("Europe/Istanbul")
        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        private const val KEY_API_URL        = "droidle.apiBaseUrl"
        private const val KEY_STREAK         = "droidle.currentStreak"
        private const val KEY_LONGEST_STREAK = "droidle.longestStreak"
        private const val KEY_LAST_DATE      = "droidle.lastCompletedDate"
        private const val KEY_TOTAL_CORRECT  = "droidle.totalCorrect"
        private const val KEY_TOTAL_ATTEMPTED= "droidle.totalAttempted"
        private const val KEY_TOTAL_XP       = "droidle.totalXp"
        private const val KEY_TOTAL_PLAYED   = "droidle.totalPlayed"
        private const val KEY_LAST_NOTIFIED  = "droidle.lastNotifiedDate"
        private const val KEY_USERNAME       = "droidle.username"
        private const val KEY_USER_ID        = "droidle.userId"
        private const val KEY_AVATAR_URL     = "droidle.avatarUrl"
        private const val KEY_LANGUAGE       = "droidle.language"
        private const val KEY_TODAY_DATE     = "droidle.todayDate"
        private const val KEY_TODAY_SCORE    = "droidle.todayScore"
        private const val KEY_TODAY_XP       = "droidle.todayXp"
        private const val KEY_TODAY_OPTIONS  = "droidle.todayOptions"
        private const val KEY_TODAY_ANSWERS  = "droidle.todayAnswers"

        fun getInstance(): StateManager = service()
    }
}

/** Per-category result for a single quiz session. */
data class CategoryResult(val attempted: Int, val correct: Int)
