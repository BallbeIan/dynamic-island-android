package com.ian.island

import android.content.ComponentName
import android.content.res.Configuration
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ian.island.activities.MediaProvider
import com.ian.island.activities.NotificationEngine
import com.ian.island.island.IslandController

/**
 * Notification listener: the permission that lets us read media sessions and notifications.
 * It feeds the shared [IslandController]; if the accessibility service is off it also hosts the overlay
 * (as a plain application overlay).
 */
class IslandService : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: IslandService? = null
            private set
    }

    private var provider: MediaProvider? = null
    private var engine: NotificationEngine? = null
    private val component get() = ComponentName(this, IslandService::class.java)

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        val p = MediaProvider(this, component)
        provider = p
        engine = NotificationEngine(this)
        IslandController.media = p
        IslandController.attachListener(this)
        p.start()
        try { activeNotifications?.forEach { engine?.restored(it) } } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        teardown()
        requestRebind(component)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (instance === this) instance = null
        provider?.stop(); provider = null
        engine = null
        IslandController.detachListener(this)
    }

    /** Called from the settings screen after permissions change. */
    fun refresh() { provider?.refresh(); IslandController.recompute() }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        IslandController.onConfigChanged()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        engine?.posted(sbn, rankingMap)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        engine?.removed(sbn)
    }
}
