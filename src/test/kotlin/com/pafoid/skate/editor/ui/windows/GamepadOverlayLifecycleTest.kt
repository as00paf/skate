package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.core.EventSystem
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GamepadOverlayLifecycleTest {

    @Test
    fun controllerTexture_ProjectClosed_InvalidatesAndReloadsTexture() {
        val assetsManager = mockk<AssetsManager>(relaxed = true)
        every { assetsManager.getTexture(Assets.Textures.XBOX_CONTROLLER) } returnsMany listOf(
            texture(101),
            texture(202)
        )

        val eventSystem = EventSystem()
        val overlay = GamepadOverlay(
            assetsManager = mockk(),
            gamepadListener = mockk(),
            settingsManager = mockk(),
            stringManager = mockk(),
            eventSystem = mockk()
        )
        assertEquals(101, resolveControllerTexture(overlay).texId)

        eventSystem.publish(ProjectEvent.Closed("AnyProject"))
        assertNull(readCachedControllerTexture(overlay))

        assertEquals(202, resolveControllerTexture(overlay).texId)
        verify(exactly = 2) { assetsManager.getTexture(Assets.Textures.XBOX_CONTROLLER) }
    }

    private fun texture(id: Int): Texture = Texture().also { it.texId = id }

    private fun resolveControllerTexture(overlay: GamepadOverlay): Texture {
        val method = GamepadOverlay::class.java.getDeclaredMethod("resolveControllerTexture")
        method.isAccessible = true
        return method.invoke(overlay) as Texture
    }

    private fun readCachedControllerTexture(overlay: GamepadOverlay): Texture? {
        val field = GamepadOverlay::class.java.getDeclaredField("controllerTexture")
        field.isAccessible = true
        return field.get(overlay) as Texture?
    }
}
