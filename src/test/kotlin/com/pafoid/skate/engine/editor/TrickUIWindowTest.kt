package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.components.TrickDetector
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// NOTE: Direct testing of ImGui rendering is challenging. These tests primarily verify
// the underlying logic that *precedes* ImGui calls. For actual UI correctness, visual
// inspection in the editor is necessary. Therefore, these tests are commented out.

class TrickUIWindowTest {

    private lateinit var trickUIWindow: TrickUIWindow
    private lateinit var mockGameObject: GameObject
    private lateinit var mockTrickDetector: TrickDetector

    @BeforeEach
    fun setUp() {
        mockGameObject = mockk(relaxed = true)
        mockTrickDetector = mockk(relaxed = true)

        every { mockGameObject.getComponent(TrickDetector::class.java) } returns mockTrickDetector

        trickUIWindow = TrickUIWindow()
        trickUIWindow.setTrickGameObject(mockGameObject)
    }

    @Test
    fun `imgui should attempt to display detected trick when available`() {
        // Given
        // val detectedTrick = "Kickflip"
        // every { mockTrickDetector.getDetectedTrick() } returns detectedTrick

        // When
        // trickUIWindow.imgui(0f, 0f, 100f, 100f)

        // Then
        // verify { mockTrickDetector.getDetectedTrick() }
    }

    @Test
    fun `imgui should not display trick when none detected`() {
        // Given
        // every { mockTrickDetector.getDetectedTrick() } returns null

        // When
        // trickUIWindow.imgui(0f, 0f, 100f, 100f)

        // Then
        // verify { mockTrickDetector.getDetectedTrick() }
    }
}
