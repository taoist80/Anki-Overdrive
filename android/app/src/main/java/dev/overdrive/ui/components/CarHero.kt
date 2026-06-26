package dev.overdrive.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.overdrive.CarType
import dev.overdrive.ui.theme.OverdriveTheme
import dev.overdrive.ui.theme.rememberAsset

/** Centered car portrait + name + series + ATTACK/SUPPORT stat bars. The garage centerpiece. */
@Composable
fun CarHero(car: CarType, modifier: Modifier = Modifier) {
    val colors = OverdriveTheme.colors
    val font = OverdriveTheme.font
    val sprite = rememberAsset(car.spriteFile?.let { "cars/$it" })
    val logo = rememberAsset(car.logoAsset)
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (sprite != null) {
                Image(sprite, car.name, Modifier.fillMaxWidth(0.62f), contentScale = ContentScale.Fit)
            } else {
                Text(car.name, fontFamily = font, color = colors.textDim, fontSize = 44.sp)
            }
        }
        // Prefer the branded 4.0.4 wordmark; fall back to the two-tone racing name.
        if (logo != null) {
            Image(logo, car.name, Modifier.fillMaxWidth(0.62f).heightIn(max = 64.dp), contentScale = ContentScale.Fit)
        } else {
            RacingName(car.name, fontSize = 40)
        }
        car.series?.let {
            Text(it.uppercase(), fontFamily = font, color = colors.gold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            StatBars("ATTACK", car.attackBays)
            StatBars("SUPPORT", car.supportBays)
        }
        Spacer(Modifier.height(20.dp))
    }
}
