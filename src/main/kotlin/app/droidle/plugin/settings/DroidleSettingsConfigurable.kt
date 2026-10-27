package app.droidle.plugin.settings

import app.droidle.plugin.state.StateManager
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class DroidleSettingsConfigurable : Configurable {

    private val apiUrlField = JBTextField()
    private val tokenField = JBPasswordField()
    private val badgeCheckbox = JBCheckBox("Quiz tamamlanmadıysa Tool Window'da rozet göster")
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Droidle"

    override fun createComponent(): JComponent {
        val state = DroidleSettingsState.getInstance()
        apiUrlField.text = state.apiBaseUrl
        badgeCheckbox.isSelected = state.showBadgeOnIncomplete
        StateManager.getInstance().getAuthToken()?.let { tokenField.text = it }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("API Base URL:"), apiUrlField)
            .addLabeledComponent(JBLabel("GitHub Token (opsiyonel):"), tokenField)
            .addComponent(badgeCheckbox)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return panel!!
    }

    override fun isModified(): Boolean {
        val state = DroidleSettingsState.getInstance()
        return apiUrlField.text != state.apiBaseUrl ||
               badgeCheckbox.isSelected != state.showBadgeOnIncomplete
    }

    override fun apply() {
        val state = DroidleSettingsState.getInstance()
        state.apiBaseUrl = apiUrlField.text.trimEnd('/')
        state.showBadgeOnIncomplete = badgeCheckbox.isSelected

        val newToken = String(tokenField.password).trim()
        if (newToken.isNotEmpty()) StateManager.getInstance().saveAuthToken(newToken)
        else StateManager.getInstance().clearAuthToken()
    }

    override fun reset() {
        val state = DroidleSettingsState.getInstance()
        apiUrlField.text = state.apiBaseUrl
        badgeCheckbox.isSelected = state.showBadgeOnIncomplete
    }
}
