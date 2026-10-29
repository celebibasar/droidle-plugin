package app.droidle.plugin.ui.components

import app.droidle.plugin.model.DocLink
import app.droidle.plugin.ui.TextFormatter
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

/**
 * Modern explanation card with status icon, XP chip, "TEKNİK AÇIKLAMA" header tag,
 * syntax-highlighted technical explanation text, and "Resmi Dokümana Git" button.
 * Strictly aligned with Component.LEFT_ALIGNMENT and full horizontal width to prevent layout drift.
 */
class ModernExplanationCard : JPanel() {

    private var isCorrect: Boolean = true
    private val statusIcon = JBLabel().apply {
        alignmentX = Component.LEFT_ALIGNMENT
    }
    private val statusTitle = JBLabel("").apply {
        font = JBUI.Fonts.label(13f).deriveFont(Font.BOLD)
        alignmentX = Component.LEFT_ALIGNMENT
    }
    private val xpChip = XpPill()

    private val sectionHeader = JBLabel("💡 TEKNİK AÇIKLAMA").apply {
        font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
        foreground = JBUI.CurrentTheme.Label.disabledForeground()
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val explanationText = AutoWrappingHtmlLabel("").apply {
        font = JBUI.Fonts.label(12.5f)
        horizontalAlignment = SwingConstants.LEFT
        verticalAlignment = SwingConstants.TOP
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private val linksPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(JBUI.scale(12), JBUI.scale(14))
        alignmentX = Component.LEFT_ALIGNMENT

        // Header row
        val headerRow = object : JPanel(BorderLayout(JBUI.scale(8), 0)) {
            override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)
        }.apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT

            val left = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)).apply {
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT
                add(statusIcon)
                add(statusTitle)
            }
            add(left, BorderLayout.WEST)
            add(xpChip, BorderLayout.EAST)
        }

        add(headerRow)
        add(vGap(8))
        add(sectionHeader)
        add(vGap(6))
        add(explanationText)
        add(vGap(8))
        add(linksPanel)
    }

    private fun vGap(h: Int): Component = Box.createRigidArea(Dimension(0, JBUI.scale(h))).apply {
        if (this is JComponent) alignmentX = Component.LEFT_ALIGNMENT
    }

    private var currentOnReportClick: (() -> Unit)? = null
    private var currentIsEnglish: Boolean = false
    private var isReported: Boolean = false

    fun refreshReportedState(reported: Boolean) {
        isReported = reported
        updateReportPill()
    }

    private var reportPillComponent: JComponent? = null

    private fun updateReportPill() {
        val onReport = currentOnReportClick ?: return
        reportPillComponent?.let { linksPanel.remove(it) }

        val pill = ReportLinkPill(
            text = if (isReported) (if (currentIsEnglish) "✓ Reported" else "✓ Bildirildi")
                   else (if (currentIsEnglish) "Report Question ⚑" else "Soruyu Bildir ⚑"),
            isReported = isReported,
            onClick = {
                if (!isReported) {
                    onReport.invoke()
                }
            }
        )
        reportPillComponent = pill
        linksPanel.add(pill)
        linksPanel.isVisible = true
        linksPanel.revalidate()
        linksPanel.repaint()
    }

    fun show(
        correct: Boolean,
        text: String,
        earnedXp: Int = 0,
        docLinks: List<DocLink> = emptyList(),
        isEnglish: Boolean = false,
        isReported: Boolean = false,
        onReportClick: (() -> Unit)? = null
    ) {
        isCorrect = correct
        this.currentIsEnglish = isEnglish
        this.currentOnReportClick = onReportClick
        this.isReported = isReported

        statusIcon.icon = if (correct) AllIcons.General.InspectionsOK else AllIcons.General.BalloonError
        statusTitle.text = if (correct) {
            if (isEnglish) "Correct Answer!" else "Doğru Cevap!"
        } else {
            if (isEnglish) "Incorrect Answer" else "Yanlış Cevap"
        }
        statusTitle.foreground = if (correct)
            JBColor(Color(0x065F46), Color(0x6EE7B7))
        else
            JBColor(Color(0x9F1239), Color(0xFDA4AF))

        sectionHeader.text = if (isEnglish) "💡 TECHNICAL EXPLANATION" else "💡 TEKNİK AÇIKLAMA"

        xpChip.update(earnedXp, correct)

        explanationText.text = TextFormatter.formatExplanationHtml(text)
        explanationText.foreground = JBUI.CurrentTheme.Label.foreground()

        linksPanel.removeAll()
        reportPillComponent = null

        if (docLinks.isNotEmpty()) {
            docLinks.forEach { link ->
                val linkLabel = if (isEnglish) "Official Documentation ↗" else "Resmi Dokümana Git ↗"
                val btn = DocLinkPill(linkLabel, link.url)
                linksPanel.add(btn)
            }
        }

        if (onReportClick != null) {
            updateReportPill()
        } else {
            linksPanel.isVisible = linksPanel.componentCount > 0
        }

        isVisible = true
        revalidate()
        repaint()
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val arc = JBUI.scale(12)

            val bg = if (isCorrect)
                JBColor(Color(0xECFDF5), Color(0x132A22))
            else
                JBColor(Color(0xFFF1F2), Color(0x2D181E))
            g2.color = bg
            g2.fillRoundRect(0, 0, width, height, arc, arc)

            val borderCol = if (isCorrect)
                JBColor(Color(0x10B981), Color(0x059669))
            else
                JBColor(Color(0xF43F5E), Color(0xBE123C))
            g2.stroke = BasicStroke(JBUI.scale(1).toFloat() * 1.2f)
            g2.color = borderCol
            g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)

            // Prominent 3px left accent bar
            val accentCol = if (isCorrect) Color(0x10, 0xB9, 0x81) else Color(0xF4, 0x3F, 0x5E)
            g2.color = accentCol
            val barW = JBUI.scale(4)
            g2.fillRoundRect(0, JBUI.scale(4), barW, height - JBUI.scale(8), barW, barW)
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }

    private class DocLinkPill(text: String, url: String) : JButton(text) {
        private var hovered = false

        init {
            font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = url
            foreground = JBColor(Color(0x2563EB), Color(0x60A5FA))
            border = JBUI.Borders.empty(JBUI.scale(4), JBUI.scale(10))
            alignmentX = Component.LEFT_ALIGNMENT
            addActionListener { BrowserUtil.browse(url) }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?) { hovered = true; repaint() }
                override fun mouseExited(e: MouseEvent?) { hovered = false; repaint() }
            })
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val blue = Color(0x25, 0x63, 0xEB)
                val bgAlpha = if (hovered) 40 else 20
                g2.color = Color(blue.red, blue.green, blue.blue, bgAlpha)
                g2.fillRoundRect(0, 0, width, height, height, height)

                g2.color = if (hovered) JBColor(Color(0x1D4ED8), Color(0x93C5FD)) else JBColor(Color(0x2563EB), Color(0x60A5FA))
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, height, height)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }

    private class ReportLinkPill(
        text: String,
        private val isReported: Boolean,
        onClick: () -> Unit
    ) : JButton(text) {
        private var hovered = false

        init {
            font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            cursor = if (isReported) Cursor.getDefaultCursor() else Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = if (isReported) "Bu soru için bildirim gönderildi" else "Soruyu bildir"
            foreground = if (isReported) JBColor(Color(0x059669), Color(0x34D399))
                         else JBColor(Color(0xF43F5E), Color(0xFB7185))
            border = JBUI.Borders.empty(JBUI.scale(4), JBUI.scale(10))
            alignmentX = Component.LEFT_ALIGNMENT
            addActionListener { onClick() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?) {
                    if (!isReported) {
                        hovered = true
                        repaint()
                    }
                }
                override fun mouseExited(e: MouseEvent?) {
                    if (!isReported) {
                        hovered = false
                        repaint()
                    }
                }
            })
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val baseCol = if (isReported) Color(0x10, 0xB9, 0x81) else Color(0xF4, 0x3F, 0x5E)
                val bgAlpha = if (isReported) 25 else (if (hovered) 35 else 18)
                g2.color = Color(baseCol.red, baseCol.green, baseCol.blue, bgAlpha)
                g2.fillRoundRect(0, 0, width, height, height, height)

                g2.color = if (isReported) Color(0x10, 0xB9, 0x81, 120) else Color(baseCol.red, baseCol.green, baseCol.blue, if (hovered) 120 else 60)
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, height, height)
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }

    private class XpPill : JPanel() {
        private var xp: Int = 0
        private var isCorrect: Boolean = false

        init {
            isOpaque = false
        }

        fun update(newXp: Int, correct: Boolean) {
            xp = newXp
            isCorrect = correct
            val text = if (correct) "+$xp XP" else "0 XP"
            val fm = getFontMetrics(JBUI.Fonts.smallFont().deriveFont(Font.BOLD))
            preferredSize = Dimension(fm.stringWidth(text) + JBUI.scale(16), JBUI.scale(20))
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

                val col = if (isCorrect) Color(0x10, 0xB9, 0x81) else Color(0xF4, 0x3F, 0x5E)
                val text = if (isCorrect) "+$xp XP" else "0 XP"
                val arc = height

                g2.color = Color(col.red, col.green, col.blue, 30)
                g2.fillRoundRect(0, 0, width, height, arc, arc)

                g2.color = col
                g2.stroke = BasicStroke(1f)
                g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc)

                g2.font = JBUI.Fonts.smallFont().deriveFont(Font.BOLD)
                val fm = g2.fontMetrics
                val tx = (width - fm.stringWidth(text)) / 2
                val ty = (height + fm.ascent - fm.descent) / 2
                g2.drawString(text, tx, ty)
            } finally {
                g2.dispose()
            }
        }
    }
}
