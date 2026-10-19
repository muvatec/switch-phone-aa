package com.muvatec.switchphone

import android.content.ComponentName
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.content.Context

/** Quick Settings tile: one tap to make this phone the one used by the car. */
class SwitchTileService : TileService() {
    override fun onTileAdded() { Prefs.setTileAdded(this, true) }
    override fun onTileRemoved() { Prefs.setTileAdded(this, false) }
    override fun onStartListening() { Prefs.setTileAdded(this, true); update() }

    override fun onClick() {
        if (!Presence.selfProjecting) PresenceService.requestHere(this)
        update()
    }

    private fun update() {
        val t = qsTile ?: return
        val active = Presence.selfProjecting
        t.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Switch Phone"
        if (android.os.Build.VERSION.SDK_INT >= 29) t.subtitle = if (active) "Activo en el carro" else "Usar este celular"
        t.updateTile()
    }

    companion object {
        fun refresh(ctx: Context) = requestListeningState(ctx, ComponentName(ctx, SwitchTileService::class.java))
    }
}
