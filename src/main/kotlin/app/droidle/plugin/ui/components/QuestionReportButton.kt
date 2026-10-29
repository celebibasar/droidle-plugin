package app.droidle.plugin.ui.components

import app.droidle.plugin.model.Question
import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.state.StateManager
import app.droidle.plugin.ui.QuestionReportDialog
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton

/**
 * Modern JetBrains New UI styled Report Button.
 *
 * States:
 * - Unreported: "⚑ Bildir" / "⚑ Report" (or flag icon in compact mode).
 *   Subtle muted color, turns rose red on hover. Hand cursor.
 * - Reported: "✓ Bildirildi" / "✓ Reported" (or check icon in compact mode).
 *   Emerald green text and pill border, non-clickable.
 */
class QuestionReportButton(
    private var currentQuestion: Question? = null,
    private var currentLang: AppLanguage = AppLanguage.TR,
    private val project: Project? = null,
    private val compact: Boolean = false,
    private val isReportedChecker: ((String) -> Boolean)? = null,
    private val onReportSuccess: ((questionId: String) -> Unit)? = null
) : JButton() {

    private var isHovered = false
    private var isReported = false

    init {
        isBorderPainted = false
        isContentAreaFilled = false
        isFocusPainted = false
        alignmentX = Component.LEFT_ALIGNMENT

        refreshReportedState()

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                if (!isReported) {
                    isHovered = true
                    repaint()
                }
            }

            override fun mouseExited(e: MouseEvent?) {
                if (!isReported) {
                    isHovered = false
                    repaint()
                }
            }
        })

        addActionListener {
            if (!isReported) {
                openReportDialog()
            }
        }
    }

    fun updateQuestion(question: Question?, lang: AppLanguage) {
        currentQuestion = question
        currentLang = lang
        refreshReportedState()
        revalidate()
        repaint()
    }

    fun refreshReportedState(reported: Boolean? = null) {
        val qId = currentQuestion?.id
        isReported = reported ?: if (!qId.isNullOrBlank()) {
            if (isReportedChecker != null) {
                isReportedChecker.invoke(qId)
            } else {
                try {
                    StateManager.getInstance().isQuestionReported(qId)
                } catch (_: Throwable) {
                    false
                }
            }
        } else false
        applyStyling()
    }

    fun getIsReported(): Boolean = isReported

    private fun applyStyling() {
        font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
        cursor = if (isReported) Cursor.getDefaultCursor() else Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        if (isReported) {
            text = if (compact) "✓" else if (currentLang == AppLanguage.EN) "✓ Reported" else "✓ Bildirildi"
            toolTipText = if (currentLang == AppLanguage.EN)
                "Report submitted for this question"
            else
                "Bu soru için bildirim gönderildi"
            foreground = JBColor(Color(0x059669), Color(0x34D399))
            border = JBUI.Borders.empty(JBUI.scale(3), JBUI.scale(8))
        } else {
            text = if (compact) "⚑" else if (currentLang == AppLanguage.EN) "⚑ Report" else "⚑ Bildir"
            toolTipText = if (currentLang == AppLanguage.EN)
                "Report question"
            else
                "Soruyu bildir"
            foreground = if (isHovered) JBColor(Color(0xF43F5E), Color(0xFB7185)) else JBUI.CurrentTheme.Label.disabledForeground()
            border = JBUI.Borders.empty(JBUI.scale(3), JBUI.scale(8))
        }
    }

    private fun openReportDialog() {
        val q = currentQuestion ?: return
        if (q.id.isBlank()) return
        val dialog = QuestionReportDialog(
            project = project,
            question = q,
            lang = currentLang,
            onSuccess = { qId ->
                refreshReportedState(true)
                revalidate()
                repaint()
                onReportSuccess?.invoke(qId)
            }
        )
        dialog.show()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(8)

            if (isReported) {
                // Subtle emerald background & border
                g2.color = JBColor(Color(0x10, 0xB9, 0x81, 25), Color(0x10, 0xB9, 0x81, 35))
                g2.fillRoundRect(0, 0, width, height, arc, arc)

                g2.color = JBColor(Color(0x10, 0xB9, 0x81, 100), Color(0x34, 0xD3, 0x99, 100))
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
            } else if (isHovered) {
                // Subtle hover background
                g2.color = JBColor(Color(0xF4, 0x3F, 0x5E, 20), Color(0xFB, 0x71, 0x85, 25))
                g2.fillRoundRect(0, 0, width, height, arc, arc)

                g2.color = JBColor(Color(0xF4, 0x3F, 0x5E, 80), Color(0xFB, 0x71, 0x85, 80))
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)
            }
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }
}
