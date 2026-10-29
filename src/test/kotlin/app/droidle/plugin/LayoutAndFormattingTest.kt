package app.droidle.plugin

import app.droidle.plugin.model.DocLink
import app.droidle.plugin.model.Question
import app.droidle.plugin.ui.QuizScreen
import app.droidle.plugin.ui.TextFormatter
import app.droidle.plugin.ui.components.AutoWrappingHtmlLabel
import app.droidle.plugin.ui.components.ModernCodeSnippet
import app.droidle.plugin.ui.components.ModernExplanationCard
import app.droidle.plugin.ui.components.ModernOptionCard
import com.intellij.ui.components.JBScrollPane
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Dimension
import com.intellij.ui.components.JBTextArea
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JComponent
import javax.swing.JScrollPane

class LayoutAndFormattingTest {

    @Test
    fun `test mouse wheel propagation when inner scrollpane reaches bottom`() {
        val longCode = (1..30).joinToString("\n") { "val item$it = $it" }
        val snippet = ModernCodeSnippet(null, longCode)
        val outerBody = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(snippet)
            add(JPanel().apply { preferredSize = Dimension(400, 1000) })
        }
        val outerScrollPane = JBScrollPane(outerBody)
        val frame = JFrame().apply {
            contentPane = outerScrollPane
            setSize(400, 400)
            pack()
        }

        val innerScrollPane = snippet.getInnerScrollPane()!!
        assertTrue(innerScrollPane.verticalScrollBar.isVisible, "Inner vertical scrollbar should be visible for 30 lines")

        // Scroll inner scrollbar to minimum (top)
        innerScrollPane.verticalScrollBar.value = 0

        var outerReceivedWheelUp = false
        outerScrollPane.addMouseWheelListener {
            outerReceivedWheelUp = true
        }

        val wheelEventUp = java.awt.event.MouseWheelEvent(
            innerScrollPane,
            java.awt.event.MouseEvent.MOUSE_WHEEL,
            System.currentTimeMillis(),
            0,
            10, 10,
            1, false,
            java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
            3, -1 // scrolling up
        )

        innerScrollPane.dispatchEvent(wheelEventUp)
        println("At top: outerReceivedWheelUp: $outerReceivedWheelUp, isConsumed: ${wheelEventUp.isConsumed}")
        assertTrue(outerReceivedWheelUp)
    }

    @Test
    fun `test TextFormatter inline code formatting and HTML escaping`() {
        val raw = "Jetpack Compose'ta `Modifier.fillMaxWidth().padding(16.dp)` kullanımı `Modifier.padding(16.dp)` ile farkı?"
        val formatted = TextFormatter.formatInlineCode(raw)

        // Backticks should be converted to styled code font tags on background chips
        assertTrue(formatted.contains("<code><b>Modifier.fillMaxWidth().padding(16.dp)</b></code>"), "Should format first backtick term")
        assertTrue(formatted.contains("<code><b>Modifier.padding(16.dp)</b></code>"), "Should format second backtick term")
        assertTrue(formatted.contains("background-color:"), "Should contain styled background chip")
        assertFalse(formatted.contains("`"), "Raw backticks should not remain")

        // Generics & special characters should be safely escaped
        val codeWithGenerics = "What does `List<String>` return with &amp;?"
        val formattedGenerics = TextFormatter.formatInlineCode(codeWithGenerics)
        assertTrue(formattedGenerics.contains("&lt;String&gt;"), "Should escape < and > in code terms")

        // Line breaks should be converted to <br/>
        val multiline = "Line 1\nLine 2"
        assertEquals("Line 1<br/>Line 2", TextFormatter.formatInlineCode(multiline))

        // Empty/blank strings
        assertEquals("", TextFormatter.formatInlineCode(""))
        assertEquals("", TextFormatter.formatInlineCode("   "))
    }

    @Test
    fun `test AutoWrappingHtmlLabel dynamic height calculation for constrained width`() {
        val longText = "Jetpack Compose'ta `Modifier.fillMaxWidth().padding(16.dp)` kullanımı ile `Modifier.padding(16.dp).fillMaxWidth()` kullanımı arasındaki temel fark nedir?"
        val label = AutoWrappingHtmlLabel(TextFormatter.formatQuestionHtml(longText))

        // When inside a container with 400px width
        val container = JPanel().apply {
            setSize(400, 600)
            add(label)
        }

        val prefSize = label.preferredSize
        // Height must be dynamically wrapped (greater than single-line 20px)
        assertTrue(prefSize.height >= 40, "Wrapped height (${prefSize.height}) should be at least 40px for multi-line question at 400px width")
        assertTrue(label.alignmentX == Component.LEFT_ALIGNMENT)
    }

    @Test
    fun `test ModernOptionCard alignment and full width configuration`() {
        val card = ModernOptionCard("A", "Test `code` option with enough text to verify wrapping behavior across lines") {}

        // alignmentX must strictly be LEFT_ALIGNMENT (0.0f)
        assertEquals(Component.LEFT_ALIGNMENT, card.alignmentX, 0.001f)

        // maximumSize.width must expand to Int.MAX_VALUE for BoxLayout
        assertEquals(Int.MAX_VALUE, card.maximumSize.width)

        // Test updating text
        card.updateText("Updated `newText` option")
        assertEquals(Component.LEFT_ALIGNMENT, card.alignmentX, 0.001f)
    }

    @Test
    fun `test ModernExplanationCard alignment and children layout`() {
        val card = ModernExplanationCard()

        assertEquals(Component.LEFT_ALIGNMENT, card.alignmentX, 0.001f)
        assertEquals(Int.MAX_VALUE, card.maximumSize.width)

        // Show explanation and verify children alignment
        card.show(
            correct = true,
            text = "Compose `Modifier` explanation with multi-line technical rationale",
            earnedXp = 10,
            docLinks = listOf(DocLink(title = "Docs", url = "https://developer.android.com")),
            isEnglish = false
        )

        assertEquals(Component.LEFT_ALIGNMENT, card.alignmentX, 0.001f)

        // Every child component inside ModernExplanationCard must have LEFT_ALIGNMENT
        for (i in 0 until card.componentCount) {
            val comp = card.getComponent(i)
            if (comp is JComponent) {
                assertEquals(
                    Component.LEFT_ALIGNMENT,
                    comp.alignmentX,
                    0.001f,
                    "Child $i (${comp.javaClass.simpleName}) must have LEFT_ALIGNMENT to avoid BoxLayout displacement"
                )
            }
        }
    }

    @Test
    fun `test ModernCodeSnippet alignment, sizing and minimum bounds`() {
        val snippet = ModernCodeSnippet(null, "val x = 42")

        assertEquals(Component.LEFT_ALIGNMENT, snippet.alignmentX, 0.001f)
        assertEquals(Int.MAX_VALUE, snippet.maximumSize.width)
        assertTrue(snippet.minimumSize.width <= 150, "Snippet should be able to shrink cleanly in narrow tool windows")
    }

    @Test
    fun `test ModernCodeSnippet internal scrolling policies and horizontal scrolling on long lines`() {
        val longLineCode = "val greeting = \"" + "A".repeat(150) + "\"\nval y = 10"
        val snippet = ModernCodeSnippet(null, longLineCode)

        val scrollPane = snippet.getInnerScrollPane()
        assertNotNull(scrollPane, "ModernCodeSnippet should contain an inner scroll pane")
        assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED, scrollPane!!.horizontalScrollBarPolicy)
        assertEquals(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, scrollPane.verticalScrollBarPolicy)

        val textArea = scrollPane.viewport.view as JBTextArea
        assertFalse(textArea.lineWrap, "Line wrap should be disabled so long lines can scroll horizontally")
        assertFalse(textArea.isEditable, "Text area should be read-only")

        // View preferred width for 150+ chars is wide (> 500px)
        assertTrue(textArea.preferredSize.width > 500, "150-char line should have wide preferred width in monospace font")

        // But snippet's own preferred width remains compact (~200px) to prevent blowing up parent layout
        assertTrue(snippet.preferredSize.width < 400, "Snippet preferred width should stay compact to avoid breaking outer tool window layout")

        // When laid out inside a 300px wide frame, horizontal scrollbar is needed
        snippet.setSize(300, 150)
        snippet.doLayout()
        scrollPane.setSize(300, 120)
        scrollPane.doLayout()
        assertTrue(scrollPane.horizontalScrollBar.isVisible, "Horizontal scrollbar should be visible when code line exceeds width")
    }

    @Test
    fun `test ModernCodeSnippet vertical scroll threshold for long code snippets`() {
        val multilineCode = (1..25).joinToString("\n") { "val item$it = $it * 100" }
        val snippet = ModernCodeSnippet(null, multilineCode)

        val scrollPane = snippet.getInnerScrollPane()
        assertNotNull(scrollPane)
        val textArea = scrollPane!!.viewport.view as JBTextArea

        assertTrue(textArea.preferredSize.height > scrollPane.preferredSize.height, "Text area height (${textArea.preferredSize.height}) should exceed scrollPane height (${scrollPane.preferredSize.height})")

        // The snippet and scrollPane height should be capped at the threshold (<= 320px)
        assertTrue(scrollPane.preferredSize.height <= 320, "Inner scrollpane height (${scrollPane.preferredSize.height}) must be capped at reasonable threshold")
        assertTrue(snippet.preferredSize.height <= 360, "Snippet preferred height (${snippet.preferredSize.height}) must be capped to prevent pushing options offscreen")

        // When laid out at its preferred height, vertical scrollbar is needed
        snippet.setSize(350, snippet.preferredSize.height)
        snippet.doLayout()
        scrollPane.setSize(350, scrollPane.preferredSize.height)
        scrollPane.doLayout()
        assertTrue(scrollPane.verticalScrollBar.isVisible, "Vertical scrollbar should be visible when code lines exceed height threshold")
    }

    @Test
    fun `test ModernCodeSnippet blank code hides component and setCode updates view`() {
        val snippet = ModernCodeSnippet(null, "")
        assertFalse(snippet.isVisible, "Snippet with blank code should be hidden")

        snippet.setCode("val count = 1")
        assertTrue(snippet.isVisible, "Snippet should become visible after setCode with valid code")
        assertNotNull(snippet.getInnerScrollPane())

        snippet.setCode("   ")
        assertFalse(snippet.isVisible, "Snippet should hide when code is updated to blank")
    }

    @Test
    fun `test ModernCodeSnippet single line compact height and language support`() {
        val singleLine = "val isCompact = true"
        val snippet = ModernCodeSnippet(null, singleLine, "xml")

        // Preferred height for 1 line should be very compact (< 100px)
        assertTrue(snippet.preferredSize.height <= 90, "1-line snippet height (${snippet.preferredSize.height}) should be compact without excess padding")

        // Language tag should be set to xml
        val langLabel = (snippet.getComponent(0) as JPanel).components
            .filterIsInstance<JPanel>()
            .flatMap { it.components.toList() }
            .filterIsInstance<com.intellij.ui.components.JBLabel>()
            .firstOrNull { it.text == "xml" }
        assertNotNull(langLabel, "Language label should display 'xml'")

        // Updating with new language
        snippet.setCode("dependencies {\n    implementation(libs.androidx)\n}", "gradle")
        val updatedLangLabel = (snippet.getComponent(0) as JPanel).components
            .filterIsInstance<JPanel>()
            .flatMap { it.components.toList() }
            .filterIsInstance<com.intellij.ui.components.JBLabel>()
            .firstOrNull { it.text == "gradle" }
        assertNotNull(updatedLangLabel, "Language label should update to 'gradle'")

        // Verify paintComponent renders without exceptions
        val img = java.awt.image.BufferedImage(300, 100, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g2 = img.createGraphics()
        assertDoesNotThrow {
            snippet.setSize(300, 100)
            snippet.paint(g2)
        }
        g2.dispose()
    }



    @Test
    fun `test QuizScreen layout bounds and horizontal left alignment`() {
        val question = Question(
            id = "q1",
            question = "Jetpack Compose'ta `Modifier.fillMaxWidth().padding(16.dp)` kullanımı ile `Modifier.padding(16.dp).fillMaxWidth()` kullanımı arasındaki temel fark nedir?",
            options = mapOf(
                "A" to "fillMaxWidth().padding() önce tüm genişliği kaplar, ardından içeriye 16dp iç boşluk verir.",
                "B" to "Modifier sıralaması Jetpack Compose'ta düz bir yapı zinciri olduğu için her iki kullanım da tamamen aynı düzeni üretir.",
                "C" to "padding().fillMaxWidth() kullanımı derleme zamanı hatasına yol açar.",
                "D" to "padding() metodundan sonra fillMaxWidth() çağrıldığında padding sıfırlanır."
            ),
            correct_answer = "A",
            category = "Compose",
            difficulty = "Junior",
            explanation = "Compose içerisinde Modifier sıralaması kaskad çalışır."
        )

        val screen = QuizScreen(
            questions = listOf(question),
            project = null,
            onComplete = { _, _, _, _, _, _ -> },
            onError = {}
        )

        val frame = JFrame()
        frame.contentPane.add(screen)
        frame.setSize(450, 750)
        frame.doLayout()
        screen.setSize(450, 750)
        screen.doLayout()

        // Inspect body panel inside scrollPane
        val scrollPane = screen.components.filterIsInstance<JBScrollPane>().first()
        val viewport = scrollPane.viewport
        val bodyPanel = viewport.view as JPanel
        bodyPanel.setSize(450, bodyPanel.preferredSize.height)
        bodyPanel.doLayout()

        // Verify all bodyPanel children are left-aligned at x = insets.left
        val expectedLeftX = bodyPanel.insets.left
        val questionWrapper = bodyPanel.getComponent(0) as JPanel
        assertEquals(expectedLeftX, questionWrapper.x, "Question wrapper must start at left border inset")
        assertTrue(questionWrapper.width >= 400, "Question wrapper must fill available width")
        assertTrue(questionWrapper.height >= 35, "Question wrapper height (${questionWrapper.height}) must accommodate wrapped lines")

        // Verify option cards are left-aligned and fill width
        val optionsPanel = bodyPanel.getComponent(4) as JPanel
        println("optionsPanel bounds = ${optionsPanel.bounds}")
        println("optionsPanel prefSize = ${optionsPanel.preferredSize}")
        for (i in 0 until optionsPanel.componentCount) {
            val c = optionsPanel.getComponent(i)
            println("optionsPanel child $i: ${c.javaClass.simpleName} bounds=${c.bounds}, prefSize=${c.preferredSize}, alignX=${c.alignmentX}")
        }
        assertEquals(expectedLeftX, optionsPanel.x, "Options panel must start at left border inset")
        assertTrue(optionsPanel.width >= 400, "Options panel width was ${optionsPanel.width}, expected >= 400")
    }
}
