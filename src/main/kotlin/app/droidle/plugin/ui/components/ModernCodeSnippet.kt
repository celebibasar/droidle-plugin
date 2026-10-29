package app.droidle.plugin.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.CompoundBorder

/**
 * Modern code snippet viewer featuring genuine IntelliJ EditorTextField with
 * Kotlin syntax highlighting, read-only viewer mode, copy button, and elegant 1px rounded card frame.
 * Strictly aligned with Component.LEFT_ALIGNMENT and full horizontal width.
 */
class ModernCodeSnippet(
    private val project: Project?,
    codeText: String = "",
    initialLanguage: String? = null
) : JPanel(BorderLayout()) {

    private var currentCode: String = codeText.trim()
    private var currentLanguage: String = initialLanguage?.lowercase() ?: "kotlin"
    private val codeContainer = JPanel(BorderLayout()).apply {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
    }
    private val copyButton: JButton
    private val langLabel: JBLabel

    init {
        isOpaque = false
        alignmentX = Component.LEFT_ALIGNMENT
        border = CompoundBorder(
            RoundedLineBorder(JBColor(Color(0xE4E4E7), Color(0x323438)), JBUI.scale(10)),
            JBUI.Borders.empty(JBUI.scale(4), JBUI.scale(6), JBUI.scale(6), JBUI.scale(6))
        )

        // Top bar
        val topBar = JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            border = JBUI.Borders.empty(JBUI.scale(2), JBUI.scale(4), JBUI.scale(4), JBUI.scale(4))
        }

        langLabel = JBLabel(currentLanguage).apply {
            font = Font(Font.MONOSPACED, Font.BOLD, JBUI.scaleFontSize(10f).toInt())
            foreground = JBUI.CurrentTheme.Label.disabledForeground()
        }

        val langTag = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            val dot = object : JComponent() {
                init {
                    preferredSize = Dimension(JBUI.scale(6), JBUI.scale(6))
                    alignmentX = Component.LEFT_ALIGNMENT
                }
                override fun paintComponent(g: Graphics) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = Color(0x7F, 0x52, 0xFF) // Kotlin purple
                    g2.fillOval(0, 0, width, height)
                }
            }
            add(dot)
            add(langLabel)
        }

        copyButton = JButton(AllIcons.Actions.Copy).apply {
            toolTipText = "Kodu Kopyala"
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { copyToClipboard() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent?) { isContentAreaFilled = true }
                override fun mouseExited(e: MouseEvent?) { isContentAreaFilled = false }
            })
        }

        topBar.add(langTag, BorderLayout.WEST)
        topBar.add(copyButton, BorderLayout.EAST)

        add(topBar, BorderLayout.NORTH)
        add(codeContainer, BorderLayout.CENTER)

        setCode(codeText, initialLanguage)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = JBColor(Color(0xF8F9FA), Color(0x1E1F22))
            g2.fillRoundRect(0, 0, width, height, JBUI.scale(10), JBUI.scale(10))
        } finally {
            g2.dispose()
        }
        super.paintComponent(g)
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun getMinimumSize(): Dimension = Dimension(JBUI.scale(100), preferredSize.height)

    fun getInnerScrollPane(): JScrollPane? {
        fun find(c: Container): JScrollPane? {
            if (c is JScrollPane) return c
            for (child in c.components) {
                if (child is JScrollPane) return child
                if (child is Container) {
                    val found = find(child)
                    if (found != null) return found
                }
            }
            return null
        }
        return find(codeContainer)
    }

    fun setCode(newCode: String, language: String? = null) {
        currentCode = newCode.trim()
        if (language != null) {
            currentLanguage = language.lowercase()
            langLabel.text = currentLanguage
        }
        codeContainer.removeAll()

        if (currentCode.isBlank()) {
            isVisible = false
            return
        }
        isVisible = true

        val editorComponent = createEditorOrFallback(currentCode)
        codeContainer.add(editorComponent, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun createEditorOrFallback(code: String): JComponent {
        val lineCount = code.lines().size.coerceIn(1, 12)
        val estimatedH = JBUI.scale(lineCount * 20 + 20)

        if (project != null && !project.isDisposed) {
            try {
                val ext = when (currentLanguage) {
                    "kotlin", "kt" -> "kt"
                    "java" -> "java"
                    "xml" -> "xml"
                    "json" -> "json"
                    "groovy", "gradle" -> "gradle"
                    else -> "kt"
                }
                val fileType = FileTypeManager.getInstance().getFileTypeByExtension(ext)
                val editorField = object : EditorTextField(code, project, fileType) {
                    override fun createEditor(): EditorEx {
                        val editor = super.createEditor()
                        editor.settings.isLineNumbersShown = true
                        editor.settings.isFoldingOutlineShown = false
                        editor.settings.isAutoCodeFoldingEnabled = false
                        editor.settings.isRightMarginShown = false
                        editor.settings.isWhitespacesShown = false
                        editor.settings.isIndentGuidesShown = false
                        editor.settings.isUseSoftWraps = false
                        editor.isViewer = true
                        editor.backgroundColor = JBColor(Color(0xF8F9FA), Color(0x1E1F22))
                        editor.colorsScheme.editorFontSize = JBUI.scaleFontSize(11f).toInt()
                        editor.setBorder(JBUI.Borders.empty(4, 6))

                        editor.setHorizontalScrollbarVisible(true)
                        editor.setVerticalScrollbarVisible(true)
                        editor.scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                        editor.scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                        editor.scrollPane.border = JBUI.Borders.empty()
                        editor.scrollPane.viewportBorder = JBUI.Borders.empty()
                        return editor
                    }
                }
                editorField.addSettingsProvider { editor ->
                    editor.setHorizontalScrollbarVisible(true)
                    editor.setVerticalScrollbarVisible(true)
                    editor.scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                    editor.scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
                    editor.settings.isUseSoftWraps = false
                    editor.scrollPane.border = JBUI.Borders.empty()
                    editor.scrollPane.viewportBorder = JBUI.Borders.empty()
                }
                editorField.isViewer = true
                editorField.setOneLineMode(false)
                editorField.alignmentX = Component.LEFT_ALIGNMENT
                editorField.font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11f).toInt())
                editorField.preferredSize = Dimension(JBUI.scale(200), estimatedH)
                editorField.maximumSize = Dimension(Int.MAX_VALUE, estimatedH)
                return editorField
            } catch (_: Throwable) {
                // Fallback below
            }
        }

        // Monospace fallback view
        val textArea = JBTextArea(code).apply {
            isEditable = false
            lineWrap = false
            wrapStyleWord = false
            alignmentX = Component.LEFT_ALIGNMENT
            font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scaleFontSize(11f).toInt())
            background = JBColor(Color(0xF8F9FA), Color(0x1E1F22))
            foreground = JBColor(Color(0x24292F), Color(0xC9D1D9))
            border = JBUI.Borders.empty(JBUI.scale(6), JBUI.scale(8))
        }

        return JBScrollPane(textArea).apply {
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
            background = JBColor(Color(0xF8F9FA), Color(0x1E1F22))
            viewport.background = JBColor(Color(0xF8F9FA), Color(0x1E1F22))
            alignmentX = Component.LEFT_ALIGNMENT
            preferredSize = Dimension(JBUI.scale(200), estimatedH)
            maximumSize = Dimension(Int.MAX_VALUE, estimatedH)
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            verticalScrollBar.unitIncrement = JBUI.scale(16)
            horizontalScrollBar.unitIncrement = JBUI.scale(16)
        }
    }

    private fun copyToClipboard() {
        if (currentCode.isNotBlank()) {
            CopyPasteManager.getInstance().setContents(StringSelection(currentCode))
            copyButton.toolTipText = "Kopyalandı!"
            Timer(1500) { copyButton.toolTipText = "Kodu Kopyala" }.apply {
                isRepeats = false
                start()
            }
        }
    }
}
