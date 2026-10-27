package app.droidle.plugin

import app.droidle.plugin.state.StateManager
import app.droidle.plugin.ui.QuizPanel
import app.droidle.plugin.ui.ResultPanel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class DroidleToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance()
            .createContent(buildPanel(toolWindow), null, false)
        toolWindow.contentManager.addContent(content)
    }

    private fun buildPanel(toolWindow: ToolWindow): JPanel {
        val state = StateManager.getInstance()

        return if (state.isQuizCompletedToday()) {
            JPanel(BorderLayout()).apply {
                add(JLabel(
                    "<html><center>✅ Bugünkü quiz tamamlandı!<br><br>" +
                    "🔥 ${state.currentStreak} gün streak<br><br>" +
                    "Yarın tekrar gel.</center></html>",
                    SwingConstants.CENTER
                ), BorderLayout.CENTER)
            }
        } else {
            QuizPanel { correctCount, questions ->
                val result = ResultPanel(correctCount, questions) {}
                val newContent = ContentFactory.getInstance()
                    .createContent(result, null, false)
                toolWindow.contentManager.removeAllContents(true)
                toolWindow.contentManager.addContent(newContent)
            }
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
