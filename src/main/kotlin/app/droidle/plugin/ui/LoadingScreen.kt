package app.droidle.plugin.ui

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

        // Caption
        add(JBLabel("Günlük sorular yükleniyor...").apply {
            font = JBUI.Fonts.label(12f)
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }, gbc.also { it.insets = Insets(0, 0, 0, 0) })
    }
}
