package com.muvatec.switchphone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Active = Color(0xFF2EB872)
val Available = Color(0xFF2B8CFF)
val Saved = Color(0xFF8A93A0)

private fun scheme(dark: Boolean): ColorScheme = if (dark) darkColorScheme(
    primary = Available, onPrimary = Color.White,
    background = Color(0xFF0E1116), onBackground = Color(0xFFEDEFF3),
    surface = Color(0xFF171B22), onSurface = Color(0xFFEDEFF3),
    surfaceVariant = Color(0xFF222832), onSurfaceVariant = Color(0xFF9AA3AF),
    outline = Color(0xFF3A4350), secondaryContainer = Color(0xFF22344D), onSecondaryContainer = Color(0xFFD7E6FF),
) else lightColorScheme(
    primary = Color(0xFF1F73E0), onPrimary = Color.White,
    background = Color(0xFFF2F4F8), onBackground = Color(0xFF14181F),
    surface = Color.White, onSurface = Color(0xFF14181F),
    surfaceVariant = Color(0xFFE8ECF2), onSurfaceVariant = Color(0xFF5B6574),
    outline = Color(0xFFCBD2DC), secondaryContainer = Color(0xFFDCE9FF), onSecondaryContainer = Color(0xFF0B2B5C),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = scheme(isSystemInDarkTheme()), shapes = androidx.compose.material3.Shapes()) { DongleApp() }
        }
    }
}

private fun PhoneStatus.color() = when (this) {
    PhoneStatus.CONNECTED -> Active
    PhoneStatus.NEARBY -> Available
    PhoneStatus.SAVED -> Saved
}

private fun PhoneStatus.title() = when (this) {
    PhoneStatus.CONNECTED -> "ACTIVO"
    PhoneStatus.NEARBY -> "DISPONIBLE"
    PhoneStatus.SAVED -> "GUARDADO"
}

private fun PhoneStatus.detail() = when (this) {
    PhoneStatus.CONNECTED -> "Conexión actual"
    PhoneStatus.NEARBY -> "Visible por Bluetooth"
    PhoneStatus.SAVED -> "No visible ahora"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DongleApp() {
    val ctx = LocalContext.current
    val client = remember { DongleClient(ctx) }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var state by remember { mutableStateOf<DongleState?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var confirmForget by remember { mutableStateOf<Device?>(null) }
    var confirmRestart by remember { mutableStateOf(false) }
    var confirmSwitch by remember { mutableStateOf<Device?>(null) }
    var renaming by remember { mutableStateOf<Device?>(null) }
    var renameText by remember { mutableStateOf("") }
    var aggressive by remember { mutableStateOf(Prefs.aggressive(ctx)) }
    var advanced by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf<NameField?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var baseline by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun refresh() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client.infos() } }
                .onSuccess { state = it; error = null }
                .onFailure { error = it.message ?: it.toString() }
            loading = false
        }
    }

    fun act(msg: String, block: () -> Unit) {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { snack.showSnackbar(msg) }
                .onFailure { snack.showSnackbar("Error: ${it.message}") }
            refresh()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        PresenceService.start(ctx)
    }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    LaunchedEffect(Unit) {
        refresh()
        val perms = Presence.requiredPermissions().toMutableList()
        if (android.os.Build.VERSION.SDK_INT >= 33) perms += android.Manifest.permission.POST_NOTIFICATIONS
        if (Presence.hasPermissions(ctx)) PresenceService.start(ctx) else permLauncher.launch(perms.toTypedArray())
        while (true) { delay(6000); refresh() }
    }

    val ctl = Ctl(ctx, client, state, { msg, block -> act(msg, block) }, { refresh() },
        { msg -> scope.launch { snack.showSnackbar(msg) } }, { editName = it }, { confirmRestart = true })
    var tab by remember { mutableStateOf(Tab.HOME) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.HOME -> {
                    val s = state
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().statusBarsPadding(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
            item { ScreenTitle("Switch Phone", "Elige qué celular usa Android Auto") { refresh() } }

            if (s == null) {
                item { OfflineCard(loading = loading, error = error) }
            } else {
                item { DongleCard(s) }
                item {
                    val here = Presence.selfProjecting; @Suppress("UNUSED_EXPRESSION") tick
                    SoftCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (here) {
                                Text("Este celular está activo en el carro", fontWeight = FontWeight.Bold, color = Active)
                            } else {
                                val other = Presence.activeOthers().firstOrNull()?.let { Prefs.nameForHash(ctx, it) ?: "Otro celular" }
                                val rs = Presence.reqState
                                val who = Prefs.nameForHash(ctx, rs.targetHash) ?: other ?: "El otro celular"
                                when (rs.phase) {
                                    Presence.ReqPhase.WAITING -> {
                                        Text("Esperando respuesta de $who…", fontWeight = FontWeight.SemiBold)
                                        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                                        Text("Aparece en la pantalla del carro y en su celular.", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        OutlinedButton(onClick = { Presence.clearRequest(ctx) }, Modifier.fillMaxWidth()) { Text("Cancelar") }
                                    }
                                    Presence.ReqPhase.ACCEPTED -> Text("$who aceptó. Conectando este celular…", fontWeight = FontWeight.Bold, color = Active)
                                    else -> {
                                        if (rs.phase == Presence.ReqPhase.REJECTED)
                                            Text("$who rechazó la solicitud", fontWeight = FontWeight.Bold, color = Color(0xFFE5484D))
                                        else if (rs.phase == Presence.ReqPhase.TIMEOUT)
                                            Text("$who no respondió", fontWeight = FontWeight.Bold, color = Color(0xFFE5A100))
                                        else if (other != null)
                                            Text("$other está usando el carro", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Button(onClick = { PresenceService.requestHere(ctx) }, Modifier.fillMaxWidth()) {
                                            Icon(Icons.Filled.SwapHoriz, null); Spacer(Modifier.size(8.dp))
                                            Text(if (rs.phase == Presence.ReqPhase.IDLE) "Solicitar conexión" else "Reintentar")
                                        }
                                    }
                                }
                            }
                            if (android.os.Build.VERSION.SDK_INT >= 33) {
                                OutlinedButton(onClick = {
                                    val sb = ctx.getSystemService(android.app.StatusBarManager::class.java)
                                    sb.requestAddTileService(android.content.ComponentName(ctx, SwitchTileService::class.java), "Switch Phone",
                                        android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_swap), ctx.mainExecutor) { }
                                }, Modifier.fillMaxWidth()) {
                                    Icon(Icons.Filled.Add, null); Spacer(Modifier.size(8.dp)); Text("Añadir botón al panel rápido")
                                }
                            }
                        }
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SectionTitle("Celulares") }
                        TextButton(onClick = { baseline = s.devices.map { it.id }.toSet(); addOpen = true }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.size(4.dp)); Text("Agregar celular")
                        }
                    }
                }
                if (s.devices.isEmpty()) item { Hint("El dongle no tiene celulares guardados todavía.") }
                items(s.devices.sortedBy { s.statusOf(it).ordinal }, key = { it.id }) { d ->
                    PhoneCard(
                        d = d, status = s.statusOf(d),
                        onSwitch = { confirmSwitch = d },
                        onRename = { renaming = d; renameText = d.alias.ifBlank { d.name } },
                        onForget = { confirmForget = d },
                    )
                }

                    }
                    }
                }
                Tab.SETTINGS -> SettingsTab(ctl, aggressive) { aggressive = it; Prefs.setAggressive(ctx, it) }
                Tab.MONITOR -> MonitorTab(ctl)
                Tab.TOOLS -> ToolsTab(ctl)
            }
            SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            Tab.values().forEach { t ->
                NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Icon(t.icon, t.label) }, label = { Text(t.label, maxLines = 1) })
            }
        }
    }

    if (addOpen) {
        val btName = state?.btName?.ifBlank { null } ?: Prefs.lastBtName(ctx).ifBlank { "el nombre del dongle (AutoKit_xxx)" }
        val added = state?.devices?.filter { it.id !in baseline }.orEmpty()
        AlertDialog(
            onDismissRequest = { addOpen = false },
            title = { Text(if (added.isEmpty()) "Agregar un celular" else "¡Celular agregado!") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (added.isNotEmpty()) {
                        added.forEach { Text("✓ ${it.label} ya está guardado en el dongle.", fontWeight = FontWeight.Bold, color = Active) }
                        Text("Ya puedes cambiar a él desde la lista o desde la pantalla del carro.")
                    } else {
                        Text("El dongle solo conecta a un celular a la vez, así que primero hay que dejarlo libre.")
                        Text("1. En el celular que ya está conectado, pulsa «Liberar dongle».", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { act("Dongle liberado · reiniciando") { client.restart() } }, Modifier.fillMaxWidth()) { Text("Liberar dongle (reinicia)") }
                        Text("2. En el celular nuevo: instala Switch Phone y empareja el Bluetooth con \"$btName\".", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = {
                            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }, Modifier.fillMaxWidth()) { Text("Abrir Bluetooth (en el celular nuevo)") }
                        Text("3. En el celular nuevo abre Android Auto y elige conectar un carro nuevo. Se conectará por WiFi al dongle.", style = MaterialTheme.typography.bodyMedium)
                        Text("Esta pantalla detecta cuando el celular nuevo aparece en el dongle (se actualiza sola).", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addOpen = false }) { Text(if (added.isEmpty()) "Cerrar" else "Listo") } },
        )
    }
    editName?.let { f ->
        var text by remember(f) { mutableStateOf(f.current) }
        val valid = NAME_RULE.matches(text) && text != f.current
        AlertDialog(
            onDismissRequest = { editName = null },
            title = { Text(f.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, singleLine = true,
                        isError = text.isNotEmpty() && !NAME_RULE.matches(text),
                        supportingText = { Text("Letras, números, _ y - · hasta 31 caracteres · sin espacios") },
                    )
                    Text("Reinicia el dongle para aplicarlo. Si cambias el Bluetooth o el WiFi, los celulares pueden pedir volver a emparejar.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(enabled = valid, onClick = { val v = text; editName = null; act("${f.title}: $v · reinicia el dongle") { client.set(f.item, v) } }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editName = null }) { Text("Cancelar") } },
        )
    }
    renaming?.let { d ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Nombre del celular") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { Prefs.setAlias(ctx, d.id, renameText); renaming = null; refresh() }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } },
        )
    }
    confirmSwitch?.let { d ->
        val cur = state?.devices?.firstOrNull { state?.isConnected(it) == true }
        AlertDialog(
            onDismissRequest = { confirmSwitch = null },
            title = { Text("Cambiar a ${d.label}") },
            text = { Text(if (cur != null) "Se desconecta ${cur.label} y Android Auto pasa a ${d.label}. Tarda unos 30 segundos." else "Android Auto pasa a ${d.label}.") },
            confirmButton = { TextButton(onClick = { confirmSwitch = null; act("Cambiando a ${d.label}…") { client.switchTo(d, cur, aggressive) } }) { Text("Cambiar") } },
            dismissButton = { TextButton(onClick = { confirmSwitch = null }) { Text("Cancelar") } },
        )
    }
    confirmForget?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmForget = null },
            title = { Text("Olvidar ${d.label}") },
            text = { Text("El dongle borra este celular. Habrá que volver a emparejarlo.") },
            confirmButton = { TextButton(onClick = { confirmForget = null; act("${d.label} olvidado") { client.forget(d.id) } }) { Text("Olvidar", color = Color(0xFFE5484D)) } },
            dismissButton = { TextButton(onClick = { confirmForget = null }) { Text("Cancelar") } },
        )
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Reiniciar dongle") },
            text = { Text("Se corta Android Auto unos segundos y vuelve a buscar un celular.") },
            confirmButton = { TextButton(onClick = { confirmRestart = false; act("Reiniciando…") { client.restart() } }) { Text("Reiniciar") } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DongleCard(s: DongleState) {
    val brush = Brush.linearGradient(listOf(Color(0xFF1F6FEB), Color(0xFF14A3C7)))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(brush).padding(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF8CFFB9)))
                Spacer(Modifier.size(8.dp))
                Text("Dongle conectado", color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.labelLarge)
            }
            // Title: the name the owner gave the dongle; then the car it reports; then what the hardware is.
            Text(s.boxName.ifBlank { "Mi dongle" }, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            val car = listOf(s.carInfo.brand, s.carInfo.model, s.carInfo.year.takeIf { it > 0 }?.toString()).filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
            Text(if (car.isBlank()) "Carro sin configurar (Herramientas → Datos del carro)" else car,
                color = Color.White.copy(alpha = .95f), style = MaterialTheme.typography.titleSmall)
            Text("Carlinkit 5.0 · ${s.product.removeSuffix("_AUTOKIT")}", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Meta(Icons.Filled.Bluetooth, s.btName)
                Meta(Icons.Filled.Wifi, s.wifiName)
            }
            Text("Firmware ${s.version}", color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Meta(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun OfflineCard(loading: Boolean, error: String?) {
    SoftCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WifiOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(10.dp))
                Text(if (loading) "Buscando el dongle…" else "Sin conexión con el dongle", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            if (!loading) {
                Text("Conecta este celular al WiFi del dongle (AutoKit_xx) para ver y cambiar los ajustes. En el carro funciona sin hacer nada.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!error.isNullOrBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PhoneCard(d: Device, status: PhoneStatus, onSwitch: () -> Unit, onRename: () -> Unit, onForget: () -> Unit) {
    val accent = status.color()
    var menu by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (status == PhoneStatus.CONNECTED) 3.dp else 1.dp),
        border = if (status == PhoneStatus.CONNECTED) BorderStroke(2.dp, accent) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(accent.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Smartphone, null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = .16f)) {
                        Text(status.title(), color = accent, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(status.detail(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (status != PhoneStatus.CONNECTED) {
                FilledTonalButton(onClick = onSwitch, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Cambiar")
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Más", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Renombrar") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Olvidar", color = Color(0xFFE5484D)) }, leadingIcon = { Icon(Icons.Filled.Delete, null, tint = Color(0xFFE5484D)) }, onClick = { menu = false; onForget() })
                }
            }
        }
    }
}

@Composable
fun SoftCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Column { content() } }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
fun SettingRow(title: String, subtitle: String, checked: Boolean, padded: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(if (padded) PaddingValues16 else androidx.compose.foundation.layout.PaddingValues(0.dp)),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private val PaddingValues16 = androidx.compose.foundation.layout.PaddingValues(16.dp)

val NAME_RULE = Regex("^[0-9a-zA-Z_-]{1,31}$")

@Composable
fun NameRow(icon: ImageVector, label: String, value: String, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "—" }, fontWeight = FontWeight.SemiBold)
        }
        Icon(Icons.Filled.Edit, "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}
