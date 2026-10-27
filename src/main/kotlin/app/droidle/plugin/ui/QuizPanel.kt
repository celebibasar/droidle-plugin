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

    // Geçerli soru için key→button eşlemesi
    private val optionKeyMap = mutableMapOf<JRadioButton, String>()

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
        optionKeyMap.clear()

        val displayOptions = q.displayOptions()   // TR öncelikli { A→metin, B→metin, ... }

        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(4)
            add(JBLabel("<html><b>${q.displayQuestion()}</b></html>").apply {
                alignmentX = Component.LEFT_ALIGNMENT
            })
            add(Box.createVerticalStrut(12))
        }

        // Sıralı A, B, C, D
        listOf("A", "B", "C", "D").forEach { key ->
            val text = displayOptions[key] ?: return@forEach
            val btn = JRadioButton("<html><b>$key.</b> $text</html>").apply {
                alignmentX = Component.LEFT_ALIGNMENT
                background = UIManager.getColor("Panel.background")
            }
            optionGroup.add(btn)
            optionButtons.add(btn)
            optionKeyMap[btn] = key
            contentPanel.add(btn)
            contentPanel.add(Box.createVerticalStrut(4))
            btn.addActionListener { submitAnswer(key, q) }
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

    private fun submitAnswer(selectedKey: String, question: Question) {
        optionButtons.forEach { it.isEnabled = false }
        nextButton.text = if (currentIndex < questions.size - 1) "Sonraki Soru →" else "Sonuçları Gör"

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = DroidleService.getInstance().verifyAnswer(question.id, selectedKey)
                SwingUtilities.invokeLater { showResult(result, selectedKey) }
            } catch (e: Exception) {
                // verify-answer başarısız olsa bile client-side doğrulama yap
                val correctKey = question.correct_answer
                val isCorrect = correctKey != null && selectedKey.equals(correctKey, ignoreCase = true)
                SwingUtilities.invokeLater {
                    showLocalResult(isCorrect, correctKey ?: selectedKey, question)
                }
            }
        }
    }

    private fun showResult(result: VerifyResponse, selectedKey: String) {
        if (result.isCorrect) correctCount++
        highlightOptions(result.correctAnswer, selectedKey)
        val explanation = result.explanation_tr ?: result.explanation
        explanationArea.text = if (result.isCorrect) "✓  $explanation" else "✗  $explanation"
        explanationArea.isVisible = true
        nextButton.isEnabled = true
        nextButton.isVisible = true
    }

    /** API erişimi olmadığında correct_answer alanından yerel değerlendirme */
    private fun showLocalResult(isCorrect: Boolean, correctKey: String, question: Question) {
        if (isCorrect) correctCount++
        highlightOptions(correctKey, if (isCorrect) correctKey else "")
        val explanation = question.explanation_tr ?: question.explanation ?: ""
        explanationArea.text = if (isCorrect) "✓  $explanation" else "✗  Doğru cevap: $correctKey\n$explanation"
        explanationArea.isVisible = true
        nextButton.isEnabled = true
        nextButton.isVisible = true
    }

    private fun highlightOptions(correctKey: String, selectedKey: String) {
        optionButtons.forEach { btn ->
            val key = optionKeyMap[btn] ?: return@forEach
            when {
                key.equals(correctKey, ignoreCase = true) -> {
                    btn.foreground = JBColor(Color(0, 140, 0), Color(80, 200, 80))
                    btn.font = btn.font.deriveFont(Font.BOLD)
                }
                key.equals(selectedKey, ignoreCase = true) && !key.equals(correctKey, ignoreCase = true) -> {
                    btn.foreground = JBColor(Color(180, 0, 0), Color(220, 80, 80))
                }
            }
        }
    }

    private fun showError(msg: String) {
        errorLabel.text = "<html>$msg</html>"
        errorLabel.isVisible = true
    }
}
