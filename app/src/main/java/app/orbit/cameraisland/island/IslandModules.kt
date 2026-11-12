package app.orbit.cameraisland.island

import android.content.Context
import app.orbit.cameraisland.activities.BatteryProvider
import app.orbit.cameraisland.alerts.SystemAlerts

/** The set of self-contained modules the controller runs while any service is connected. */
object IslandModules {
    fun create(ctx: Context): List<IslandModule> = listOf(
        BatteryProvider(ctx),
        SystemAlerts(ctx),
    )
}
