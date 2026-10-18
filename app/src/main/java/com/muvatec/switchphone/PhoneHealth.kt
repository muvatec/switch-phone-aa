package com.muvatec.switchphone

import java.io.File

/** Memory pressure of THIS phone. A phone that is out of RAM and swap makes Android Auto slow and drop touches. */
data class PhoneMem(val totalMb: Int, val availMb: Int, val swapTotalMb: Int, val swapFreeMb: Int) {
    val ramFreePct: Int get() = if (totalMb > 0) availMb * 100 / totalMb else 100
    val swapUsedPct: Int get() = if (swapTotalMb > 0) 100 - swapFreeMb * 100 / swapTotalMb else 0

    /** Low RAM and swap nearly exhausted. */
    val critical: Boolean get() = ramFreePct < 20 && swapTotalMb > 0 && swapUsedPct > 85
    val warning: Boolean get() = ramFreePct < 20 && swapUsedPct > 70
}

object PhoneHealth {
    fun read(): PhoneMem? = runCatching {
        val m = HashMap<String, Long>()
        File("/proc/meminfo").forEachLine { l ->
            val p = l.split(Regex("\\s+"))
            if (p.size >= 2) m[p[0].removeSuffix(":")] = p[1].toLongOrNull() ?: 0L
        }
        fun mb(k: String) = ((m[k] ?: 0L) / 1024).toInt()
        PhoneMem(mb("MemTotal"), mb("MemAvailable"), mb("SwapTotal"), mb("SwapFree"))
    }.getOrNull()
}
