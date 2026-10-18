package com.muvatec.switchphone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val OK = Color(0xFF2EB872)
private val WARN = Color(0xFFE5A100)
private val HOT = Color(0xFFE5484D)

/** Live CPU temperature, load, memory and WiFi traffic of the dongle (`BoxMonitor`). */
@Composable
fun MonitorTab(c: Ctl) {
    val history = remember { mutableStateListOf<Monitor>() }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(c.state != null) {
        while (true) {
            runCatching { withContext(Dispatchers.IO) { c.client.boxMonitor() } }
                .onSuccess { history += it; if (history.size > 60) history.removeAt(0); error = null }
                .onFailure { error = it.message }
            delay(2000)
        }
    }

    val now = history.lastOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Monitor", "Estado del dongle en vivo", null) }
        if (now == null) {
            item { OfflineCard(loading = error == null, error = error ?: "Conecta el celular al WiFi del dongle (AutoKit_xx).") }
            return@LazyColumn
        }
        val tempColor = when { now.temp >= 75 -> HOT; now.temp >= 60 -> WARN; else -> OK }
        item {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Temperatura del procesador", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.1f °C".format(now.temp), fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = tempColor)
                        Text(
                            when { now.temp >= 75 -> "  muy caliente"; now.temp >= 60 -> "  tibio"; else -> "  normal" },
                            color = tempColor, modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Spark(history.map { it.temp.toFloat() }, tempColor)
                }
            }
        }
        item { Metric("Uso de CPU", "${now.cpu} %", "${now.freq} MHz", history.map { it.cpu.toFloat() }, MaterialTheme.colorScheme.primary) }
        item { Metric("Memoria", "%.0f %%".format(now.mem), "", history.map { it.mem.toFloat() }, MaterialTheme.colorScheme.primary) }
        item { Metric("WiFi ↓ recibido", "%.2f".format(now.rx), "valor del dongle", history.map { it.rx.toFloat() }, OK) }
        item { Metric("WiFi ↑ enviado", "%.2f".format(now.tx), "valor del dongle", history.map { it.tx.toFloat() }, OK) }
        item {
            Text(
                "Se actualiza cada 2 s. Si pasa de 75 °C con Android Auto activo, el celular avisa con una notificación.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Metric(title: String, value: String, extra: String, series: List<Float>, color: Color) {
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (extra.isNotBlank()) Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spark(series, color)
        }
    }
}

@Composable
private fun Spark(values: List<Float>, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        if (values.size < 2) return@Canvas
        val lo = values.min(); val hi = values.max()
        val span = (hi - lo).coerceAtLeast(1f)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height - (v - lo) / span * (size.height - 6f) - 3f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
        drawLine(color.copy(alpha = .15f), Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f)
    }
}
