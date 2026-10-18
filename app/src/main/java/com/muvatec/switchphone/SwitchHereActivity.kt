package com.muvatec.switchphone

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/** Icon shortcut ("Usar este celular"): no UI, just asks the active phone to hand the dongle over. */
class SwitchHereActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Presence.selfProjecting) {
            Toast.makeText(this, "Este celular ya está activo", Toast.LENGTH_SHORT).show()
        } else {
            PresenceService.requestHere(this)
            Toast.makeText(this, "Pidiendo el control del carro…", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
