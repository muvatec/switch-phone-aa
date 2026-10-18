package com.muvatec.switchphone

import android.os.Handler
import android.os.Looper

/** A phone asked (over BLE) to take over the dongle; the active phone shows it in the car and in a notification. */
object RequestInbox {
    data class Req(val hash: String, val name: String, val at: Long = System.currentTimeMillis())

    @Volatile var pending: Req? = null
        private set
    @Volatile var listener: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun post(r: Req) { pending = r; main.post { listener?.invoke() } }
    fun clear() { pending = null; main.post { listener?.invoke() } }
}
