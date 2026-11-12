package app.orbit.cameraisland

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

/**
 * After boot or an app update, ask Android to rebind the notification listener (the accessibility
 * service is restarted by the system on its own). The services re-initialise the island themselves.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            try {
                NotificationListenerService.requestRebind(ComponentName(context, IslandService::class.java))
            } catch (_: Exception) {}
        }
    }
}
