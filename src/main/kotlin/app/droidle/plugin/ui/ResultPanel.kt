package app.droidle.plugin.ui

import app.droidle.plugin.model.Question
import app.droidle.plugin.state.StateManager
import com.intellij.ide.BrowserUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableModel

class ResultPanel(
    correctCount: Int,
    questions: List<Question>,
    @Suppress("UNUSED_PARAMETER") onRetry: () -> Unit
) : JPanel() {

    init {
        layout = BorderLayout(0, 12)
        border = JBUI.Borders.empty(16)

        val state = StateManager.getInstance()
        val total = questions.size
        val accuracy = if (total > 0) (correctCount * 100) / total else 0

        val scoreColor = when {
            accuracy >= 80 -> JBColor(Color(0, 140, 0), Color(80, 200, 80))
            accuracy >= 50 -> JBColor(Color(180, 120, 0), Color(220, 180, 60))
            else -> JBColor(Color(180, 0, 0), Color(220, 80, 80))
        }

        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)

            add(JBLabel("$correctCount / $total", SwingConstants.CENTER).apply {
                font = font.deriveFont(Font.BOLD, 36f)
                foreground = scoreColor
                alignmentX = Component.CENTER_ALIGNMENT
            })
            add(Box.createVerticalStrut(4))
            add(JBLabel("$accuracy% doğru", SwingConstants.CENTER).apply {
                foreground = JBColor.GRAY
                alignmentX = Component.CENTER_ALIGNMENT
            })
            add(Box.createVerticalStrut(8))
            add(JBLabel("🔥 ${state.currentStreak} gün streak", SwingConstants.CENTER).apply {
                font = font.deriveFont(Font.BOLD, 14f)
                alignmentX = Component.CENTER_ALIGNMENT
            })
        }

        val tableModel = DefaultTableModel(arrayOf("Kategori", "Soru"), 0)
        questions.groupBy { it.category }.forEach { (cat, qs) ->
            tableModel.addRow(arrayOf(cat, qs.size.toString()))
        }
        val table = JTable(tableModel).apply {
            isEnabled = false
            tableHeader.reorderingAllowed = false
            rowHeight = 24
        }

        val southPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)

            add(JPanel(FlowLayout(FlowLayout.CENTER)).apply {
                add(JButton("Droidle.app'te Gör →").apply {
                    addActionListener { BrowserUtil.browse("https://droidle.vercel.app") }
                })
            })
            add(Box.createVerticalStrut(8))
            add(JBLabel(
                "Toplam: ${state.totalCorrect} doğru / ${state.totalAttempted} soru",
                SwingConstants.CENTER
            ).apply {
                font = font.deriveFont(10f)
                foreground = JBColor.GRAY
                alignmentX = Component.CENTER_ALIGNMENT
            })
        }

        add(headerPanel, BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)
        add(southPanel, BorderLayout.SOUTH)
    }
}
