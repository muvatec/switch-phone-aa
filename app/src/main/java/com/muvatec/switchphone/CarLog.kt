package com.muvatec.switchphone

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent log of what Android Auto does with this app (service created, host, screen size, taps, disconnects).
 * The system logcat only keeps a few minutes, so a trip to the car would be gone by the time the phone reaches a computer.
 */
object CarLog {
    private const val MAX_BYTES = 200 * 1024
    private val lock = Any()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "car.log")

    fun log(ctx: Context, msg: String) {
        Log.d("SwitchPhone", "car: $msg")
        runCatching {
            synchronized(lock) {
                val f = file(ctx)
                if (f.exists() && f.length() > MAX_BYTES) {
                    val keep = f.readText().takeLast(MAX_BYTES / 2)
                    f.writeText(keep.substringAfter('\n'))
                }
                f.appendText("${fmt.format(Date())}  $msg\n")
            }
        }
    }

    fun read(ctx: Context): String = runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
    fun clear(ctx: Context) { runCatching { file(ctx).delete() } }
}
