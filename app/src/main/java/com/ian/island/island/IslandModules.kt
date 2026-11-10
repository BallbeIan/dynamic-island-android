package com.ian.island.island

import android.content.Context
import com.ian.island.activities.BatteryProvider
import com.ian.island.alerts.SystemAlerts

/** The set of self-contained modules the controller runs while any service is connected. */
object IslandModules {
    fun create(ctx: Context): List<IslandModule> = listOf(
        BatteryProvider(ctx),
        SystemAlerts(ctx),
    )
}
