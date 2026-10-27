package app.droidle.plugin.auth

import com.intellij.util.messages.Topic

/**
 * MessageBus topic for auth state changes.
 * Subscribers (e.g. toolbar) can listen and refresh themselves.
 */
fun interface DroidleAuthListener {
    fun onAuthChanged()

    companion object {
        @JvmField
        val TOPIC: Topic<DroidleAuthListener> =
            Topic.create("Droidle Auth Changed", DroidleAuthListener::class.java)
    }
}
