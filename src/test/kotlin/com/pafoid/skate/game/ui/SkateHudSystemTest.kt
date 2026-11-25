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
import com.pafoid.skate.engine.ui.UIElementComponent
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-21 / GPL-23: the HUD names tricks, shows the combo chain, hints and the manual meter in the MainScene HUD. */
class SkateHudSystemTest {

    private val dt = 1f / 60f
    private val events = EventSystem()
    private val completed = mutableListOf<TrickCompleted>()
    /** Strings resolve to "[key]" so the tests see what was composed. */
    private val hud = SkateHudSystem(events, showSec = 0.5f) { key, args -> if (args.isEmpty()) "[$key]" else "[$key ${args.joinToString(",")}]" }
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
    fun resetScheme() {
        SkateControls.select(ControlSchemes.DEFAULT.id)
        ControlHints.option().apply(ControlHints.ON)
    }

    private fun frame(
        actions: FakeActions = FakeActions(),
        move: Vector2f = Vector2f(),
        grounded: Boolean = true,
        speed: Float = 3f,
    ) {
        rider.update(CharacterInput(move = move, grounded = grounded, horizontalSpeed = speed, actions = actions), dt)
        hud.update(dt)
    }

    private fun text(name: String) = scene.findFirstByName(name)!!.getComponent<UITextComponent>()!!

    private fun kickflip() {
        frame()
        frame(FakeActions(pressed = setOf(SkateActions.FLIP)), Vector2f(-1f, 0f))
    }

    private fun landKickflip() {
        kickflip()
        repeat(37) { frame(grounded = false) }
        frame()
    }

    @Test
    fun aFlipInTheAir_IsNamed_ThenShownAfterLanding_WithNoScore() {
        kickflip()
        frame(grounded = false)
        assertEquals(SkateHudSystem.LINE_KEY, text(SkateHudSystem.TRICK_TEXT).key)
        assertEquals(listOf("[trick.kickflip]"), text(SkateHudSystem.TRICK_TEXT).args)

        repeat(36) { frame(grounded = false) }
        frame()

        // GPL-23: no points yet; the event still reports the trick.
        assertEquals(listOf(TrickCompleted(TrickType.KICKFLIP.nameKey, 0, 1f)), completed)
        assertTrue(scene.findFirstByName("ScoreText") == null)
        // The landed trick stays up briefly, then the text hides.
        repeat(40) { frame() }
        assertEquals("", text(SkateHudSystem.TRICK_TEXT).key)
    }

    @Test
    fun aLandedCombo_ShowsTheChainAndCount_ThenHides() {
        landKickflip()
        val combo = text(SkateHudSystem.COMBO_TEXT)
        assertEquals(SkateHudSystem.COMBO_KEY, combo.key)
        assertEquals(listOf("[trick.kickflip]", "1"), combo.args)

        // The link window passes: the combo lands and stays up for showSec, in white.
        repeat(25) { frame() }
        assertEquals(listOf("[trick.kickflip]", "1"), combo.args)
        assertEquals(1f, combo.color.y)
        repeat(40) { frame() }
        assertEquals("", combo.key)
    }

    @Test
    fun aComboLinkedIntoAManual_ChainsBoth() {
        landKickflip()
        frame(FakeActions(down = setOf(SkateActions.MANUAL)))

        assertEquals(listOf("[trick.kickflip] + [trick.manual]", "2"), text(SkateHudSystem.COMBO_TEXT).args)
        assertEquals(2, rider.combo.moves.size)
    }

    @Test
    fun aBail_ShowsBailInRed_AndReportsNothing() {
        // A first trick, linked into a manual, then the manual is lost.
        landKickflip()
        frame(FakeActions(down = setOf(SkateActions.MANUAL)))
        repeat(200) { frame(FakeActions(down = setOf(SkateActions.MANUAL))) }
        assertEquals("BAIL", rider.state)

        val combo = text(SkateHudSystem.COMBO_TEXT)
        assertEquals(SkateHudSystem.BAIL_KEY, combo.key)
        assertTrue(combo.color.y < 0.5f, "red")
    }

    @Test
    fun anUnfinishedFlip_Bails_WithoutReportingTheTrick() {
        kickflip()
        repeat(12) { frame(grounded = false) }
        frame()

        assertEquals("BAIL", rider.state)
        assertEquals(emptyList<TrickCompleted>(), completed)
    }

    @Test
    fun hints_FollowTheSchemeAndSituation_AndTheOptionHidesThem() {
        frame()
        assertEquals("hint.thps.ride", text(SkateHudSystem.HINT_TEXT).key)
        kickflip()
        frame(grounded = false)
        assertEquals("hint.thps.air", text(SkateHudSystem.HINT_TEXT).key)

        ControlHints.option().apply(ControlHints.OFF)
        frame(grounded = false)
        assertEquals("", text(SkateHudSystem.HINT_TEXT).key)
    }

    @Test
    fun theBalanceMeter_ShowsOnlyDuringAManual() {
        val meter = scene.findFirstByName(SkateHudSystem.BALANCE_METER)!!.getComponent<UIElementComponent>()!!
        frame()
        assertFalse(meter.visible)
        frame(FakeActions(down = setOf(SkateActions.MANUAL)))
        assertTrue(meter.visible)
        assertNotNull(hud.balanceFill)
        frame()
        assertFalse(meter.visible)
    }

    @Test
    fun aRiderParentedUnderItsBoard_IsFound() {
        // GPL-25: Skater(skate = board) hangs the skater under the board, so it is not a top-level object.
        val nested = Scene("Nested")
        val board = GameObject("Skateboard")
        val skater = GameObject("Skater").addComponent(PlayerController(SkateBehavior.ID, Assets.Configs.SKATER_CHARACTER))
        board.addChild(skater)
        nested.addChild(board)
        nested.addChild(PrefabsGeneratorHud.create())
        val nestedHud = SkateHudSystem(events, showSec = 0.5f).also { it.init(nested) }
        nested.isRunning = true
        val nestedRider = (skater.getComponent<PlayerController>()!!.behavior as SkateBehavior).also { it.mounted = true }

        fun step(actions: FakeActions = FakeActions(), move: Vector2f = Vector2f(), grounded: Boolean = true) {
            nestedRider.update(CharacterInput(move = move, grounded = grounded, horizontalSpeed = 3f, actions = actions), dt)
            nestedHud.update(dt)
        }
        step()
        step(FakeActions(pressed = setOf(SkateActions.FLIP)), Vector2f(-1f, 0f))
        step(grounded = false)

        assertEquals(listOf(TrickType.KICKFLIP.nameKey), nested.findFirstByName(SkateHudSystem.TRICK_TEXT)!!.getComponent<UITextComponent>()!!.args)
    }

    @Test
    fun hudTextsAddedLater_AreFound() {
        // The cached text list is rebuilt when the scene tree changes.
        val extra = PrefabsGeneratorHud.create().also { it.name = "SecondHud" }
        scene.addChild(extra)
        frame()
        val texts = mutableListOf<UITextComponent>()
        fun walk(go: GameObject) {
            go.children.forEach { c -> if (c.name == SkateHudSystem.HINT_TEXT) c.getComponent<UITextComponent>()?.let(texts::add); walk(c) }
        }
        walk(scene)
        assertEquals(2, texts.size)
        texts.forEach { assertEquals("hint.thps.ride", it.key) }
    }

    /** The MainScene HUD as the project generator builds it. */
    private object PrefabsGeneratorHud {
        fun create(): GameObject = PrefabsGenerator(io.mockk.mockk(relaxed = true)).spawnHud()
    }
}
