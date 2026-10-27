package app.droidle.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Error empty-state: icon + message + retry button.
 */
class ErrorScreen(private val onRetry: () -> Unit) : JPanel(GridBagLayout()) {

    private val messageArea = JTextArea("").apply {
        isEditable  = false
        isOpaque    = false
        lineWrap    = true
        wrapStyleWord = true
        columns     = 28
        foreground  = JBUI.CurrentTheme.Label.disabledForeground()
        font        = JBUI.Fonts.smallFont()
        border      = JBUI.Borders.empty()
    }

    init {
        border = JBUI.Borders.empty(JBUI.scale(16))
        isOpaque = false

        val gbc = GridBagConstraints().apply {
            gridx = 0; gridy = GridBagConstraints.RELATIVE
            anchor = GridBagConstraints.CENTER
            fill = GridBagConstraints.NONE
        }

        // Icon
        add(JBLabel(AllIcons.General.Error), gbc.also { it.insets = Insets(0, 0, JBUI.scale(10), 0) })

        // Title
        add(JBLabel("<html><b>Bağlantı hatası</b></html>").apply {
            font = JBUI.Fonts.label(14f)
        }, gbc.also { it.insets = Insets(0, 0, JBUI.scale(6), 0) })

        // Detail
        add(messageArea, gbc.also { it.insets = Insets(0, 0, JBUI.scale(16), 0) })

        // Retry button
        val retryBtn = JButton("Tekrar Dene").apply {
            addActionListener { onRetry() }
        }
        add(retryBtn, gbc.also { it.insets = Insets(0, 0, 0, 0) })
    }

    fun setMessage(msg: String) {
        messageArea.text = msg
    }
}
