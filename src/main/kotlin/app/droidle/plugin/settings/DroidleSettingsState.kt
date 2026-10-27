package app.droidle.plugin.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(name = "DroidleSettings", storages = [Storage("droidle.xml")])
class DroidleSettingsState : PersistentStateComponent<DroidleSettingsState> {
    var apiBaseUrl: String = "https://droidle.app"
    var showBadgeOnIncomplete: Boolean = true

    override fun getState(): DroidleSettingsState = this
    override fun loadState(state: DroidleSettingsState) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        fun getInstance(): DroidleSettingsState = service()
    }
}
