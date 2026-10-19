package com.muvatec.switchphone

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

private val RED = Color(0xFFE5484D)

@Composable
fun ToolsTab(c: Ctl) {
    val s = c.state
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Herramientas", "Logo, datos del carro, logs y mantenimiento", c.refresh) }
        if (s == null) {
            item { OfflineCard(loading = false, error = "Conecta el celular al WiFi del dongle (AutoKit_xx).") }
            return@LazyColumn
        }

        item { SectionTitle("Logo del carro") }
        item { LogoCard(c) }

        item { SectionTitle("Datos del carro") }
        item { CarInfoCard(c, s.carInfo) }

        item { SectionTitle("Logs para soporte") }
        item {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Descarga el registro del dongle y compártelo (correo, WhatsApp, GitHub).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LogButton(c, scope, "Log del dongle", "logFile")
                    LogButton(c, scope, "Log de la aplicación del dongle", "appLogFile")
                    LogButton(c, scope, "Log del SDK", "sdkLogFile")
                }
            }
        }

        item { SectionTitle("Registro del carro") }
        item {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Guarda lo que hace Android Auto con esta app (si el carro la enlazó, tamaño de pantalla, toques). Úsalo después de conectar al carro si Switch Phone no aparece.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {
                        val txt = CarLog.read(c.ctx).ifBlank { "Sin eventos: Android Auto todavía no ha usado esta app." }
                        val f = File(File(c.ctx.cacheDir, "logs").apply { mkdirs() }, "switchphone_carro_${System.currentTimeMillis()}.log").also { it.writeText(txt) }
                        val uri = FileProvider.getUriForFile(c.ctx, "${c.ctx.packageName}.files", f)
                        c.ctx.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            "Registro del carro").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, Modifier.fillMaxWidth()) { Text("Compartir registro del carro") }
                    TextButton(onClick = { CarLog.clear(c.ctx); c.toast("Registro borrado") }) { Text("Borrar registro") }
                    Text("Con el celular conectado a Android Auto, simula que otro celular pide el carro para ver el aviso con Aceptar / Rechazar sobre el mapa y la música. No cambia nada en el dongle.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { PresenceService.testRequest(c.ctx); c.toast("Aviso de prueba enviado") }, Modifier.fillMaxWidth()) { Text("Probar aviso de solicitud en el carro") }
                }
            }
        }

        item { SectionTitle("Firmware") }
        item {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.product, fontWeight = FontWeight.Bold)
                    Text("Versión ${s.version}", style = MaterialTheme.typography.bodyMedium)
                    if (s.hardware.isNotBlank()) Text("Hardware ${s.hardware}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.modules.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        "Actualizar el firmware no está en esta app: una imagen incorrecta puede dejar el dongle inservible. " +
                            "Hazlo con la app oficial del dongle.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item { SectionTitle("Zona de peligro") }
        item {
            SoftCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reiniciar el dongle suelta al celular actual; restaurar borra los ajustes.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { c.requestRestart() }, Modifier.fillMaxWidth()) { Text("Reiniciar dongle") }
                    OutlinedButton(onClick = { confirmReset = "resetApp" }, Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RED)) { Text("Restablecer la app del dongle") }
                    OutlinedButton(onClick = { confirmReset = "reset" }, Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RED)) { Text("Restaurar ajustes de fábrica") }
                }
            }
        }
    }

    confirmReset?.let { kind ->
        var typed by remember(kind) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text(if (kind == "reset") "Restaurar de fábrica" else "Restablecer la app") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se pierden los ajustes y los celulares guardados en el dongle; habrá que volver a emparejar. " +
                        "Escribe RESTAURAR para confirmar.")
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim() == "RESTAURAR", onClick = {
                    confirmReset = null
                    c.act("Dongle restaurado") { if (kind == "reset") c.client.factoryReset() else c.client.resetApp() }
                }) { Text("Restaurar", color = RED) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun LogoCard(c: Ctl) {
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var png by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bmp = c.ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }!!
            val side = minOf(bmp.width, bmp.height)
            val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            // The dongle's own UI sends 180x180 PNG files of at most 100 KB.
            var px = 180
            var bytes: ByteArray
            do {
                val scaled = Bitmap.createScaledBitmap(square, px, px, true)
                bytes = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                preview = scaled
                px -= 20
            } while (bytes.size > 100 * 1024 && px >= 80)
            if (bytes.size > 100 * 1024) error("La imagen pesa más de 100 KB incluso reducida")
            png = bytes
            error = null
        }.onFailure { preview = null; png = null; error = it.message ?: "No pude leer la imagen" }
    }
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Imagen que el dongle muestra al conectar. Cuadrada, se ajusta a 180×180 PNG (máx. 100 KB). Reinicia el dongle para verla.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            preview?.let {
                Image(it.asImageBitmap(), "Vista previa", Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)))
            }
            if (error != null) Text(error!!, color = RED, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pick.launch("image/*") }) { Text("Elegir imagen") }
                Button(enabled = png != null, onClick = {
                    val data = png ?: return@Button
                    c.act("Logo subido · reinicia el dongle") { c.client.uploadLogo(data) }
                }) { Text("Subir") }
            }
            TextButton(onClick = { c.act("Logo restaurado") { c.client.resetLogo() } }) { Text("Volver al logo original") }
        }
    }
}

@Composable
private fun CarInfoCard(c: Ctl, info: CarInfoData) {
    var brand by remember(info) { mutableStateOf(info.brand) }
    var model by remember(info) { mutableStateOf(info.model) }
    var year by remember(info) { mutableStateOf(if (info.year > 0) info.year.toString() else "") }
    val ok = brand.isNotBlank() && model.isNotBlank() && year.toIntOrNull()?.let { it in 1980..2100 } == true &&
        brand.toByteArray().size <= 31 && model.toByteArray().size <= 31
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Marca, modelo y año que el dongle informa. Hasta 31 caracteres.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(brand, { brand = it }, label = { Text("Marca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text("Modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Año") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = ok, onClick = { c.act("Datos del carro guardados") { c.client.setCarInfo(brand.trim(), model.trim(), year.toInt()) } }) { Text("Guardar") }
        }
    }
}

@Composable
private fun LogButton(c: Ctl, scope: kotlinx.coroutines.CoroutineScope, label: String, kind: String) {
    OutlinedButton(onClick = {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = c.client.readLog(kind)
                    val dir = File(c.ctx.cacheDir, "logs").apply { mkdirs() }
                    File(dir, "dongle_${kind}_${System.currentTimeMillis()}.log").also { it.writeText(text) }
                }
            }.onSuccess { f ->
                val uri = FileProvider.getUriForFile(c.ctx, "${c.ctx.packageName}.files", f)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                c.ctx.startActivity(Intent.createChooser(send, label).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { c.toast("Error: ${it.message}") }
        }
    }, Modifier.fillMaxWidth()) { Text(label) }
}
