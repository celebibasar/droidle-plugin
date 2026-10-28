package app.droidle.plugin.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea

class ErrorScreen(private val onRetry: () -> Unit) : JPanel(GridBagLayout()) {

    private val red = Color(0xEF, 0x44, 0x44)

    private val messageArea = JTextArea("").apply {
        isEditable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        columns = 26
        rows = 3
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
        font = JBUI.Fonts.smallFont()
        border = JBUI.Borders.empty()
    }

    init {
        border = JBUI.Borders.empty(JBUI.scale(20))
        isOpaque = false

        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = GridBagConstraints.RELATIVE
            anchor = GridBagConstraints.CENTER
            fill = GridBagConstraints.NONE
        }

        // Red circle with "!" icon
        val errorIcon = object : JPanel() {
            init {
                preferredSize = Dimension(JBUI.scale(50), JBUI.scale(50))
                isOpaque = false
            }
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val size = JBUI.scale(50)
                g2.color = red
                g2.fillOval(0, 0, size, size)
                g2.color = Color.WHITE
                g2.font = Font(Font.SANS_SERIF, Font.BOLD, JBUI.scale(22))
                val fm = g2.fontMetrics
                val text = "!"
                val x = (size - fm.stringWidth(text)) / 2
                val y = (size + fm.ascent - fm.descent) / 2
                g2.drawString(text, x, y)
            }
        }
        add(errorIcon, gbc.also { it.insets = Insets(0, 0, JBUI.scale(14), 0) })

        // Title
        add(JBLabel("<html><b>Bağlantı Hatası</b></html>").apply {
            font = JBUI.Fonts.label(15f)
        }, gbc.also { it.insets = Insets(0, 0, JBUI.scale(8), 0) })

        // Message
        add(messageArea, gbc.also { it.insets = Insets(0, 0, JBUI.scale(20), 0) })

        // Outlined red retry button
        val retryBtn = object : JButton("Tekrar Dene") {
            init {
                isContentAreaFilled = false
                isBorderPainted = false
                isFocusPainted = false
                isOpaque = false
                font = JBUI.Fonts.label(12f)
                foreground = red
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                preferredSize = Dimension(JBUI.scale(160), JBUI.scale(34))
            }
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val bg = if (model.isPressed || model.isRollover) Color(red.red, red.green, red.blue, 20) else Color(0, 0, 0, 0)
                g2.color = bg
                g2.fillRoundRect(0, 0, width, height, JBUI.scale(8), JBUI.scale(8))
                g2.color = red
                g2.stroke = BasicStroke(1.5f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, JBUI.scale(8), JBUI.scale(8))
                super.paintComponent(g)
            }
        }
        retryBtn.addActionListener { onRetry() }
        add(retryBtn, gbc.also { it.insets = Insets(0, 0, 0, 0) })
    }

    fun setMessage(msg: String) {
        messageArea.text = msg
    }
}
