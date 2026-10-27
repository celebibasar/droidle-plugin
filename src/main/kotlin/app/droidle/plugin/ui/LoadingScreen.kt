package app.droidle.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JPanel

/**
 * Full-panel loading state with IDE-native spinner.
 * Uses AnimatedIcon.Default — the same spinner IntelliJ uses for inspections/indexing.
 */
class LoadingScreen : JPanel(GridBagLayout()) {

    init {
        border = JBUI.Borders.empty()
        isOpaque = false

        val gbc = GridBagConstraints().apply {
            gridx = 0; gridy = GridBagConstraints.RELATIVE
            anchor = GridBagConstraints.CENTER
            fill = GridBagConstraints.NONE
        }

        // Spinner
        val spinner = JBLabel(AnimatedIcon.Default())
        gbc.insets = Insets(0, 0, JBUI.scale(8), 0)
        add(spinner, gbc)

        // Caption
        val caption = JBLabel("Sorular hazırlanıyor…").apply {
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }
        gbc.insets = Insets(0, 0, 0, 0)
        add(caption, gbc)
    }
}
