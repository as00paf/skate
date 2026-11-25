package com.pafoid.skate.game.ui

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.EcsChanges
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.events.InteractionFocusChanged
import com.pafoid.skate.engine.events.TrickCompleted
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UIProgressBarComponent
import com.pafoid.skate.engine.ui.UITextComponent
import com.pafoid.skate.game.input.SkateControls
import com.pafoid.skate.game.player.ComboMove
import com.pafoid.skate.game.player.ComboOutcome
import com.pafoid.skate.game.player.SkateBehavior
import com.pafoid.skate.game.player.TrickType
import org.joml.Vector4f

/**
 * The skate HUD in editor Play and the exported game (GPL-21, GPL-23 §6), written into the scene's HUD texts by name:
 *
 * - [TRICK_TEXT]: the trick in the air with its body spin ("FS 180 Kickflip"), then the one just landed for [showSec].
 * - [COMBO_TEXT]: the combo chain and its trick count ("Kickflip + 50-50 + Manual (3)"); it stays [showSec] after the
 *   combo lands, and shows "Bail!" in red when it is lost. No points or score yet.
 * - [BALANCE_METER]: the manual balance (a progress bar, half full when balanced), shown only during a manual.
 * - [HINT_TEXT]: what the rider can do now in the selected control scheme ([ControlHints]).
 * - [PROMPT_TEXT]: the interaction prompt from [InteractionFocusChanged] (GPL-24d).
 *
 * Each landed trick publishes [TrickCompleted] (score 0). Names come from the game strings through [string], so the
 * texts follow the language. A new scene (Play, Restart) starts a new run.
 */
class SkateHudSystem(
    private val events: EventSystem? = null,
    private val showSec: Float = 1.5f,
    private val string: (key: String, args: Array<out Any>) -> String = { key, _ -> key },
) : System(priority = ExecutionPriority.DEFAULT) {

    /** What the texts show right now: key and args ("" key hides a text). */
    data class Line(val key: String = "", val args: List<String> = emptyList())

    var promptKey = ""
        private set
    var trickLine = Line()
        private set
    var comboLine = Line()
        private set
    var hintKey = ""
        private set
    /** Manual balance meter fill (0..1), null while hidden. */
    var balanceFill: Float? = null
        private set

    private var seenLanded = 0
    private var seenResults = 0
    private var rider: SkateBehavior? = null
    private var trickShownFor = 0f
    private var comboShownFor = 0f
    private var comboBailed = false

    private val riders = query(PlayerController::class)
    private val texts = HashMap<String, MutableList<UITextComponent>>()
    private val meters = mutableListOf<Pair<UIElementComponent, UIProgressBarComponent>>()
    private var textsVersion = -1L

    init {
        events?.subscribe<InteractionFocusChanged> { promptKey = it.promptKey.orEmpty() }
    }

    override fun init(scene: Scene) {
        super.init(scene)
        promptKey = ""
        trickLine = Line()
        comboLine = Line()
        hintKey = ""
        balanceFill = null
        rider = null
        trickShownFor = 0f
        comboShownFor = 0f
        comboBailed = false
        textsVersion = -1L
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        val behavior = findRider()
        if (behavior == null) {
            hintKey = ""
            balanceFill = null
            return writeTexts()
        }
        if (behavior !== rider) {
            rider = behavior
            seenLanded = behavior.landedTricks
            seenResults = behavior.combo.resultCount
        }
        updateTrick(behavior, dt)
        updateCombo(behavior, dt)
        hintKey = ControlHints.key(SkateControls.scheme, ControlHints.situation(behavior))
        balanceFill = behavior.manualBalance?.let { (it + 1f) / 2f }
        writeTexts()
    }

    private fun updateTrick(behavior: SkateBehavior, dt: Float) {
        val inAir = behavior.trick
        val spin = behavior.airSpin
        when {
            behavior.landedTricks > seenLanded -> {
                seenLanded = behavior.landedTricks
                behavior.lastLanded?.let { landed ->
                    trickLine = line(landed)
                    trickShownFor = 0f
                    events?.publish(TrickCompleted(landed.trick.nameKey, 0, 1f))
                }
            }
            inAir != null && (inAir != TrickType.OLLIE || spin != null) -> {
                trickLine = line(ComboMove.Air(inAir, spin))
                trickShownFor = 0f
            }
            trickLine.key.isNotEmpty() -> {
                trickShownFor += dt
                if (trickShownFor >= showSec) trickLine = Line()
            }
        }
    }

    private fun updateCombo(behavior: SkateBehavior, dt: Float) {
        val combo = behavior.combo
        val result = combo.lastResult
        when {
            combo.moves.isNotEmpty() -> {
                comboLine = chainLine(combo.moves)
                comboBailed = false
                comboShownFor = 0f
                seenResults = combo.resultCount
            }
            combo.resultCount > seenResults && result != null -> {
                seenResults = combo.resultCount
                comboBailed = result.outcome == ComboOutcome.BAILED
                comboLine = if (comboBailed) Line(BAIL_KEY) else chainLine(result.moves)
                comboShownFor = 0f
            }
            comboLine.key.isNotEmpty() -> {
                comboShownFor += dt
                if (comboShownFor >= showSec) comboLine = Line()
            }
        }
    }

    private fun line(move: ComboMove) = Line(LINE_KEY, listOf(move.label(string)))

    private fun chainLine(moves: List<ComboMove>) =
        Line(COMBO_KEY, listOf(moves.joinToString(" + ") { it.label(string) }, moves.size.toString()))

    /** The first skate rider anywhere in the scene, e.g. a skater parented under its board (GPL-25). */
    private fun findRider(): SkateBehavior? =
        riders.entities.firstNotNullOfOrNull { it.getComponent<PlayerController>()?.behavior as? SkateBehavior }

    private fun writeTexts() {
        if (textsVersion != EcsChanges.version) {
            textsVersion = EcsChanges.version
            texts.clear()
            meters.clear()
            collect(scene)
        }
        write(TRICK_TEXT, trickLine)
        write(COMBO_TEXT, comboLine)
        write(HINT_TEXT, Line(hintKey))
        write(PROMPT_TEXT, Line(promptKey))
        texts[COMBO_TEXT]?.forEach { it.color.set(if (comboBailed) BAIL_COLOR else NORMAL_COLOR) }
        val fill = balanceFill
        meters.forEach { (element, bar) ->
            element.visible = fill != null
            if (fill != null) bar.value = fill
        }
    }

    private fun write(name: String, line: Line) {
        texts[name]?.forEach { text ->
            text.key = line.key
            if (text.args != line.args) text.args = line.args
        }
    }

    /** Caches the HUD widgets; walked again only after the scene tree changes (GPL-25). */
    private fun collect(root: GameObject) {
        for (child in root.children) {
            when (child.name) {
                TRICK_TEXT, COMBO_TEXT, HINT_TEXT, PROMPT_TEXT ->
                    child.getComponent<UITextComponent>()?.let { texts.getOrPut(child.name) { mutableListOf() } += it }
                BALANCE_METER -> {
                    val element = child.getComponent<UIElementComponent>()
                    val bar = child.getComponent<UIProgressBarComponent>()
                    if (element != null && bar != null) meters += element to bar
                }
            }
            collect(child)
        }
    }

    companion object {
        const val TRICK_TEXT = GameUi.TRICK_NAME
        const val COMBO_TEXT = GameUi.COMBO_NAME
        const val HINT_TEXT = GameUi.HINT_NAME
        const val PROMPT_TEXT = GameUi.PROMPT_NAME
        const val BALANCE_METER = GameUi.BALANCE_NAME

        /** Game strings: one resolved name, the chain with its count, and the lost combo. */
        const val LINE_KEY = "hud.line"
        const val COMBO_KEY = "hud.comboLine"
        const val BAIL_KEY = "hud.bail"

        private val NORMAL_COLOR = Vector4f(1f, 1f, 1f, 1f)
        private val BAIL_COLOR = Vector4f(0.95f, 0.25f, 0.2f, 1f)
    }
}
