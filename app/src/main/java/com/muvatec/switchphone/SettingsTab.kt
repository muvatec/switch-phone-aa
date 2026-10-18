package com.muvatec.switchphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val RESOLUTIONS = listOf(800 to 480, 1280 to 720, 1920 to 1080)
private val CH_5G = listOf(36, 40, 44, 48, 149, 153, 157, 161, 165)

/** Keys this screen knows how to present. Anything else the firmware reports is listed read-only at the end. */
private val KNOWN = setOf(
    "fastConnect", "autoConn", "syncMode", "bgMode", "returnMode", "startDelay", "carLinkType", "autoUpdate",
    "mediaDelay", "mediaSound", "CallQuality", "NaviAudio", "naviVolume", "btCall", "echoDelay", "autoPlay", "micGain",
    "resolutionWidth", "resolutionHeight", "bitRate", "fps", "ScreenDPI", "displaySize", "autoDisplay", "transMode",
    "squareCorner", "autoRefresh", "mouseMode", "KnobMode", "backRecording", "gps", "wifiChannel", "wifi5GSwitch", "lang",
)

/** Settings the firmware exposes, grouped. Only rows whose key exists in `infos` are shown, like the dongle's own web UI. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(c: Ctl, aggressive: Boolean, onAggressive: (Boolean) -> Unit) {
    val s = c.state
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Ajustes", "Solo aparece lo que tu firmware permite cambiar", c.refresh) }
        if (s == null) {
            item { OfflineCard(loading = false, error = "Conecta el celular al WiFi del dongle (AutoKit_xx).") }
            return@LazyColumn
        }
        val set = s.settings
        fun has(k: String) = set.containsKey(k)

        fun sw(k: String, title: String, sub: String): (@Composable () -> Unit)? =
            if (!has(k)) null else {
                { SettingRow(title, sub, (set[k] ?: 0) == 1) { on -> c.act("$title: ${if (on) "sí" else "no"}") { c.client.set(k, if (on) 1 else 0) } } }
            }

        fun slider(k: String, title: String, sub: String, min: Int, max: Int, step: Int, unit: String, zero: String? = null): (@Composable () -> Unit)? =
            if (!has(k)) null else {
                { SliderSetting(c, k, title, sub, min, max, step, unit, zero, set[k] ?: min) }
            }

        fun choice(k: String, title: String, sub: String, options: List<Pair<Int, String>>): (@Composable () -> Unit)? =
            if (!has(k)) null else {
                { ChoiceSetting(c, k, title, sub, options, set[k]) }
            }

        // ---- Names -------------------------------------------------------------------------------
        item { SectionTitle("Nombres del dongle") }
        item {
            SoftCard {
                NameRow(Icons.Filled.Tune, "Dispositivo", s.boxName) { c.editName(NameField("boxName", "Nombre del dispositivo", s.boxName)) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
                NameRow(Icons.Filled.Bluetooth, "Bluetooth", s.btName) { c.editName(NameField("btName", "Nombre Bluetooth", s.btName)) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
                NameRow(Icons.Filled.Wifi, "WiFi", s.wifiName) { c.editName(NameField("wifiName", "Nombre del WiFi", s.wifiName)) }
            }
        }

        // ---- Connection --------------------------------------------------------------------------
        val connection = listOfNotNull(
            sw("fastConnect", "Conexión rápida", "Reconecta más rápido al subir al carro"),
            sw("autoConn", "Conectar automáticamente", "El dongle busca un celular al encender"),
            sw("syncMode", "Sincronizar hora", "El dongle toma la hora del celular"),
            sw("bgMode", "Modo segundo plano", "Mantiene Android Auto activo en segundo plano"),
            choice("returnMode", "Al salir de Android Auto", "A qué pantalla vuelve el carro", listOf(0 to "Carro original", 1 to "Dongle")),
            slider("startDelay", "Retardo de arranque", "Espera antes de iniciar la conexión", 0, 120, 5, " s", "sin retardo"),
            choice("carLinkType", "Tipo de enlace con el carro", "Cómo se presenta el dongle al carro", listOf(30 to "Automático", 2 to "CarPlay", 4 to "Android Auto")),
            sw("autoUpdate", "Actualizar firmware automáticamente", "El dongle puede cambiar de versión solo"),
        )
        if (connection.isNotEmpty()) { item { SectionTitle("Conexión") }; item { Group(connection) } }

        // ---- Audio -------------------------------------------------------------------------------
        val audio = listOfNotNull(
            slider("mediaDelay", "Retardo de audio", "Menos = más reactivo, más = más estable", 300, 2000, 100, " ms"),
            sw("mediaSound", "Sonido multimedia", "Reproduce el audio por el carro"),
            choice("CallQuality", "Calidad de llamada", "", listOf(0 to "Normal", 1 to "Clara", 2 to "HD")),
            choice("NaviAudio", "Audio de navegación", "Canal que usan las indicaciones", listOf(0 to "Navegación", 1 to "Voz", 2 to "Multimedia")),
            slider("naviVolume", "Volumen de navegación", "", 0, 100, 5, "%"),
            sw("btCall", "Llamadas por Bluetooth", "Usa el Bluetooth del dongle para llamadas"),
            slider("echoDelay", "Retardo de eco", "Ajuste del micrófono en llamadas", 20, 2000, 20, " ms"),
            sw("autoPlay", "Reproducir automáticamente", "Inicia música al conectar"),
        )
        if (audio.isNotEmpty()) { item { SectionTitle("Audio y llamadas") }; item { Group(audio) } }

        // ---- Video -------------------------------------------------------------------------------
        if (has("resolutionWidth") && has("resolutionHeight")) {
            item { SectionTitle("Video") }
            item {
                SoftCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Resolución", fontWeight = FontWeight.SemiBold)
                        Text("Reinicia el dongle para aplicarla", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val w = set["resolutionWidth"]; val h = set["resolutionHeight"]
                            RESOLUTIONS.forEach { (rw, rh) ->
                                FilterChip(selected = w == rw && h == rh,
                                    onClick = { c.act("Resolución ${rw}x$rh · reinicia el dongle") { c.client.set("resolutionWidth", rw); c.client.set("resolutionHeight", rh) } },
                                    label = { Text("${rw}x$rh") })
                            }
                        }
                    }
                }
            }
        }
        val video = listOfNotNull(
            slider("bitRate", "Bitrate", "Calidad del video", 0, 20, 1, " Mbps", "automático"),
            slider("fps", "Cuadros por segundo", "", 0, 60, 5, " fps", "automático"),
            slider("ScreenDPI", "DPI de pantalla", "Tamaño de la interfaz", 0, 480, 10, "", "predeterminado"),
            choice("displaySize", "Tamaño de iconos", "", listOf(0 to "Predeterminado", 1 to "Pequeño", 2 to "Mediano", 3 to "Grande")),
            choice("autoDisplay", "Modo de pantalla", "", listOf(0 to "Normal", 1 to "Vehículo")),
            choice("transMode", "Modo de transferencia", "", listOf(0 to "Normal", 1 to "Compatible")),
            sw("squareCorner", "Esquinas cuadradas", "Quita las esquinas redondeadas"),
            sw("autoRefresh", "Refresco automático", ""),
            sw("mouseMode", "Modo ratón", ""),
            sw("KnobMode", "Modo perilla", ""),
            sw("backRecording", "Grabación en segundo plano", ""),
            sw("gps", "GPS del dongle (GNSS)", "Comparte la ubicación con el carro"),
        )
        if (video.isNotEmpty()) { if (!has("resolutionWidth")) item { SectionTitle("Video") }; item { Group(video) } }

        // ---- WiFi --------------------------------------------------------------------------------
        if (has("wifiChannel")) {
            item { SectionTitle("WiFi del dongle") }
            item {
                SoftCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Canal", fontWeight = FontWeight.SemiBold)
                        Text("Reinicia el dongle para aplicarlo. 5 GHz da más velocidad; 2.4 GHz más alcance.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val cur = set["wifiChannel"]
                        val all = (s.channels.ifEmpty { CH_5G }).sorted()
                        Text("5 GHz", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            all.filter { it >= 36 }.forEach { ch ->
                                FilterChip(selected = cur == ch, onClick = { c.act("Canal $ch · reinicia el dongle") { c.client.set("wifiChannel", ch) } }, label = { Text("$ch") })
                            }
                        }
                        val g24 = all.filter { it < 36 }
                        if (g24.isNotEmpty()) {
                            Text("2.4 GHz", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                g24.forEach { ch ->
                                    FilterChip(selected = cur == ch, onClick = { c.act("Canal $ch · reinicia el dongle") { c.client.set("wifiChannel", ch) } }, label = { Text("$ch") })
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- Language ----------------------------------------------------------------------------
        if (has("lang") && s.langs.isNotEmpty()) {
            item { SectionTitle("Idioma del dongle") }
            item {
                SoftCard {
                    Column(Modifier.padding(16.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.langs.forEach { l ->
                                FilterChip(selected = set["lang"] == l.id, onClick = { c.act("Idioma: ${l.name}") { c.client.set("lang", l.id) } }, label = { Text(l.name) })
                            }
                        }
                    }
                }
            }
        }

        // ---- This app ----------------------------------------------------------------------------
        item { SectionTitle("Esta app") }
        item {
            SoftCard {
                var auto by remember { mutableStateOf(Prefs.autoAccept(c.ctx)) }
                SettingRow("Aceptar solicitudes automáticamente", "Cede el carro sin preguntar cuando otro celular lo pide", auto) { auto = it; Prefs.setAutoAccept(c.ctx, it) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
                SettingRow("Cambio agresivo", "Al cambiar, olvida el celular actual (hay que re-emparejarlo)", aggressive) { onAggressive(it) }
            }
        }

        // ---- Anything else the firmware reports --------------------------------------------------
        val other = set.filterKeys { it !in KNOWN }
        if (other.isNotEmpty()) {
            item { SectionTitle("Otros valores del firmware (solo lectura)") }
            item {
                SoftCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        other.toSortedMap().forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$v", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(rows: List<@Composable () -> Unit>) {
    SoftCard {
        rows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
            row()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceSetting(c: Ctl, key: String, title: String, sub: String, options: List<Pair<Int, String>>, current: Int?) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (v, label) ->
                FilterChip(selected = current == v, onClick = { c.act("$title: $label") { c.client.set(key, v) } }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun SliderSetting(c: Ctl, key: String, title: String, sub: String, min: Int, max: Int, step: Int, unit: String, zero: String?, current: Int) {
    var v by remember(current) { mutableStateOf(current.coerceIn(min, max).toFloat()) }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val shown = v.roundToInt()
            Text(if (shown == 0 && zero != null) zero else "$shown$unit", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = v,
            onValueChange = { v = (((it - min) / step).roundToInt() * step + min).toFloat().coerceIn(min.toFloat(), max.toFloat()) },
            valueRange = min.toFloat()..max.toFloat(),
            onValueChangeFinished = { val n = v.roundToInt(); c.act("$title: $n$unit") { c.client.set(key, n) } },
        )
    }
}
