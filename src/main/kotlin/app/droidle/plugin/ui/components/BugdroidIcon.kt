package app.droidle.plugin.ui.components

import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent

/**
 * Interactive vector-drawn Android Bugdroid icon for ResultScreen.
 *
 * Drawn precisely via Graphics2D vector paths with Anti-Aliasing.
 * - Emerald Green (#10B981) if question answered correctly.
 * - Rose Red (#F43F5E) if question answered incorrectly.
 * - Selected state with active accent ring.
 * - Hover state with hand cursor.
 * - Clicking triggers onClick(questionIndex).
 */
class BugdroidIcon(
    val questionIndex: Int,
    val isCorrect: Boolean,
    var isSelected: Boolean = false,
    private val onClick: (Int) -> Unit
) : JComponent() {

    private var isHovered = false

    private val green = Color(0x10, 0xB9, 0x81)
    private val red   = Color(0xF4, 0x3F, 0x5E)

    init {
        val w = JBUI.scale(48)
        val h = JBUI.scale(56)
        preferredSize = Dimension(w, h)
        minimumSize   = preferredSize
        maximumSize   = preferredSize
        cursor        = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        isOpaque      = false

        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                isHovered = true
                repaint()
            }
            override fun mouseExited(e: MouseEvent?) {
                isHovered = false
                repaint()
            }
            override fun mousePressed(e: MouseEvent?) {
                onClick(questionIndex)
            }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val color = if (isCorrect) green else red

            // Active or hover highlight pill
            if (isSelected || isHovered) {
                val bgAlpha = if (isSelected) 35 else 18
                g2.color = Color(color.red, color.green, color.blue, bgAlpha)
                g2.fillRoundRect(0, 0, width, height, JBUI.scale(12), JBUI.scale(12))

                if (isSelected) {
                    g2.color = Color(color.red, color.green, color.blue, 180)
                    g2.stroke = BasicStroke(JBUI.scale(15) / 10f)
                    g2.drawRoundRect(0, 0, width - 1, height - 1, JBUI.scale(12), JBUI.scale(12))
                }
            }

            // Coordinate calculations for Bugdroid vector parts
            val cx = width / 2f
            val cy = (height - JBUI.scale(14)) / 2f + JBUI.scale(1)
            val s  = JBUI.scale(1).toFloat()

            g2.color = color

            // 1. Antennas (angled lines with rounded caps)
            g2.stroke = BasicStroke(1.5f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g2.drawLine((cx - 4 * s).toInt(), (cy - 7 * s).toInt(), (cx - 7.5f * s).toInt(), (cy - 12.5f * s).toInt())
            g2.drawLine((cx + 4 * s).toInt(), (cy - 7 * s).toInt(), (cx + 7.5f * s).toInt(), (cy - 12.5f * s).toInt())

            // 2. Head (semi-circle dome)
            g2.fillArc((cx - 9 * s).toInt(), (cy - 10 * s).toInt(), (18 * s).toInt(), (18 * s).toInt(), 0, 180)

            // 3. Eyes (white circular dots)
            g2.color = Color.WHITE
            val eyeR = (1.2f * s).toInt().coerceAtLeast(1)
            g2.fillOval((cx - 4.5f * s).toInt(), (cy - 5.5f * s).toInt(), eyeR * 2, eyeR * 2)
            g2.fillOval((cx + 2.5f * s).toInt(), (cy - 5.5f * s).toInt(), eyeR * 2, eyeR * 2)

            // 4. Body (rounded rectangle)
            g2.color = color
            val bodyW = 18 * s
            val bodyH = 11 * s
            g2.fill(RoundRectangle2D.Float(cx - 9 * s, cy - 0.5f * s, bodyW, bodyH, 3 * s, 3 * s))

            // 5. Arms (left & right rounded pills)
            val armW = 3.2f * s
            val armH = 8.5f * s
            g2.fill(RoundRectangle2D.Float(cx - 13.5f * s, cy, armW, armH, 2.5f * s, 2.5f * s))
            g2.fill(RoundRectangle2D.Float(cx + 10.3f * s, cy, armW, armH, 2.5f * s, 2.5f * s))

            // 6. Legs (bottom rounded pills)
            val legW = 3.2f * s
            val legH = 5 * s
            g2.fill(RoundRectangle2D.Float(cx - 5.5f * s, cy + 11 * s, legW, legH, 2 * s, 2 * s))
            g2.fill(RoundRectangle2D.Float(cx + 2.3f * s, cy + 11 * s, legW, legH, 2 * s, 2 * s))

            // 7. Question label ("Q1" .. "Q5") below Bugdroid
            g2.font = JBUI.Fonts.label(10f).deriveFont(Font.BOLD)
            val label = "Q${questionIndex + 1}"
            val fm    = g2.fontMetrics
            val tx    = (width - fm.stringWidth(label)) / 2
            val ty    = height - JBUI.scale(3)
            g2.color  = if (isSelected) color else com.intellij.ui.JBColor(Color(0x71717A), Color(0xA1A1AA))
            g2.drawString(label, tx, ty)
        } finally {
            g2.dispose()
        }
    }
}
