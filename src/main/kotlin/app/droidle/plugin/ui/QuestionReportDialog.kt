package app.droidle.plugin.ui

import app.droidle.plugin.model.Question
import app.droidle.plugin.model.ReportReason
import app.droidle.plugin.service.SupabaseService
import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.state.StateManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Modern JetBrains New UI styled DialogWrapper for reporting question errors/issues.
 * Completely localized in Turkish and English based on active language.
 */
class QuestionReportDialog(
    private val project: Project? = null,
    val question: Question,
    val lang: AppLanguage = AppLanguage.TR,
    val onSuccess: ((questionId: String) -> Unit)? = null,
    val onError: ((error: String) -> Unit)? = null
) : DialogWrapper(project, true) {

    private val radioButtons = mutableMapOf<ReportReason, JRadioButton>()
    private val detailsArea = JBTextArea(3, 30)
    private val charCountLabel = JBLabel("0/2000").apply {
        font = JBUI.Fonts.smallFont()
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
    }
    private val errorBanner = JPanel(BorderLayout()).apply {
        border = CompoundBorder(
            RoundedLineBorder(Color(0xF4, 0x3F, 0x5E), JBUI.scale(8)),
            JBUI.Borders.empty(JBUI.scale(6), JBUI.scale(10))
        )
        background = JBColor(Color(0xFFF1F2), Color(0x2D181E))
        isOpaque = true
        isVisible = false
        alignmentX = Component.LEFT_ALIGNMENT
    }
    private val errorLabel = JBLabel("").apply {
        foreground = JBColor(Color(0x9F1239), Color(0xFDA4AF))
        font = JBUI.Fonts.smallFont()
    }

    private var isSubmitting = false
    private val alreadyReported: Boolean = try {
        StateManager.getInstance().isQuestionReported(question.id)
    } catch (_: Throwable) {
        false
    }

    init {
        title = if (lang == AppLanguage.EN) "Report Question" else "Soruyu Bildir"
        setOKButtonText(
            if (alreadyReported) {
                if (lang == AppLanguage.EN) "Reported" else "Bildirildi"
            } else {
                if (lang == AppLanguage.EN) "Submit Report" else "Bildirimi Gönder"
            }
        )
        setCancelButtonText(if (lang == AppLanguage.EN) "Cancel" else "Vazgeç")
        errorBanner.add(errorLabel, BorderLayout.CENTER)
        init()
        if (alreadyReported) {
            isOKActionEnabled = false
        }
    }

    override fun createCenterPanel(): JComponent {
        val rootPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(JBUI.scale(12), JBUI.scale(16))
            preferredSize = Dimension(JBUI.scale(440), JBUI.scale(350))
        }

        // 1. Description
        val descText = if (lang == AppLanguage.EN)
            "Report an issue or correction for this question."
        else
            "Bu soruyla ilgili bir hata veya düzeltme bildirin."
        val descLabel = JBLabel(descText).apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
            font = JBUI.Fonts.smallFont()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        rootPanel.add(descLabel)
        rootPanel.add(Box.createVerticalStrut(JBUI.scale(8)))

        // 2. Already reported banner
        if (alreadyReported) {
            val reportedBanner = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
                border = CompoundBorder(
                    RoundedLineBorder(Color(0x10, 0xB9, 0x81), JBUI.scale(8)),
                    JBUI.Borders.empty(JBUI.scale(6), JBUI.scale(10))
                )
                background = JBColor(Color(0xECFDF5), Color(0x132A22))
                isOpaque = true
                alignmentX = Component.LEFT_ALIGNMENT

                val text = if (lang == AppLanguage.EN)
                    "✓ You have already submitted a report for this question."
                else
                    "✓ Bu soru için daha önce bildirim gönderdiniz."
                val label = JBLabel(text).apply {
                    foreground = JBColor(Color(0x065F46), Color(0x6EE7B7))
                    font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
                }
                add(label, BorderLayout.CENTER)
            }
            rootPanel.add(reportedBanner)
            rootPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
        }

        // 3. Dynamic error banner
        rootPanel.add(errorBanner)
        rootPanel.add(Box.createVerticalStrut(JBUI.scale(4)))

        // 4. Reason selector
        val reasonHeader = JBLabel(
            if (lang == AppLanguage.EN) "Select issue type:" else "Sorun türünü seçin:"
        ).apply {
            font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
            foreground = JBUI.CurrentTheme.Label.foreground()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        rootPanel.add(reasonHeader)
        rootPanel.add(Box.createVerticalStrut(JBUI.scale(4)))

        val radioGroup = ButtonGroup()
        val reasonsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
        }

        ReportReason.values().forEachIndexed { index, reason ->
            val rb = JRadioButton(reason.localizedLabel(lang)).apply {
                isSelected = (index == 0)
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                font = JBUI.Fonts.label(12f)
                isEnabled = !alreadyReported
            }
            radioGroup.add(rb)
            radioButtons[reason] = rb
            reasonsPanel.add(rb)
            if (index < ReportReason.values().size - 1) {
                reasonsPanel.add(Box.createVerticalStrut(JBUI.scale(2)))
            }
        }
        rootPanel.add(reasonsPanel)
        rootPanel.add(Box.createVerticalStrut(JBUI.scale(10)))

        // 5. Details textarea with live character counter
        val detailsHeader = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            val label = JBLabel(
                if (lang == AppLanguage.EN) "Additional details (optional):" else "Ek açıklama (isteğe bağlı):"
            ).apply {
                font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
                foreground = JBUI.CurrentTheme.Label.foreground()
            }
            add(label, BorderLayout.WEST)
            add(charCountLabel, BorderLayout.EAST)
        }
        rootPanel.add(detailsHeader)
        rootPanel.add(Box.createVerticalStrut(JBUI.scale(4)))

        detailsArea.apply {
            lineWrap = true
            wrapStyleWord = true
            isEnabled = !alreadyReported
            font = JBUI.Fonts.label(12f)
            emptyText.text = if (lang == AppLanguage.EN)
                "Please describe what is wrong or suggest a fix..."
            else
                "Sorunun neden hatalı olduğunu veya düzeltme önerinizi açıklayabilirsiniz..."
            border = JBUI.Borders.empty(JBUI.scale(6))

            document.addDocumentListener(object : DocumentListener {
                private fun updateCount() {
                    val count = text.length
                    charCountLabel.text = "$count/2000"
                    if (count > 2000) {
                        charCountLabel.foreground = JBColor(Color(0xF43F5E), Color(0xFB7185))
                    } else {
                        charCountLabel.foreground = JBUI.CurrentTheme.Label.disabledForeground()
                    }
                }
                override fun insertUpdate(e: DocumentEvent?) = updateCount()
                override fun removeUpdate(e: DocumentEvent?) = updateCount()
                override fun changedUpdate(e: DocumentEvent?) = updateCount()
            })
        }

        val scrollArea = JBScrollPane(detailsArea).apply {
            border = RoundedLineBorder(JBColor(Color(0xD4D4D8), Color(0x3F3F46)), JBUI.scale(8))
            alignmentX = Component.LEFT_ALIGNMENT
            preferredSize = Dimension(JBUI.scale(410), JBUI.scale(70))
            maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(85))
        }
        rootPanel.add(scrollArea)
        rootPanel.add(Box.createVerticalStrut(JBUI.scale(10)))

        // 6. Question ID indicator
        val idLabel = JBLabel("ID: ${question.id} • ${question.category} • ${question.difficulty}").apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(10f).toInt())
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
            alignmentX = Component.LEFT_ALIGNMENT
        }
        rootPanel.add(idLabel)

        return rootPanel
    }

    fun getSelectedReason(): ReportReason {
        return radioButtons.entries.firstOrNull { it.value.isSelected }?.key ?: ReportReason.INCORRECT_ANSWER
    }

    fun getDetailsText(): String = detailsArea.text.trim()

    override fun doValidate(): ValidationInfo? {
        if (detailsArea.text.length > 2000) {
            val msg = if (lang == AppLanguage.EN)
                "Details cannot exceed 2000 characters."
            else
                "Açıklama en fazla 2000 karakter olabilir."
            return ValidationInfo(msg, detailsArea)
        }
        return null
    }

    override fun doOKAction() {
        if (isSubmitting || alreadyReported) return

        val selectedReason = getSelectedReason()
        val detailsText = getDetailsText()

        isSubmitting = true
        isOKActionEnabled = false
        setOKButtonText(if (lang == AppLanguage.EN) "Submitting..." else "Gönderiliyor...")

        val executeSubmission = {
            val userId = try { StateManager.getInstance().getUserId() } catch (_: Throwable) { null }
            val success = SupabaseService.getInstance().reportQuestion(
                questionId = question.id,
                reason = selectedReason.key,
                details = detailsText.ifBlank { null },
                userId = userId
            )

            SwingUtilities.invokeLater {
                if (success) {
                    try {
                        StateManager.getInstance().markQuestionAsReported(question.id)
                    } catch (_: Throwable) {}
                    showSuccessNotification(project, lang)
                    onSuccess?.invoke(question.id)
                    close(OK_EXIT_CODE)
                } else {
                    isSubmitting = false
                    isOKActionEnabled = true
                    setOKButtonText(if (lang == AppLanguage.EN) "Submit Report" else "Bildirimi Gönder")
                    val errorMsg = if (lang == AppLanguage.EN)
                        "An error occurred while submitting your report."
                    else
                        "Bildirim gönderilirken bir hata oluştu."
                    errorLabel.text = errorMsg
                    errorBanner.isVisible = true
                    rootPane.revalidate()
                    rootPane.repaint()
                    showErrorNotification(project, lang)
                    onError?.invoke(errorMsg)
                }
            }
        }

        val app = ApplicationManager.getApplication()
        if (app != null) {
            app.executeOnPooledThread(executeSubmission)
        } else {
            Thread(executeSubmission).start()
        }
    }

    companion object {
        fun showSuccessNotification(project: Project?, lang: AppLanguage) {
            val title = if (lang == AppLanguage.EN) "Report Submitted" else "Bildirim Gönderildi"
            val message = if (lang == AppLanguage.EN)
                "Thank you for your report! We will review it."
            else
                "Bildiriminiz için teşekkürler! İncelenecektir."

            try {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Droidle")
                    ?.createNotification(title, message, NotificationType.INFORMATION)
                    ?.notify(project)
            } catch (_: Throwable) {}
        }

        fun showErrorNotification(project: Project?, lang: AppLanguage) {
            val title = if (lang == AppLanguage.EN) "Report Failed" else "Bildirim Gönderilemedi"
            val message = if (lang == AppLanguage.EN)
                "An error occurred while submitting your report."
            else
                "Bildirim gönderilirken bir hata oluştu."

            try {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Droidle")
                    ?.createNotification(title, message, NotificationType.ERROR)
                    ?.notify(project)
            } catch (_: Throwable) {}
        }
    }
}
