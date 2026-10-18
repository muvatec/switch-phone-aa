package com.muvatec.switchphone

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Name / security / password for the passenger hotspot, QR for passengers and a shortcut to the system hotspot screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HotspotCard(c: Ctl) {
    val ctx = c.ctx
    var remind by remember { mutableStateOf(Prefs.hotspotRemind(ctx)) }
    var name by remember { mutableStateOf(Prefs.hotspotName(ctx)) }
    var secure by remember { mutableStateOf(Prefs.hotspotSecure(ctx)) }
    var pass by remember { mutableStateOf(Prefs.hotspotPass(ctx).ifBlank { Hotspot.generatePassword().also { Prefs.setHotspotPass(ctx, it) } }) }

    val nameOk = Hotspot.validName(name)
    val passOk = !secure || Hotspot.validPassword(pass)
    val qr = remember(name, secure, pass, nameOk, passOk) {
        if (nameOk && passOk) Hotspot.qrBitmap(Hotspot.wifiQrText(name, secure, pass)).asImageBitmap() else null
    }

    SoftCard {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text("Estos datos se guardan solos en la app (no hay botón Guardar).", fontWeight = FontWeight.SemiBold)
            Text("Pero NO cambian el hotspot del celular: eso lo decide Android.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        SettingRow("Recordarme al conectar Android Auto", "Agrega el botón Hotspot a la notificación \"Conectado al carro\"", remind) {
            remind = it; Prefs.setHotspotRemind(ctx, it)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it; if (Hotspot.validName(it)) Prefs.setHotspotName(ctx, it) },
                label = { Text("Nombre de la red") }, singleLine = true, isError = !nameOk, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Hasta 32 caracteres") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = secure, onClick = { secure = true; Prefs.setHotspotSecure(ctx, true) }, label = { Text("Con contraseña (WPA2)") })
                FilterChip(selected = !secure, onClick = { secure = false; Prefs.setHotspotSecure(ctx, false) }, label = { Text("Abierta") })
            }
            if (secure) {
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it; if (Hotspot.validPassword(it)) Prefs.setHotspotPass(ctx, it) },
                    label = { Text("Contraseña") }, singleLine = true, isError = !passOk, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("De 8 a 63 caracteres") },
                )
                TextButton(onClick = { pass = Hotspot.generatePassword().also { Prefs.setHotspotPass(ctx, it) } }) { Text("Generar una contraseña") }
            } else {
                Text("Una red abierta deja que cualquiera cerca use tus datos móviles.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (qr != null) {
                Image(qr, "Código QR de la red", Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)))
                Text("Los pasajeros lo escanean con la cámara para unirse.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Completa nombre y contraseña para ver el QR.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = nameOk, onClick = { Hotspot.copy(ctx, "Red", name); c.toast("Nombre copiado") }) { Text("Copiar nombre") }
                if (secure) OutlinedButton(enabled = passOk, onClick = { Hotspot.copy(ctx, "Contraseña", pass); c.toast("Contraseña copiada") }) { Text("Copiar clave") }
            }
            Button(onClick = { Hotspot.openSettings(ctx) }, Modifier.fillMaxWidth()) { Text("Abrir ajustes del hotspot") }
            Text(
                "Android no deja que una app encienda el hotspot ni cambie su nombre o clave. Configura estos mismos datos una vez en " +
                    "Ajustes → Hotspot; después basta activarlo con un toque (desde la notificación o el panel rápido). " +
                    "Funciona a la vez que Android Auto en celulares con WiFi cliente + hotspot simultáneos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal,
            )
        }
    }
}
