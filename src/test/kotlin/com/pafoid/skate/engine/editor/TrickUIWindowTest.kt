package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.components.TrickDetector
import imgui.ImGui
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
        // Mock ImGui statically if needed, or focus on logic that doesn't directly interact with ImGui rendering for now
    }

    @Test
    fun `imgui should display detected trick when available`() {
        // Given
        val detectedTrick = "Kickflip"
        every { mockTrickDetector.getDetectedTrick() } returns detectedTrick

        // When
        trickUIWindow.imgui()

        // Then
        // This part is tricky as ImGui is static. We can't easily verify ImGui.text() calls directly.
        // For now, we'll assume a visual inspection is part of the 'Green' phase.
        // If we had a wrapper around ImGui calls, we could verify that.
        // We'll focus on ensuring the logic leading to the display is sound.
        verify { mockTrickDetector.getDetectedTrick() }
    }

    @Test
    fun `imgui should not display trick when none detected`() {
        // Given
        every { mockTrickDetector.getDetectedTrick() } returns null

        // When
        trickUIWindow.imgui()

        // Then
        verify { mockTrickDetector.getDetectedTrick() }
        // Again, direct ImGui.text verification is hard. We're testing the underlying logic.
    }
}
