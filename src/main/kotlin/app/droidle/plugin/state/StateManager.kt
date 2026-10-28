package app.droidle.plugin.state

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul")
private val DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE

data class DroidleSettings(
    val apiBaseUrl: String = "https://droidle.vercel.app"
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
        get() = DroidleSettings(apiBaseUrl = props.getValue(KEY_API_URL, "https://droidle.vercel.app"))
        set(value) { props.setValue(KEY_API_URL, value.apiBaseUrl) }

    // ── Streak (Europe/Istanbul, mirrors web calculateStreak()) ───────────────

    val currentStreak: Int get() = props.getInt(KEY_STREAK, 0)
    val longestStreak: Int get() = props.getInt(KEY_LONGEST_STREAK, 0)

    /** Today's date in Europe/Istanbul — always matches web's getIstanbulDateString(). */
    fun todayIstanbul(): LocalDate = LocalDate.now(ISTANBUL_ZONE)

    val lastCompletedDate: LocalDate?
        get() = props.getValue(KEY_LAST_DATE)?.let {
            runCatching { LocalDate.parse(it, DATE_FMT) }.getOrNull()
        }

    /**
     * Records quiz completion with Istanbul-aware streak logic.
     * Mirrors web src/lib/streak.ts#calculateStreak() exactly:
     *   null → 1 | yesterday → streak+1 | today → no-op | gap → 1
     */
    fun recordQuizCompletion(
        correctCount: Int,
        totalCount: Int,
        earnedXp: Int = 0,
        categoryResults: Map<String, CategoryResult> = emptyMap()
    ) {
        val today = todayIstanbul()
        val last  = lastCompletedDate

        // Prevent double-counting if already recorded today
        if (last == today) {
            // Update scores if server gave us better data, but don't re-increment counters
            return
        }

        val newStreak = when {
            last == null              -> 1
            last == today.minusDays(1) -> currentStreak + 1
            else                      -> 1   // gap of ≥ 1 day → reset
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
     * Does NOT increment counters — just ensures local state reflects server truth.
     */
    fun syncFromServer(
        correctCount: Int,
        totalCount: Int,
        serverStreak: Int? = null,
        earnedXp: Int = 0
    ) {
        val today = todayIstanbul()
        val last  = lastCompletedDate

        // Mark as completed today if not already
        if (last != today) {
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

    val totalCorrect:   Int get() = props.getInt(KEY_TOTAL_CORRECT,   0)
    val totalAttempted: Int get() = props.getInt(KEY_TOTAL_ATTEMPTED, 0)
    val totalXp:        Int get() = props.getInt(KEY_TOTAL_XP,        0)
    val totalPlayed:    Int get() = props.getInt(KEY_TOTAL_PLAYED,    0)

    /** True when Istanbul-local date matches recorded completion date. */
    fun isQuizCompletedToday(): Boolean = lastCompletedDate == todayIstanbul()

    /** Returns how many total correct answers in a category (for stats display). */
    fun categoryCorrect(cat: String): Int  = props.getInt("droidle.cat.${cat}.correct",   0)
    fun categoryAttempted(cat: String): Int = props.getInt("droidle.cat.${cat}.attempted", 0)

    // ── Notification guard ────────────────────────────────────────────────────

    fun wasNotifiedToday(): Boolean =
        props.getValue(KEY_LAST_NOTIFIED) == todayIstanbul().format(DATE_FMT)

    fun markNotifiedToday() {
        props.setValue(KEY_LAST_NOTIFIED, todayIstanbul().format(DATE_FMT))
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    fun getAuthToken(): String? = PasswordSafe.instance.getPassword(credentialAttributes())

    fun saveAuthToken(token: String) {
        PasswordSafe.instance.set(credentialAttributes(), Credentials("droidle", token))
    }

    fun clearAuthToken() {
        PasswordSafe.instance.set(credentialAttributes(), null)
    }

    private fun credentialAttributes() = CredentialAttributes("app.droidle.plugin", "github_token")

    // ── Username / Profile ────────────────────────────────────────────────────

    fun getUsername(): String? = props.getValue(KEY_USERNAME)

    fun saveUsername(username: String) { props.setValue(KEY_USERNAME, username) }
    fun clearUsername()                { props.unsetValue(KEY_USERNAME) }

    fun isLoggedIn(): Boolean = getAuthToken() != null

    // ── Language ──────────────────────────────────────────────────────────────

    var language: AppLanguage
        get() = AppLanguage.fromCode(props.getValue(KEY_LANGUAGE))
        set(value) { props.setValue(KEY_LANGUAGE, value.code) }

    // ── Keys ──────────────────────────────────────────────────────────────────

    companion object {
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
        private const val KEY_LANGUAGE       = "droidle.language"

        fun getInstance(): StateManager = service()
    }
}

/** Per-category result for a single quiz session. */
data class CategoryResult(val attempted: Int, val correct: Int)
