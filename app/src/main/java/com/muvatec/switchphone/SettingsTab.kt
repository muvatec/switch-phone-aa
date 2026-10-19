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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.OutlinedButton
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
        item { SectionTitle("Este celular") }
        item { ThisPhoneCard(c) }
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

        // Labels and help text come from the dongle's own web UI (English language file + its "?" popups).

        // ---- Connection --------------------------------------------------------------------------
        val connection = listOfNotNull(
            sw("autoConn", "Conectar automáticamente", "El dongle busca y conecta al celular por su cuenta"),
            sw("fastConnect", "Modo de alta velocidad", "\"High speed Mode\" del dongle: acelera la conexión"),
            choice("carLinkType", "Tipo de enlace (HULink)", "Cómo se presenta el dongle a la unidad principal del carro", listOf(30 to "Automático", 2 to "CarPlay", 4 to "Android Auto")),
            choice("returnMode", "Al terminar la conexión", "A qué pantalla vuelve el carro", listOf(0 to "Carro original", 1 to "Dongle")),
            slider("startDelay", "Retardo de arranque", "Espera antes de iniciar la conexión", 0, 120, 5, " s", "sin retardo"),
            sw("bgMode", "Segundo plano", "No muestra la pantalla de conexión del dongle; actívalo si el carro se ve borroso tras conectar el celular"),
            sw("syncMode", "Sincronizar tablero", "Para tableros de algunos carros antiguos; normalmente no hace falta"),
            sw("autoUpdate", "Actualización automática", "El dongle puede cambiar de versión solo"),
        )
        if (connection.isNotEmpty()) { item { SectionTitle("Conexión") }; item { Group(connection) } }

        // ---- Audio -------------------------------------------------------------------------------
        val audio = listOfNotNull(
            slider("mediaDelay", "Retardo del audio multimedia", "Por defecto 1000 ms. Más retardo = menos cortes; menos retardo = música más sincronizada con la imagen. Solo cámbialo si hay problemas", 300, 2000, 100, " ms"),
            choice("CallQuality", "Calidad de llamada", "", listOf(0 to "Normal", 1 to "Clara", 2 to "HD")),
            choice("NaviAudio", "Canal de navegación", "Por dónde suena la voz de navegación", listOf(0 to "Navegación", 1 to "Voz", 2 to "Multimedia")),
            slider("naviVolume", "Volumen de navegación en llamada", "", 0, 100, 5, "%"),
            sw("btCall", "Llamadas por Bluetooth", ""),
            slider("echoDelay", "Retardo de eco", "Ajuste del micrófono en llamadas", 20, 2000, 20, " ms"),
            sw("autoPlay", "Reproducir al conectar", "Según el dongle, solo aplica a HiCar (Huawei)"),
        )
        if (audio.isNotEmpty()) { item { SectionTitle("Audio y llamadas") }; item { Group(audio) } }

        // ---- Video -------------------------------------------------------------------------------
        if (has("resolutionWidth") && has("resolutionHeight")) {
            item { SectionTitle("Video") }
            item {
                SoftCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Resolución", fontWeight = FontWeight.SemiBold)
                        Text("Resolución de la imagen del celular. Si se ve mal o estirada, iguálala a la de la pantalla del carro; la mayoría de carros no necesita cambiarla. Reinicia el dongle.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            slider("bitRate", "Bitrate del video", "Menos = más fluido: si hay trabas o retraso, por debajo de 8 Mbps. Más = calidad más estable: por encima de 8 Mbps", 0, 20, 1, " Mbps", "automático"),
            slider("fps", "Cuadros por segundo", "Si la pantalla se ve con mucho retraso, prueba con 20", 0, 60, 5, " fps", "automático"),
            slider("ScreenDPI", "DPI de pantalla", "", 0, 480, 10, "", "predeterminado"),
            choice("displaySize", "Estilo de pantalla (tamaño)", "", listOf(0 to "Predeterminado", 1 to "Pequeño", 2 to "Mediano", 3 to "Grande")),
            choice("autoDisplay", "Pantalla automática", "", listOf(0 to "Normal", 1 to "Automática")),
            choice("transMode", "Modo de transferencia", "", listOf(0 to "Normal", 1 to "Compatible")),
            sw("gps", "GPS", "El GPS del carro se sincroniza con el celular"),
            sw("mouseMode", "Modo cursor", "Muestra un cursor en carros con touchpad; tocar tres veces alterna deslizar y arrastrar"),
            sw("KnobMode", "Modo perilla", ""),
            sw("autoRefresh", "Refresco automático", ""),
            sw("backRecording", "Grabación en segundo plano", ""),
        )
        if (video.isNotEmpty()) { if (!has("resolutionWidth")) item { SectionTitle("Video") }; item { Group(video) } }

        // ---- Undocumented ------------------------------------------------------------------------
        val undocumented = listOfNotNull(
            sw("mediaSound", "Sonido multimedia", "El firmware lo reporta, pero la interfaz oficial del dongle no lo describe"),
            sw("squareCorner", "Esquinas cuadradas", "Apareció con el firmware 2025.12; sin descripción oficial ni efecto visible en Android Auto"),
        )
        if (undocumented.isNotEmpty()) {
            item { SectionTitle("Sin documentar") }
            item {
                Text("El dongle los expone pero no los describe. Si no notas efecto, déjalos como estaban.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
            item { Group(undocumented) }
        }

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

/** Status of this phone in the car and the Quick Settings button. */
@Composable
private fun ThisPhoneCard(c: Ctl) {
    val ctx = c.ctx
    val active = Presence.selfProjecting
    SoftCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("En el carro", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (active) "Este celular está activo" else "Este celular no está conectado al carro",
                    fontWeight = FontWeight.Bold, color = if (active) Active else MaterialTheme.colorScheme.onSurface)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
            val added = Prefs.tileAdded(ctx)
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Botón del panel rápido", fontWeight = FontWeight.SemiBold)
                Text("Un toque desde el panel de Android para pedir el carro (o ver que ya lo tienes).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(enabled = !added, onClick = {
                    val sb = ctx.getSystemService(android.app.StatusBarManager::class.java)
                    sb.requestAddTileService(android.content.ComponentName(ctx, SwitchTileService::class.java), "Switch Phone",
                        android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_swap), ctx.mainExecutor) { result ->
                        // 1 = already added, 2 = added
                        if (result == 1 || result == 2) Prefs.setTileAdded(ctx, true)
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(if (added) androidx.compose.material.icons.Icons.Filled.Check else androidx.compose.material.icons.Icons.Filled.Add, null)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    Text(if (added) "Botón ya añadido al panel rápido" else "Añadir botón al panel rápido")
                }
            }
        }
    }
}
