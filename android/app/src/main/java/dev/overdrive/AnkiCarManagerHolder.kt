package dev.overdrive

/**
 * Process-wide handle to the single [CarManager]. Set once by MainActivity; read by the BLE Lab
 * destination (and, later, the RaceEngine). A lightweight stand-in until a proper DI/AppContainer
 * lands in Phase 2.
 */
object AnkiCarManagerHolder {
    @Volatile var instance: CarManager? = null

    fun require(): CarManager =
        instance ?: error("CarManager not initialized — MainActivity.onCreate must set it first")
}
