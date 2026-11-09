package com.pafoid.skate.game.ui

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.events.TrickCompleted
import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UITextComponent
import com.pafoid.skate.game.input.ControlSchemes
import com.pafoid.skate.game.input.FakeActions
import com.pafoid.skate.game.input.SkateActions
import com.pafoid.skate.game.input.SkateControls
import com.pafoid.skate.game.player.SkateBehavior
import com.pafoid.skate.game.player.SkateGameplay
import com.pafoid.skate.game.player.TrickType
import org.joml.Vector2f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** GPL-21: the HUD names tricks and scores the landed ones, in the MainScene HUD. */
class SkateHudSystemTest {

    private val dt = 1f / 60f
    private val events = EventSystem()
    private val completed = mutableListOf<TrickCompleted>()
    private val hud = SkateHudSystem(events, showSec = 0.5f)
    private val scene = Scene("MainScene")
    private val rider: SkateBehavior

    init {
        SkateGameplay.register()
        SkateControls.select(ControlSchemes.THPS.id)
        events.subscribe<TrickCompleted> { completed += it }
        val skater = GameObject("Skater").addComponent(PlayerController(SkateBehavior.ID, Assets.Configs.SKATER_CHARACTER))
        rider = (skater.getComponent<PlayerController>()!!.behavior as SkateBehavior).also { it.mounted = true }
        scene.addChild(skater)
        scene.addChild(PrefabsGeneratorHud.create())
        hud.init(scene)
        scene.isRunning = true
    }

    @AfterEach
    fun resetScheme() = SkateControls.select(ControlSchemes.DEFAULT.id)

    private fun frame(actions: FakeActions = FakeActions(), move: Vector2f = Vector2f(), grounded: Boolean = true) {
        rider.update(CharacterInput(move = move, grounded = grounded, horizontalSpeed = 3f, actions = actions), dt)
        hud.update(dt)
    }

    private fun text(name: String) = scene.findFirstByName(name)!!.getComponent<UITextComponent>()!!

    private fun kickflip() {
        frame()
        frame(FakeActions(pressed = setOf(SkateActions.FLIP)), Vector2f(-1f, 0f))
    }

    @Test
    fun aFlipInTheAir_IsNamed_ThenScoredOnLanding() {
        kickflip()
        frame(grounded = false)
        assertEquals(TrickType.KICKFLIP.nameKey, text(SkateHudSystem.TRICK_TEXT).key)

        repeat(36) { frame(grounded = false) }
        frame()

        val points = rider.config.tricks.points(TrickType.KICKFLIP)
        assertEquals(points, hud.score)
        assertEquals(listOf(points.toString()), text(SkateHudSystem.SCORE_TEXT).args)
        assertEquals(listOf(TrickCompleted(TrickType.KICKFLIP.nameKey, points, 1f)), completed)
        // The landed trick stays up briefly, then the text hides.
        repeat(40) { frame() }
        assertEquals("", text(SkateHudSystem.TRICK_TEXT).key)

        // A new scene (Play again, Restart) starts a new run.
        hud.init(scene)
        hud.update(dt)
        assertEquals(0, hud.score)
        assertEquals(listOf("0"), text(SkateHudSystem.SCORE_TEXT).args)
    }

    @Test
    fun aBail_ScoresNothing() {
        kickflip()
        repeat(12) { frame(grounded = false) }
        frame()

        assertEquals("BAIL", rider.state)
        assertEquals(0, hud.score)
        assertEquals(emptyList<TrickCompleted>(), completed)
    }

    /** The MainScene HUD as the project generator builds it (holds TrickText and ScoreText). */
    private object PrefabsGeneratorHud {
        fun create(): GameObject = PrefabsGenerator(io.mockk.mockk(relaxed = true)).spawnHud()
    }
}
