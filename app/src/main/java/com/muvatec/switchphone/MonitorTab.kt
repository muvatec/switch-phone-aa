package com.muvatec.switchphone

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val OK = Color(0xFF2EB872)
private val WARN = Color(0xFFE5A100)
private val HOT = Color(0xFFE5484D)
private val BLUE = Color(0xFF2B8CFF)

/** Live CPU, memory, temperature and WiFi traffic of the dongle (`BoxMonitor`) as donut charts. */
@Composable
fun MonitorTab(c: Ctl) {
    var now by remember { mutableStateOf<Monitor?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(c.state != null) {
        while (true) {
            runCatching { withContext(Dispatchers.IO) { c.client.boxMonitor() } }
                .onSuccess { now = it; error = null }
                .onFailure { error = it.message }
            delay(2000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Monitor", "Estado del dongle en vivo", null) }
        val m = now
        if (m == null) {
            item { OfflineCard(loading = error == null, error = error ?: "Conecta el celular al WiFi del dongle (AutoKit_xx).") }
            return@LazyColumn
        }
        val load = when { m.cpu >= 85 -> HOT; m.cpu >= 60 -> WARN; else -> OK }
        val memColor = when { m.mem >= 90 -> HOT; m.mem >= 75 -> WARN; else -> BLUE }
        val tempColor = when { m.temp >= 75 -> HOT; m.temp >= 60 -> WARN; else -> OK }
        val tempText = when { m.temp >= 75 -> "Muy caliente"; m.temp >= 60 -> "Tibio"; else -> "Normal" }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    ChartCard("CPU", "${m.freq} MHz") {
                        Donut(listOf(m.cpu / 100f to load), "${m.cpu}%")
                    }
                }
                Box(Modifier.weight(1f)) {
                    ChartCard("Memoria", "en uso") {
                        Donut(listOf((m.mem / 100.0).toFloat() to memColor), "%.0f%%".format(m.mem))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    ChartCard("Temperatura", tempText, tempColor) {
                        Donut(listOf((m.temp / 100.0).toFloat() to tempColor), "%.0f°".format(m.temp))
                    }
                }
                Box(Modifier.weight(1f)) {
                    val total = m.rx + m.tx
                    ChartCard("WiFi", "↓ %.2f   ↑ %.2f".format(m.rx, m.tx)) {
                        if (total > 0.0) Donut(
                            listOf((m.rx / total).toFloat() to OK, (m.tx / total).toFloat() to BLUE),
                            "%.2f".format(total),
                        ) else Donut(emptyList(), "0")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend(OK, "Recibido ↓")
                Legend(BLUE, "Enviado ↑")
            }
        }
        item {
            Text(
                "Se actualiza cada 2 s. Si la temperatura llega a 75 °C con Android Auto activo, el celular avisa con una notificación. " +
                    "El tráfico WiFi se muestra en las unidades que entrega el dongle.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ChartCard(title: String, caption: String, captionColor: Color? = null, chart: @Composable () -> Unit) {
    SoftCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            chart()
            Text(caption, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                color = captionColor ?: MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Donut made of consecutive arcs; each pair is (share of the full circle 0..1, color). */
@Composable
private fun Donut(parts: List<Pair<Float, Color>>, center: String) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.fillMaxWidth(.82f).aspectRatio(1f), contentAlignment = Alignment.Center) {
        val animated = parts.map { (v, col) -> animateFloatAsState(v.coerceIn(0f, 1f), label = "donut") to col }
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.17f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            animated.forEach { (share, col) ->
                val sweep = 360f * share.value
                if (sweep > 0.5f) drawArc(col, start, (sweep - 2f).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Text(center, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, androidx.compose.foundation.shape.CircleShape))
        Text(text, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
