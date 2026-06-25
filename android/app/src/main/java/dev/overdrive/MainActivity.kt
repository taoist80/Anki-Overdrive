package dev.overdrive

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private lateinit var car: AnkiCar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        car = AnkiCar(this)
        setContent { MaterialTheme { CarScreen(car) } }
    }
}

private val BLE_PERMS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

@Composable
fun CarScreen(car: AnkiCar) {
    var hasPerms by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPerms = result.values.all { it } }

    LaunchedEffect(Unit) { launcher.launch(BLE_PERMS) }

    var speed by remember { mutableStateOf(0f) }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp).fillMaxSize()) {
            Text("OverdriveX — BLE PoC", style = MaterialTheme.typography.headlineSmall)
            Text("State: ${car.state}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))

            if (!hasPerms) {
                Text("Bluetooth permissions are required to find your cars.")
                Button(onClick = { launcher.launch(BLE_PERMS) }) { Text("Grant Bluetooth permissions") }
            } else when (car.state) {
                ConnState.Disconnected, ConnState.Scanning -> {
                    Button(onClick = { car.startScan() }, enabled = car.state != ConnState.Scanning) {
                        Text(if (car.state == ConnState.Scanning) "Scanning…" else "Scan for cars")
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        items(car.devices) { d ->
                            ListItem(
                                headlineContent = { Text(d.name) },
                                supportingContent = { Text("${d.address}   ${d.rssi} dBm") },
                                trailingContent = { Button(onClick = { car.connect(d) }) { Text("Connect") } },
                            )
                        }
                    }
                }
                ConnState.Connecting -> { CircularProgressIndicator(); Text("Connecting…") }
                ConnState.Connected -> {
                    val t = car.telemetry
                    Text("Connected   v=${t.version ?: "?"}   batt=${t.battery ?: "?"}")
                    t.position?.let {
                        Text("piece=${it.roadPieceId} loc=${it.locationId} off=${"%.0f".format(it.offsetMm)} spd=${it.speedMmPerSec}")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Speed: ${speed.toInt()} mm/s")
                    Slider(
                        value = speed,
                        onValueChange = { speed = it; car.setSpeed(it.toInt()) },
                        valueRange = 0f..1200f,
                    )
                    Row {
                        Button(onClick = { car.changeLane(-44f) }) { Text("◀ Left") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { car.changeLane(44f) }) { Text("Right ▶") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { speed = 0f; car.stop() }) { Text("Stop") }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { car.disconnect() }) { Text("Disconnect") }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Log", style = MaterialTheme.typography.titleSmall)
            Text(
                car.telemetry.log,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            )
        }
    }
}
