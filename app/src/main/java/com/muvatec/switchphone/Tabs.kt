package com.muvatec.switchphone

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Celulares", Icons.Filled.Smartphone),
    SETTINGS("Ajustes", Icons.Filled.Tune),
    MONITOR("Monitor", Icons.Filled.Speed),
    TOOLS("Herramientas", Icons.Filled.Build),
}

data class NameField(val item: String, val title: String, val current: String)

/** What every tab needs from the host screen. */
class Ctl(
    val ctx: Context,
    val client: DongleClient,
    val state: DongleState?,
    val act: (String, () -> Unit) -> Unit,
    val refresh: () -> Unit,
    val toast: (String) -> Unit,
    val editName: (NameField) -> Unit,
    val requestRestart: () -> Unit,
)

@Composable
fun ScreenTitle(title: String, subtitle: String, onRefresh: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onRefresh != null) IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Actualizar", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
