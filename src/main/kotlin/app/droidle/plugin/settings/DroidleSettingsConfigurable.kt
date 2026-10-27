package app.droidle.plugin.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class DroidleSettingsConfigurable : Configurable {

    private val apiUrlField = JBTextField()
    private val tokenField = JBPasswordField()
    private val bypassField = JBPasswordField()
    private val badgeCheckbox = JCheckBox("Tamamlanmamış quiz için rozet göster")

    override fun getDisplayName() = "Droidle"

    override fun createComponent(): JComponent {
        val state = DroidleSettingsState.getInstance()
        apiUrlField.text = state.apiBaseUrl
        badgeCheckbox.isSelected = state.showBadgeOnIncomplete
        bypassField.text = state.vercelBypassSecret

        // GitHub token'ı PasswordSafe'den oku
        val sm = service<app.droidle.plugin.state.StateManager>()
        tokenField.text = sm.getAuthToken() ?: ""

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("API URL:"), apiUrlField)
            .addLabeledComponent(JBLabel("Vercel Bypass Secret:"), bypassField)
            .addTooltip("Vercel Deployment Protection aktifse buraya bypass secret gir")
            .addLabeledComponent(JBLabel("GitHub Token (opsiyonel):"), tokenField)
            .addComponent(badgeCheckbox)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val state = DroidleSettingsState.getInstance()
        val sm = service<app.droidle.plugin.state.StateManager>()
        return apiUrlField.text != state.apiBaseUrl ||
                badgeCheckbox.isSelected != state.showBadgeOnIncomplete ||
                String(bypassField.password) != state.vercelBypassSecret ||
                String(tokenField.password) != (sm.getAuthToken() ?: "")
    }

    override fun apply() {
        val state = DroidleSettingsState.getInstance()
        state.apiBaseUrl = apiUrlField.text.trimEnd('/')
        state.showBadgeOnIncomplete = badgeCheckbox.isSelected
        state.vercelBypassSecret = String(bypassField.password)

        val sm = service<app.droidle.plugin.state.StateManager>()
        val token = String(tokenField.password)
        if (token.isBlank()) sm.clearAuthToken() else sm.saveAuthToken(token)
    }

    override fun reset() {
        val state = DroidleSettingsState.getInstance()
        apiUrlField.text = state.apiBaseUrl
        badgeCheckbox.isSelected = state.showBadgeOnIncomplete
        bypassField.text = state.vercelBypassSecret
        val sm = service<app.droidle.plugin.state.StateManager>()
        tokenField.text = sm.getAuthToken() ?: ""
    }
}
