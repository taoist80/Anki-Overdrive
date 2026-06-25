package dev.overdrive.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

/**
 * Thin navigation facade handed to every screen. Screens express intent with the type-safe
 * [Routes] objects (`nav.go(Routes.GarageHome)`) and never touch NavController internals; overlays
 * (the Foreground modal layer) go through the same object.
 */
@Stable
class OverdriveNav(
    val controller: NavHostController,
    val overlays: OverlayController,
) {
    fun go(route: Any) {
        controller.navigate(route)
    }

    fun back() {
        controller.popBackStack()
    }

    /** Exit any flow back to a single Home at the root of the stack. */
    fun home() {
        controller.navigate(Routes.Home) {
            popUpTo(Routes.Home) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun showOverlay(overlay: Overlay) = overlays.show(overlay)
    fun dismissOverlay() = overlays.dismiss()
}

@Composable
fun rememberOverdriveNav(
    controller: NavHostController = rememberNavController(),
    overlays: OverlayController = rememberOverlayController(),
): OverdriveNav = remember(controller, overlays) { OverdriveNav(controller, overlays) }
