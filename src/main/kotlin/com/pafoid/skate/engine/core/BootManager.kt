package com.pafoid.skate.engine.core

import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.physics3d.native.NativeLibraryLoader
import java.util.concurrent.atomic.AtomicReference

class BootManager(
    private val logger: LoggerService,
    private val audioEngine: AudioEngine,
    private val settingsManager: SettingsManager,
    private val nativeLibraryLoader: NativeLibraryLoader,
) {
    suspend fun boot(engineState: AtomicReference<EngineState>) {
        logger.log("Initializing Engine...")
        engineState.set(EngineState.LOADING)

        nativeLibraryLoader.loadNativeLibrary()
        audioEngine.init()
        settingsManager.load()

        engineState.set(EngineState.RUNNING)
        logger.log("Engine initialization complete.")
    }
}
