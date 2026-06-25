package dev.overdrive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.overdrive.nav.OverdriveNavHost
import dev.overdrive.nav.OverlayHost
import dev.overdrive.nav.rememberOverdriveNav
import dev.overdrive.ui.theme.OverdriveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnkiCarManagerHolder.instance = CarManager(this)
        requestBlePermissions()
        enableEdgeToEdge()
        setContent { OverdriveApp() }
    }

    private fun requestBlePermissions() {
        val needed = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
    }
}

@Composable
fun OverdriveApp() {
    OverdriveTheme {
        val nav = rememberOverdriveNav()
        Box(Modifier.fillMaxSize()) {
            OverdriveNavHost(nav)        // Background + NavStack layers
            OverlayHost(nav.overlays)    // Foreground (modal) layer
        }
    }
}
