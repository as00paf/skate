package com.pafoid.skate.editor.settings

import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.render.shadow.ShadowQualityLevel
import com.pafoid.skate.engine.render.shadow.ShadowSettings
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ShadowQualitySettingTest {

    @TempDir
    lateinit var dir: File

    private val initial = ShadowSettings.quality

    @AfterEach
    fun restore() {
        ShadowSettings.quality = initial
    }

    private fun manager(file: File = File(dir, "editor.config")) =
        EditorSettingsManager(Serializer(), EventSystem(mockk(relaxed = true)), mockk(relaxed = true), mockk(relaxed = true), file)

    @Test
    fun everySettingMapsToTheEngineTierOfTheSameName() {
        assertEquals(ShadowQualityLevel.LOW, ShadowQuality.LOW.toLevel())
        assertEquals(ShadowQualityLevel.MEDIUM, ShadowQuality.MEDIUM.toLevel())
        assertEquals(ShadowQualityLevel.HIGH, ShadowQuality.HIGH.toLevel())
        ShadowQuality.entries.forEach { assertEquals(it, it.toLevel().toSetting()) }
        assertEquals(ShadowQuality.entries.size, ShadowQualityLevel.entries.size)
    }

    @Test
    fun defaultGraphicsSettingsUseTheEngineDefaultTier() {
        assertEquals(ShadowQualityLevel.DEFAULT, GraphicsSettings().shadowQualityLevel)
    }

    @Test
    fun applyToEngineSetsTheShadowTier() {
        GraphicsSettings(shadowQuality = ShadowQuality.HIGH).applyToEngine()
        assertEquals(ShadowQualityLevel.HIGH, ShadowSettings.quality)
        GraphicsSettings(shadowQuality = ShadowQuality.LOW).applyToEngine()
        assertEquals(ShadowQualityLevel.LOW, ShadowSettings.quality)
    }

    @Test
    fun updateShadowQualityAppliesAtOnceAndPersists() {
        val file = File(dir, "editor.config")
        val first = manager(file).apply { loadSettings() }
        first.updateShadowQuality(ShadowQuality.LOW)
        assertEquals(ShadowQualityLevel.LOW, ShadowSettings.quality)

        ShadowSettings.quality = ShadowQualityLevel.HIGH // as if another session left a different tier
        val reloaded = manager(file).apply { loadSettings() }
        assertEquals(ShadowQuality.LOW, reloaded.editorSettings.graphics.shadowQuality)
        assertEquals(ShadowQualityLevel.LOW, ShadowSettings.quality)
    }

    @Test
    fun loadingWithoutASettingsFileAppliesTheDefaultTier() {
        ShadowSettings.quality = ShadowQualityLevel.LOW
        manager().loadSettings()
        assertEquals(ShadowQualityLevel.DEFAULT, ShadowSettings.quality)
    }
}
