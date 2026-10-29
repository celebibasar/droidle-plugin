package app.droidle.plugin.ui

import app.droidle.plugin.state.AppLanguage
import app.droidle.plugin.state.StateManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JPanel

class LoadingScreen : JPanel(GridBagLayout()) {

    private val green = Color(0x10, 0xB9, 0x81)
    private val captionLabel: JBLabel

    init {
        border = JBUI.Borders.empty()
        isOpaque = false

        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = GridBagConstraints.RELATIVE
            anchor = GridBagConstraints.CENTER
            fill = GridBagConstraints.NONE
        }

        // Logo
        add(JBLabel("🤖 Droidle").apply {
            font = JBUI.Fonts.label(10f)
            foreground = green
        }, gbc.also { it.insets = Insets(0, 0, JBUI.scale(10), 0) })

        // Big spinner
        add(JBLabel(AnimatedIcon.Big()), gbc.also { it.insets = Insets(0, 0, JBUI.scale(12), 0) })

        // Caption — language-aware
        captionLabel = JBLabel(caption()).apply {
            font = JBUI.Fonts.label(12f)
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }
        add(captionLabel, gbc.also { it.insets = Insets(0, 0, 0, 0) })
    }

    /** Call this when the language changes so the caption updates instantly. */
    fun refreshLanguage() {
        captionLabel.text = caption()
    }

    private fun caption(): String =
        if (StateManager.getInstance().language == AppLanguage.EN)
            "Loading daily questions..."
        else
            "Günlük sorular yükleniyor..."
}
