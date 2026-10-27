package app.droidle.plugin

import app.droidle.plugin.state.StateManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.delay

/**
 * IDE her proje açılışında çalışır.
 * O gün quiz gösterilmediyse kullanıcıya balloon bildirim gönderir.
 */
class DroidleStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // IDE tam yüklensin diye kısa bekle
        delay(3_000)

        val state = StateManager.getInstance()
        if (state.isQuizCompletedToday()) return  // Zaten tamamlamış, rahatsız etme
        if (state.wasNotifiedToday()) return       // Bugün zaten bildirdik

        state.markNotifiedToday()

        val group = NotificationGroupManager.getInstance()
            .getNotificationGroup("Droidle") ?: return

        val streak = state.currentStreak
        val streakText = if (streak > 0) " 🔥 $streak günlük streak devam ediyor!" else ""

        group.createNotification(
            title = "🤖 Günlük Droidle Quiz Hazır!",
            content = "Bugünün 5 Android sorusunu çözdün mü?$streakText",
            type = NotificationType.INFORMATION
        ).apply {
            addAction(com.intellij.notification.NotificationAction.createSimple("Quiz'i Aç →") {
                expire()
                ToolWindowManager.getInstance(project).getToolWindow("Droidle")?.show()
            })
        }.notify(project)
    }
}
