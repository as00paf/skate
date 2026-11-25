package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.LoggerService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files

class DefaultCubeFallbackTest {
    private fun manager(engineRoot: File): AssetsManager {
        val paths = AssetPaths().apply {
            projectRoot = Files.createTempDirectory("project").toFile()
            engineDefaults = EngineDefaultResolver(engineRoot) { projectRoot }
        }
        return AssetsManager(Serializer(paths), mockk<LoggerService>(relaxed = true))
    }

    @Test
    fun DefaultCube_LoadFailureThrowsInsteadOfRecursing() {
        val assets = manager(Files.createTempDirectory("no-engine").toFile())
        assertThrows<RuntimeException> { assets.loadModel(EngineDefaults.Models.CUBE) }
        assertThrows<RuntimeException> { assets.loadModel("engine:Models/missing.obj") }
        assertThrows<RuntimeException> { assets.loadModel(ByteArray(3), "broken.obj") }
    }

    @Test
    fun EngineDefaults_EveryTextureExistsInBundledAssets() {
        val missing = EngineDefaults.Textures.ALL.filter { !File("assets/" + it.removePrefix("engine:")).isFile }
        assertTrue(missing.isEmpty(), "bundled textures missing from assets/: $missing")
    }
}
