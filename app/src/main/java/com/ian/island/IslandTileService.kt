package com.ian.island

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ian.island.settings.Prefs

/** Quick Settings tile: tap toggles the island, long-press opens Island's settings (QS_TILE_PREFERENCES). */
class IslandTileService : TileService() {

    override fun onStartListening() { refresh() }

    override fun onClick() {
        val p = Prefs.get(this)
        p.edit().putBoolean(Prefs.ENABLED, !p.getBoolean(Prefs.ENABLED, true)).apply()
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        val on = Prefs.get(this).getBoolean(Prefs.ENABLED, true)
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = getString(R.string.app_name)
        t.updateTile()
    }
}
