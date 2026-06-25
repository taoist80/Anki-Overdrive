package dev.overdrive

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BLE_PERMS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(mgr: CarManager) {
    var hasPerms by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { r -> hasPerms = r.values.all { it } }
    LaunchedEffect(Unit) { launcher.launch(BLE_PERMS) }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp).fillMaxSize()) {
            Text("BLE multi-car lab", style = MaterialTheme.typography.titleMedium)
            Text("Connected: ${mgr.cars.count { it.state == CarState.Connected }}/${mgr.maxCars}",
                style = MaterialTheme.typography.bodySmall)

            if (!hasPerms) {
                Button(onClick = { launcher.launch(BLE_PERMS) }) { Text("Grant Bluetooth permissions") }
                return@Column
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { mgr.startScan() }, enabled = !mgr.scanning) {
                    Text(if (mgr.scanning) "Scanning…" else "Scan")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { mgr.disconnectAll() }) { Text("Disconnect all") }
                Spacer(Modifier.width(12.dp))
                Text("Drive-all", style = MaterialTheme.typography.bodySmall)
                Switch(checked = mgr.stress, onCheckedChange = { mgr.changeStress(it) })
            }

            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Interval:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Prio.values().forEach { p ->
                    FilterChip(selected = mgr.priority == p, onClick = { mgr.changePriority(p) },
                        label = { Text(p.label, fontSize = 11.sp) })
                }
            }

            if (mgr.found.isNotEmpty()) {
                Text("Found:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                mgr.found.forEach { d ->
                    ListItem(
                        headlineContent = { Text(d.name) },
                        supportingContent = { Text("${d.address}  ${d.rssi} dBm", fontSize = 11.sp) },
                        trailingContent = { Button(onClick = { mgr.connect(d) }) { Text("Connect") } },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            mgr.cars.forEach { c ->
                Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text("${c.name}  •  ${c.state}", style = MaterialTheme.typography.bodyMedium)
                        Text("uptime ${c.uptimeS}s   drops ${c.drops}   last: ${if (c.lastStatus < 0) "—" else statusName(c.lastStatus)}",
                            fontSize = 11.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Log", style = MaterialTheme.typography.labelMedium)
            Text(mgr.log, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
        }
    }
}
