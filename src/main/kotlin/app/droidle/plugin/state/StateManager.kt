package app.droidle.plugin.state

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

@Service(Service.Level.APP)
class StateManager {

    private val props = PropertiesComponent.getInstance()

    var settings: DroidleSettings
        get() = DroidleSettings(apiBaseUrl = props.getValue(KEY_API_URL, "https://droidle.vercel.app"))
        set(value) { props.setValue(KEY_API_URL, value.apiBaseUrl) }

    val currentStreak: Int get() = props.getInt(KEY_STREAK, 0)
    val longestStreak: Int get() = props.getInt(KEY_LONGEST_STREAK, 0)

    val lastCompletedDate: LocalDate?
        get() = props.getValue(KEY_LAST_DATE)?.let {
            runCatching { LocalDate.parse(it, DATE_FMT) }.getOrNull()
        }

    fun recordQuizCompletion(correctCount: Int, totalCount: Int) {
        val today = LocalDate.now()
        val last = lastCompletedDate

        val newStreak = when {
            last == null -> 1
            last == today.minusDays(1) -> currentStreak + 1
            last == today -> currentStreak
            else -> 1
        }

        props.setValue(KEY_LAST_DATE, today.format(DATE_FMT))
        props.setValue(KEY_STREAK, newStreak.toString())
        if (newStreak > longestStreak) props.setValue(KEY_LONGEST_STREAK, newStreak.toString())
        props.setValue(KEY_TOTAL_CORRECT, (totalCorrect + correctCount).toString())
        props.setValue(KEY_TOTAL_ATTEMPTED, (totalAttempted + totalCount).toString())
    }

    val totalCorrect: Int get() = props.getInt(KEY_TOTAL_CORRECT, 0)
    val totalAttempted: Int get() = props.getInt(KEY_TOTAL_ATTEMPTED, 0)

    fun isQuizCompletedToday(): Boolean = lastCompletedDate == LocalDate.now()

    /** Bugün IDE açılış bildirimi gönderildi mi? */
    fun wasNotifiedToday(): Boolean =
        props.getValue(KEY_LAST_NOTIFIED) == LocalDate.now().format(DATE_FMT)

    fun markNotifiedToday() {
        props.setValue(KEY_LAST_NOTIFIED, LocalDate.now().format(DATE_FMT))
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

    // ── Username ──────────────────────────────────────────────────────────────

    fun getUsername(): String? = props.getValue(KEY_USERNAME)

    fun saveUsername(username: String) {
        props.setValue(KEY_USERNAME, username)
    }

    fun clearUsername() {
        props.unsetValue(KEY_USERNAME)
    }

    fun isLoggedIn(): Boolean = getAuthToken() != null

    // ── Language ──────────────────────────────────────────────────────────────

    var language: AppLanguage
        get() = AppLanguage.fromCode(props.getValue(KEY_LANGUAGE))
        set(value) { props.setValue(KEY_LANGUAGE, value.code) }

    companion object {
        private const val KEY_API_URL           = "droidle.apiBaseUrl"
        private const val KEY_STREAK            = "droidle.currentStreak"
        private const val KEY_LONGEST_STREAK    = "droidle.longestStreak"
        private const val KEY_LAST_DATE         = "droidle.lastCompletedDate"
        private const val KEY_TOTAL_CORRECT     = "droidle.totalCorrect"
        private const val KEY_TOTAL_ATTEMPTED   = "droidle.totalAttempted"
        private const val KEY_LAST_NOTIFIED     = "droidle.lastNotifiedDate"
        private const val KEY_USERNAME          = "droidle.username"
        private const val KEY_LANGUAGE          = "droidle.language"

        fun getInstance(): StateManager = service()
    }
}
