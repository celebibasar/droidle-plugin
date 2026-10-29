package app.droidle.plugin.ui.components

import app.droidle.plugin.ui.TextFormatter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

enum class ModernOptionCardState {
    DEFAULT,
    HOVER,
    CORRECT,
    WRONG,
    MUTED
}

/**
 * Modern Linear-style Option Card with [A], [B], [C], [D] keyboard badge and 0ms instant feedback.
 */
class ModernOptionCard(
    val key: String,
    private var optionText: String,
    private val onClick: (String) -> Unit
) : JPanel(BorderLayout(JBUI.scale(10), 0)) {

    var cardState: ModernOptionCardState = ModernOptionCardState.DEFAULT
        private set

    private val badge = KeyboardBadge(key)
    private val textLabel = AutoWrappingHtmlLabel(TextFormatter.formatOptionHtml(optionText)).apply {
        font = JBUI.Fonts.label(12.5f)
        horizontalAlignment = SwingConstants.LEFT
        alignmentX = Component.LEFT_ALIGNMENT
    }

    init {
        isOpaque = false
        val handCursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        cursor = handCursor
        textLabel.cursor = handCursor
        badge.cursor = handCursor
        border = JBUI.Borders.empty(JBUI.scale(10), JBUI.scale(12))
        alignmentX = Component.LEFT_ALIGNMENT

        val badgeWrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(badge, BorderLayout.NORTH)
        }
        add(badgeWrapper, BorderLayout.WEST)
        add(textLabel, BorderLayout.CENTER)

        val mouseHandler = object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                if (cardState == ModernOptionCardState.DEFAULT && isEnabled) {
                    cardState = ModernOptionCardState.HOVER
                    repaint()
                }
            }
            override fun mouseExited(e: MouseEvent?) {
                if (cardState == ModernOptionCardState.HOVER && isEnabled) {
                    cardState = ModernOptionCardState.DEFAULT
                    repaint()
                }
            }
            override fun mousePressed(e: MouseEvent?) {
                if (isEnabled && (cardState == ModernOptionCardState.DEFAULT || cardState == ModernOptionCardState.HOVER)) {
                    onClick(key)
                }
            }
        }
        addMouseListener(mouseHandler)
        textLabel.addMouseListener(mouseHandler)
        badge.addMouseListener(mouseHandler)
    }

    fun applyAnswerState(isCorrect: Boolean, isSelected: Boolean) {
        cardState = when {
            isCorrect                -> ModernOptionCardState.CORRECT
            isSelected && !isCorrect -> ModernOptionCardState.WRONG
            else                     -> ModernOptionCardState.MUTED
        }
        badge.setState(cardState)
        repaint()
    }

    fun resetState() {
        cardState = ModernOptionCardState.DEFAULT
        badge.setState(ModernOptionCardState.DEFAULT)
        isEnabled = true
        repaint()
    }

    fun updateText(newText: String) {
        optionText = newText
        textLabel.text = TextFormatter.formatOptionHtml(newText)
        revalidate()
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(12)

            // Background
            val bg = when (cardState) {
                ModernOptionCardState.DEFAULT -> JBColor(Color(0xFFFFFF), Color(0x27272A))
                ModernOptionCardState.HOVER   -> JBColor(Color(0xF4F5F7), Color(0x323338))
                ModernOptionCardState.CORRECT -> JBColor(Color(0xECFDF5), Color(0x133827))
                ModernOptionCardState.WRONG   -> JBColor(Color(0xFFF1F2), Color(0x3B1820))
                ModernOptionCardState.MUTED   -> JBColor(Color(0xFAFAFA), Color(0x202023))
            }
            g2.color = bg
            g2.fillRoundRect(0, 0, width, height, arc, arc)

            // 1px Border
            val borderCol = when (cardState) {
                ModernOptionCardState.DEFAULT -> JBColor(Color(0xE4E4E7), Color(0x3F3F46))
                ModernOptionCardState.HOVER   -> JBColor(Color(0x10B981), Color(0x059669))
                ModernOptionCardState.CORRECT -> JBColor(Color(0x10B981), Color(0x10B981))
                ModernOptionCardState.WRONG   -> JBColor(Color(0xF43F5E), Color(0xF43F5E))
                ModernOptionCardState.MUTED   -> JBColor(Color(0xE4E4E7), Color(0x2E3035))
            }
            val strokeW = if (cardState == ModernOptionCardState.CORRECT || cardState == ModernOptionCardState.WRONG) 1.5f else 1f
            g2.stroke = BasicStroke(JBUI.scale(1).toFloat() * strokeW)
            g2.color = borderCol
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)

            // Text foreground
            textLabel.foreground = when (cardState) {
                ModernOptionCardState.CORRECT -> JBColor(Color(0x065F46), Color(0x6EE7B7))
                ModernOptionCardState.WRONG   -> JBColor(Color(0x9F1239), Color(0xFDA4AF))
                ModernOptionCardState.MUTED   -> JBUI.CurrentTheme.Label.disabledForeground()
                else                          -> JBUI.CurrentTheme.Label.foreground()
            }
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    /**
     * Keyboard badge [A], [B], [C], [D].
     */
    private class KeyboardBadge(val key: String) : JComponent() {
        private var state = ModernOptionCardState.DEFAULT

        init {
            val size = JBUI.scale(24)
            preferredSize = Dimension(size, size)
            minimumSize   = preferredSize
            maximumSize   = preferredSize
            isOpaque      = false
            alignmentX    = Component.LEFT_ALIGNMENT
        }

        fun setState(newState: ModernOptionCardState) {
            state = newState
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                val arc = JBUI.scale(6)

                val bg = when (state) {
                    ModernOptionCardState.CORRECT -> Color(0x10, 0xB9, 0x81)
                    ModernOptionCardState.WRONG   -> Color(0xF4, 0x3F, 0x5E)
                    ModernOptionCardState.HOVER   -> JBColor(Color(0x10B981), Color(0x059669))
                    else                          -> JBColor(Color(0xF4F5F7), Color(0x323338))
                }
                g2.color = bg
                g2.fillRoundRect(0, 0, width, height, arc, arc)

                val fg = when (state) {
                    ModernOptionCardState.CORRECT, ModernOptionCardState.WRONG -> Color.WHITE
                    ModernOptionCardState.HOVER -> Color.WHITE
                    else -> JBColor(Color(0x71717A), Color(0xA1A1AA))
                }
                g2.color = fg
                g2.font = JBUI.Fonts.label(11f).deriveFont(Font.BOLD)
                val fm = g2.fontMetrics
                val tx = (width - fm.stringWidth(key)) / 2
                val ty = (height + fm.ascent - fm.descent) / 2
                g2.drawString(key, tx, ty)
            } finally {
                g2.dispose()
            }
        }
    }
}
