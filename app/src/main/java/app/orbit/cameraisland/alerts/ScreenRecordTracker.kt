package app.orbit.cameraisland.alerts

import android.content.Context
import app.orbit.cameraisland.activities.NotifData

/**
 * Pre-Android-15 fallback for screen-recording detection: the system screen recorder posts an ongoing
 * notification on its own channel. Best effort: depends on the OEM's channel naming.
 */
object ScreenRecordTracker {
    @Volatile var active = false
        private set
    @Volatile var start = 0L
        private set
    private var key: String? = null

    private val packages = setOf("com.android.systemui", "com.samsung.android.app.screenrecorder", "com.miui.screenrecorder")

    private fun matches(d: NotifData): Boolean {
        if (d.pkg !in packages) return false
        val ch = (d.n.channelId ?: "").lowercase()
        return ch.contains("screenrecord") || ch.contains("screen_record") || ch.contains("recording")
    }

    /** @return true if this notification belongs to the screen recorder (and should not become a peek). */
    fun posted(ctx: Context, d: NotifData): Boolean {
        if (!matches(d)) return false
        if (!active || key != d.key) {
            active = true; key = d.key
            start = if (d.whenMs > 0) d.whenMs else System.currentTimeMillis()
        }
        Alerts.indicator(ctx, AlertType.SCREEN_REC, true, "Recording screen", "", timerBase = start)
        return true
    }

    fun removed(ctx: Context, sbnKey: String) {
        if (key == sbnKey) {
            active = false; key = null
            Alerts.indicator(ctx, AlertType.SCREEN_REC, false)
        }
    }
}
