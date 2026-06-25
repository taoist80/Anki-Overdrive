package dev.overdrive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        enableEdgeToEdge()
        setContent { OverdriveApp() }
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
