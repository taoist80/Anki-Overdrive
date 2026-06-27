package dev.overdrive.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.nav.OverdriveNav
import dev.overdrive.nav.Routes
import dev.overdrive.ui.components.OverdriveBackground
import dev.overdrive.ui.theme.OverdriveTheme
import dev.overdrive.ui.theme.rememberAsset
import kotlinx.coroutines.delay

/**
 * App-start splash: the authentic 4.0.4 OVERDRIVE title (already pink/violet) fades + scales in over the
 * violet nebula, with a load bar that fills, then cross-fades to [Routes.Home]. The boot logo is dropped
 * from the back stack so Back from Home exits rather than returning here.
 */
@Composable
fun SplashScreen(nav: OverdriveNav) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val logo = rememberAsset("ui/overdrive_title.png")
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        delay(1300)                      // hold on the full logo so the splash is clearly seen
        nav.controller.navigate(Routes.Home) { popUpTo(Routes.Splash) { inclusive = true } }
    }
    OverdriveBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (logo != null) {
                    Image(
                        logo, "ANKI OVERDRIVE",
                        Modifier.fillMaxWidth(0.56f).graphicsLayer {
                            alpha = p.value
                            val s = 0.90f + 0.10f * p.value
                            scaleX = s; scaleY = s
                        },
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Text("ANKI OVERDRIVE", fontFamily = font, color = colors.rose, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text("X  ·  REBUILT", fontFamily = font, color = colors.gold, fontSize = 13.sp, letterSpacing = 8.sp,
                    modifier = Modifier.graphicsLayer { alpha = p.value })
                Spacer(Modifier.height(26.dp))
                // load bar fills with the intro
                Box(Modifier.fillMaxWidth(0.22f).height(4.dp).clip(RoundedCornerShape(99.dp)).background(Color(0x18FFFFFF))) {
                    Box(
                        Modifier.fillMaxWidth(p.value).height(4.dp).clip(RoundedCornerShape(99.dp))
                            .background(Brush.horizontalGradient(listOf(colors.rose, colors.blue))),
                    )
                }
            }
        }
    }
}
