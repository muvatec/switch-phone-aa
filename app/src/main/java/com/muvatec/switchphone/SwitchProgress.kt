package com.muvatec.switchphone

/**
 * Progress of a phone switch. The dongle reboots to release the active phone, which takes about 30 s
 * (about 5 s to power down, 21 s to boot, a few seconds for the new phone to connect). Showing a countdown makes
 * that gap understandable instead of looking like a failure.
 */
object SwitchProgress {
    const val TOTAL_MS = 30_000L
    private const val GRACE_MS = 20_000L

    @Volatile var startedAt = 0L
        private set
    @Volatile var target = ""
        private set
    @Volatile var incoming = false      // true on the phone that is taking over
        private set

    fun start(target: String, incoming: Boolean = false) {
        this.target = target; this.incoming = incoming; startedAt = System.currentTimeMillis()
    }

    fun clear() { startedAt = 0L }

    fun elapsedMs(): Long = if (startedAt == 0L) 0L else System.currentTimeMillis() - startedAt
    fun active(): Boolean = startedAt != 0L && elapsedMs() < TOTAL_MS + GRACE_MS
    fun late(): Boolean = active() && elapsedMs() >= TOTAL_MS
    fun fraction(): Float = (elapsedMs().toFloat() / TOTAL_MS).coerceIn(0f, 1f)
    fun remainingSec(): Int = ((TOTAL_MS - elapsedMs()) / 1000).toInt().coerceAtLeast(0)

    fun phase(): String = when {
        late() -> "Tarda más de lo normal…"
        elapsedMs() < 6_000 -> "Soltando el carro…"
        elapsedMs() < 27_000 -> "Reiniciando el dongle…"
        else -> "Conectando…"
    }
}
