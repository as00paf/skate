package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import com.pafoid.skate.engine.ecs.config.ExecutionPriority
import com.pafoid.skate.engine.ecs.scene.SceneInitializer
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertSame

/**
 * Unit tests for EnvironmentSystem with component-based architecture.
 *
 * Tests cover:
 * - System initialization
 * - EnvironmentComponent creation on Scene
 * - Preset application to Scene's component
 * - Reset functionality
 * - ImGui integration (smoke test)
 */
class EnvironmentSystemTest {

    // Mock dependencies
    private val stringManager: StringManager = mockk()
    private val sceneInitializer: SceneInitializer = mockk()

    // Test scene for component tests
    private fun createTestScene(): Scene {
        return Scene("TestScene", sceneInitializer)
    }

    // =========================================================================
    // INITIALIZATION TESTS
    // =========================================================================

    @Test
    fun `EnvironmentSystem initializes without config`() {
        // Arrange & Act
        val system = EnvironmentSystem(stringManager = stringManager)

        // Assert
        assertNotNull(system, "System should be created")
    }

    @Test
    fun `EnvironmentSystem has EARLY execution priority`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)

        // Act
        val priority = system.priority

        // Assert
        assertEquals(ExecutionPriority.EARLY, priority, "System should run EARLY")
    }

    // =========================================================================
    // COMPONENT CREATION TESTS
    // =========================================================================

    @Test
    fun `update when Scene has no environment component creates component on Scene`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)
        val scene = createTestScene()
        system.init(scene)

        // Act
        system.update(0.016f)

        // Assert
        val component = scene.getComponent<EnvironmentComponent>()
        assertNotNull(component, "Component should be created during update")
        assertTrue(scene.hasComponent<EnvironmentComponent>(), "Scene should have component")
    }

    @Test
    fun `applyPreset uses existing component from Scene`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)
        val scene = createTestScene()
        val existingComponent = EnvironmentComponent()
        scene.addComponent(existingComponent)
        system.init(scene)

        // Act
        system.applyPreset(EnvironmentPreset.NO_FOG)

        // Assert
        val component = scene.getComponent<EnvironmentComponent>()
        assertSame(existingComponent, component, "Should use existing component")
        assertEquals(0.0f, existingComponent.fogDensity, 0.0001f)
    }

    // =========================================================================
    // PRESET APPLICATION TESTS
    // =========================================================================

    @Test
    fun `applyPreset creates component and applies preset to Scene`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)
        val scene = createTestScene()
        system.init(scene)

        // Act
        system.applyPreset(EnvironmentPreset.FOGGY)

        // Assert
        val component = scene.getComponent<EnvironmentComponent>()!!
        assertNotNull(component, "Component should be created")
        assertEquals(0.05f, component.fogDensity, 0.001f)
    }

    @Test
    fun `applyPreset CLEAR_DAY sets correct values`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)
        val scene = createTestScene()
        system.init(scene)

        // Act
        system.applyPreset(EnvironmentPreset.CLEAR_DAY)

        // Assert
        val component = scene.getComponent<EnvironmentComponent>()!!
        assertEquals(0.6f, component.skyColor.x, 0.0001f)
        assertEquals(0.7f, component.skyColor.y, 0.0001f)
        assertEquals(0.9f, component.skyColor.z, 0.0001f)
        assertEquals(0.0008f, component.fogDensity, 0.0001f)
    }

    @Test
    fun `applyPreset SUNSET sets warm colors`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)
        val scene = createTestScene()
        system.init(scene)

        // Act
        system.applyPreset(EnvironmentPreset.SUNSET)

        // Assert
        val component = scene.getComponent<EnvironmentComponent>()!!
        assertEquals(0.9f, component.skyColor.x, 0.0001f)
        assertEquals(0.5f, component.skyColor.y, 0.0001f)
        assertEquals(0.3f, component.skyColor.z, 0.0001f)
    }

    @Test
    fun `applyPreset NO_FOG disables fog`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)
        val scene = createTestScene()
        system.init(scene)

        // Act
        system.applyPreset(EnvironmentPreset.NO_FOG)

        // Assert
        val component = scene.getComponent<EnvironmentComponent>()!!
        assertEquals(0.0f, component.fogDensity, 0.0001f)
    }

    // =========================================================================
    // RESET FUNCTIONALITY TESTS
    // =========================================================================

    @Test
    fun `reset restores Scene's component to default values`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)
        val scene = createTestScene()
        system.init(scene)
        system.applyPreset(EnvironmentPreset.FOGGY)

        // Act
        system.reset()

        // Assert
        val component = scene.getComponent<EnvironmentComponent>()!!
        assertEquals(0.0f, component.fogDensity, 0.0001f)
        assertEquals(1.5f, component.fogGradient, 0.0001f)
    }

    @Test
    fun `reset does nothing if Scene has no component`() {
        // Arrange
        val system = EnvironmentSystem(stringManager = stringManager)
        val scene = createTestScene()
        system.init(scene)

        // Act - should not throw
        system.reset()

        // Assert - still no component
        assertFalse(scene.hasComponent<EnvironmentComponent>())
    }

    // =========================================================================
    // COMPONENT PROPERTY TESTS
    // =========================================================================

    @Test
    fun `EnvironmentComponent renderSky and renderFog default to true`() {
        // Arrange & Act
        val component = EnvironmentComponent()

        // Assert
        assertTrue(component.renderSky, "renderSky should default to true")
        assertTrue(component.renderFog, "renderFog should default to true")
    }

    @Test
    fun `EnvironmentComponent reset restores render toggles to true`() {
        // Arrange
        val component = EnvironmentComponent().apply {
            renderSky = false
            renderFog = false
        }

        // Act
        component.reset()

        // Assert
        assertTrue(component.renderSky)
        assertTrue(component.renderFog)
    }

    @Test
    fun `EnvironmentComponent renderSky and renderFog can be toggled independently`() {
        // Arrange
        val component = EnvironmentComponent()

        // Act & Assert - toggle sky only
        component.renderSky = false
        component.renderFog = true
        assertFalse(component.renderSky)
        assertTrue(component.renderFog)

        // Act & Assert - toggle fog only
        component.renderSky = true
        component.renderFog = false
        assertTrue(component.renderSky)
        assertFalse(component.renderFog)

        // Act & Assert - toggle both
        component.renderSky = false
        component.renderFog = false
        assertFalse(component.renderSky)
        assertFalse(component.renderFog)
    }
}
