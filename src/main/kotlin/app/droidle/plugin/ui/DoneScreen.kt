package app.droidle.plugin.ui

import app.droidle.plugin.state.StateManager
import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Shown when the user already completed today's quiz.
 * Displays streak and a link to the web leaderboard.
 */
class DoneScreen(private val onRetry: () -> Unit) : JPanel(GridBagLayout()) {

    init {
        border = JBUI.Borders.empty(JBUI.scale(16))
        isOpaque = false

        val state = StateManager.getInstance()
        val streak = state.currentStreak

        val gbc = GridBagConstraints().apply {
            gridx = 0; gridy = GridBagConstraints.RELATIVE
            anchor = GridBagConstraints.CENTER
            fill = GridBagConstraints.NONE
        }

        // Trophy icon
        add(JBLabel(AllIcons.Actions.Checked), gbc.also { it.insets = Insets(0, 0, JBUI.scale(10), 0) })

        // Headline
        add(JBLabel("<html><b>Bugün tamamlandı!</b></html>").apply {
            font = JBUI.Fonts.label(14f)
        }, gbc.also { it.insets = Insets(0, 0, JBUI.scale(6), 0) })

        // Streak badge
        val streakText = if (streak > 1) "🔥 $streak günlük seri devam ediyor" else "İlk gün tamamlandı 🎉"
        add(JBLabel(streakText).apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }, gbc.also { it.insets = Insets(0, 0, JBUI.scale(20), 0) })

        // Stats summary
        val total   = state.totalAttempted
        val correct = state.totalCorrect
        if (total > 0) {
            val pct = (correct * 100) / total
            add(JBLabel("<html><center>Toplam doğru: <b>$correct / $total</b> (%$pct)</center></html>").apply {
                foreground = JBUI.CurrentTheme.Label.disabledForeground()
                font = JBUI.Fonts.smallFont()
            }, gbc.also { it.insets = Insets(0, 0, JBUI.scale(20), 0) })
        }

        // Web link
        add(JButton("Sıralamayı Gör →").apply {
            addActionListener { onRetry() }
        }, gbc.also { it.insets = Insets(0, 0, 0, 0) })
    }
}
