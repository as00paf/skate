package com.pafoid.skate.game.ui

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.InteractionFocusChanged
import com.pafoid.skate.engine.gameplay.ActionPresets
import com.pafoid.skate.engine.gameplay.interaction.Interaction
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.ui.UITextComponent
import com.pafoid.skate.game.player.SkateGameplay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-24d: the skate HUD shows the interaction prompt, and the game registers the `interact` action. */
class InteractionPromptHudTest {

    @Test
    fun Hud_ShowsThePromptOfTheFocusedInteractableAndHidesItAfter() {
        val events = EventSystem()
        val hud = SkateHudSystem(events)
        val scene = Scene("Park").apply { isRunning = true; addChild(GameUi.createHud()) }
        hud.init(scene)
        val prompt = scene.children.single().children.single { it.name == GameUi.PROMPT_NAME }.getComponent<UITextComponent>()!!

        events.publish(InteractionFocusChanged(GameObject("Player"), GameObject("Wax"), "prompt.pickup"))
        hud.update(0.016f)
        assertEquals("prompt.pickup", prompt.key)

        events.publish(InteractionFocusChanged(GameObject("Player"), null, null))
        hud.update(0.016f)
        assertEquals("", prompt.key)
    }

    @Test
    fun SkateGameplay_RegistersTheInteractActionWithFAndDpadUp() {
        SkateGameplay.register()
        val map = ActionPresets.applyTo(ActionMap())
        val bindings = map.definition(Interaction.ACTION)!!.defaults
        assertTrue(ActionBinding.Key('F'.code) in bindings)
        assertEquals(2, bindings.size)
    }
}
