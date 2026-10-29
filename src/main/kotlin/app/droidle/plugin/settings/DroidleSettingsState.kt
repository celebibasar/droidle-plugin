package app.droidle.plugin.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(name = "DroidleSettings", storages = [Storage("droidle.xml")])
class DroidleSettingsState : PersistentStateComponent<DroidleSettingsState> {
    var apiBaseUrl: String = "https://droidle.dev"
    var showBadgeOnIncomplete: Boolean = true
    /** Vercel Deployment Protection bypass secret (opsiyonel) */
    var vercelBypassSecret: String = ""
    var supabaseUrl: String = "https://qgayxrjogzoikagdbuyv.supabase.co"
    var supabaseAnonKey: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFnYXl4cmpvZ3pvaWthZ2RidXl2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTI0OTU4NDcsImV4cCI6MjEwODA3MTg0N30.CVOAY6eOXVzpSodvtbMaOyIvBupRxRkTVNYCo97PHB8"

    override fun getState(): DroidleSettingsState = this
    override fun loadState(state: DroidleSettingsState) = XmlSerializerUtil.copyBean(state, this)

    companion object {
        fun getInstance(): DroidleSettingsState = service()
    }
}
