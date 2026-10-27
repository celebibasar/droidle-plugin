package app.droidle.plugin.ui

import app.droidle.plugin.model.Question
import app.droidle.plugin.model.VerifyResponse
import app.droidle.plugin.service.DroidleService
import app.droidle.plugin.state.StateManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class QuizPanel(private val onComplete: (correctCount: Int, questions: List<Question>) -> Unit) : JPanel() {

    private val questions = mutableListOf<Question>()
    private var currentIndex = 0
    private var correctCount = 0

    private val progressLabel = JBLabel("Yükleniyor...")
    private val questionLabel = JBLabel("", SwingConstants.LEFT).apply {
        font = font.deriveFont(Font.BOLD, 13f)
    }
    private val optionGroup = ButtonGroup()
    private val optionButtons = mutableListOf<JRadioButton>()
    private val explanationArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = UIManager.getColor("Panel.background")
        font = font.deriveFont(11f)
        isVisible = false
    }
    private val nextButton = JButton("Sonraki Soru →").apply {
        isEnabled = false
        isVisible = false
    }
    private val errorLabel = JBLabel("").apply {
        foreground = JBColor.RED
        isVisible = false
    }
    private val loadingLabel = JBLabel("Sorular yükleniyor...", SwingConstants.CENTER)
    private var contentScrollPane: JBScrollPane = JBScrollPane()

    init {
        layout = BorderLayout(0, 8)
        border = JBUI.Borders.empty(12)

        val bottomPanel = JPanel(BorderLayout(0, 8)).apply {
            add(errorLabel, BorderLayout.NORTH)
            add(JBScrollPane(explanationArea), BorderLayout.CENTER)
            add(nextButton, BorderLayout.SOUTH)
        }

        add(progressLabel, BorderLayout.NORTH)
        add(loadingLabel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)

        nextButton.addActionListener { showQuestion(currentIndex + 1) }
        loadQuestions()
    }

    private fun loadQuestions() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val fetched = DroidleService.getInstance().fetchDailyQuiz()
                SwingUtilities.invokeLater {
                    questions.addAll(fetched)
                    remove(loadingLabel)
                    showQuestion(0)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater { showError("Bağlantı hatası: ${e.message}") }
            }
        }
    }

    private fun showQuestion(index: Int) {
        if (index >= questions.size) {
            StateManager.getInstance().recordQuizCompletion(correctCount, questions.size)
            onComplete(correctCount, questions)
            return
        }

        currentIndex = index
        val q = questions[index]

        progressLabel.text = "Soru ${index + 1} / ${questions.size}  ·  ${q.category}"

        optionGroup.clearSelection()
        optionButtons.clear()

        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4)
            add(JBLabel("<html><b>${q.question}</b></html>").apply {
                alignmentX = Component.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(12))
        }

        q.options.forEach { option ->
            val btn = JRadioButton("<html>$option</html>").apply {
                alignmentX = Component.LEFT_ALIGNMENT
                background = UIManager.getColor("Panel.background")
            }
            optionGroup.add(btn)
            optionButtons.add(btn)
            contentPanel.add(btn)
            contentPanel.add(Box.createVerticalStrut(4))
            btn.addActionListener { submitAnswer(option, q) }
        }

        explanationArea.isVisible = false
        nextButton.isVisible = false
        nextButton.isEnabled = false
        errorLabel.isVisible = false

        remove(contentScrollPane)
        contentScrollPane = JBScrollPane(contentPanel)
        add(contentScrollPane, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun submitAnswer(selected: String, question: Question) {
        optionButtons.forEach { it.isEnabled = false }
        nextButton.text = if (currentIndex < questions.size - 1) "Sonraki Soru →" else "Sonuçları Gör"

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = DroidleService.getInstance().verifyAnswer(question.id, selected)
                SwingUtilities.invokeLater { showResult(result, selected) }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError("Cevap gönderilemedi: ${e.message}")
                    optionButtons.forEach { it.isEnabled = true }
                }
            }
        }
    }

    private fun showResult(result: VerifyResponse, selectedOption: String) {
        if (result.isCorrect) correctCount++

        optionButtons.forEach { btn ->
            val rawText = btn.text.replace("<html>", "").replace("</html>", "")
            when {
                rawText.contains(result.correctAnswer) -> {
                    btn.foreground = JBColor(Color(0, 140, 0), Color(80, 200, 80))
                    btn.font = btn.font.deriveFont(Font.BOLD)
                }
                !result.isCorrect && rawText.contains(selectedOption) -> {
                    btn.foreground = JBColor(Color(180, 0, 0), Color(220, 80, 80))
                }
            }
        }

        val explanation = result.explanation_tr ?: result.explanation
        explanationArea.text = if (result.isCorrect) "✓  $explanation" else "✗  $explanation"
        explanationArea.isVisible = true
        nextButton.isEnabled = true
        nextButton.isVisible = true
    }

    private fun showError(msg: String) {
        errorLabel.text = "<html>$msg</html>"
        errorLabel.isVisible = true
    }
}
