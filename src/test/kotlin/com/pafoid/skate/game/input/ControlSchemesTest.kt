package com.pafoid.skate.game.input

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringTables
import com.pafoid.skate.engine.gameplay.ActionPresets
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** GPL-21: the four scheme definitions. */
class ControlSchemesTest {

    @AfterEach
    fun resetScheme() = SkateControls.select(ControlSchemes.DEFAULT.id)

    @Test
    fun fourSchemes_EaSkateByDefault_WithTranslatedNames() {
        assertEquals(listOf("ea_skate", "session", "skater_xl", "thps"), ControlSchemes.all.map { it.id })
        assertEquals(ControlSchemes.EA_SKATE, ControlSchemes.DEFAULT)
        assertEquals(ControlSchemes.DEFAULT, SkateControls.scheme)
        for (locale in listOf("en", "fr")) {
            val strings = StringTables.merged(File(Assets.Strings.GAME_STRINGS), locale)
            ControlSchemes.all.forEach { assertTrue(strings.containsKey(it.labelKey), "${it.labelKey} ($locale)") }
        }
    }

    @Test
    fun eachScheme_BindsWhatItsTrickStyleReads() {
        val needs = mapOf(
            TrickStyle.FLICK_IT to listOf(SkateActions.TRICK_X, SkateActions.TRICK_Y),
            TrickStyle.FEET to listOf(SkateActions.TRICK_X, SkateActions.TRICK_Y, SkateActions.FRONT_X, SkateActions.FRONT_Y),
            TrickStyle.BUTTONS to listOf(SkateActions.OLLIE, SkateActions.FLIP),
        )
        for (scheme in ControlSchemes.all) {
            val required = needs.getValue(scheme.style) + SkateActions.PUSH + SkateActions.MANUAL +
                listOfNotNull(SkateActions.STEER.takeIf { scheme.steerWithAction })
            required.forEach { assertTrue(scheme.bindings[it].orEmpty().isNotEmpty(), "${scheme.id} binds $it") }
            assertTrue(scheme.bindings.keys.all { it in SkateActions.ids }, "${scheme.id} binds only skate actions")
        }
    }

    @Test
    fun eachScheme_PlaysOnKeyboardAndGamepad() {
        for (scheme in ControlSchemes.all) {
            for ((id, bindings) in scheme.bindings) {
                assertTrue(bindings.any { it is ActionBinding.Key }, "${scheme.id} $id has a key")
                assertTrue(bindings.any { it is ActionBinding.GamepadButton || it is ActionBinding.GamepadAxis }, "${scheme.id} $id has a pad binding")
            }
        }
    }

    @Test
    fun eachScheme_HasNoConflictingBindings() {
        for (scheme in ControlSchemes.all) {
            val map = SkateActions.register(ActionMap(), scheme)
            for ((id, bindings) in scheme.bindings) {
                bindings.forEach { assertEquals(emptyList<Any>(), map.findConflicts(id, it), "${scheme.id} $id $it") }
            }
        }
    }

    @Test
    fun register_IsANoOpWhenUnchanged_AndASwitchKeepsThePlayersBindings() {
        val map = SkateActions.register(ActionMap(), ControlSchemes.EA_SKATE)
        val revision = map.revision
        SkateActions.register(map, ControlSchemes.EA_SKATE)
        assertEquals(revision, map.revision)

        map.rebind(SkateActions.MANUAL, 0, ActionBinding.Key('M'.code))
        SkateActions.register(map, ControlSchemes.THPS)

        assertEquals(ControlSchemes.THPS.bindings[SkateActions.FLIP], map.definition(SkateActions.FLIP)!!.defaults)
        assertEquals(emptyList<ActionBinding>(), map.bindings(SkateActions.TRICK_X), "THPS has no trick stick")
        assertEquals(ActionBinding.Key('M'.code), map.bindings(SkateActions.MANUAL).first(), "custom binding kept")
    }

    @Test
    fun pauseMenuOption_CyclesTheSchemesAndAppliesThem() {
        val option = SkateControls.option()
        assertEquals(SkateControls.OPTION_ID, option.id)
        assertEquals(ControlSchemes.all.map { it.id }, option.values)
        assertEquals("controls.thps", option.labelKey("thps"))
        assertEquals("ea_skate", option.next("thps"))

        val presets = ActionPresets.revision
        option.apply("session")
        assertEquals(ControlSchemes.SESSION, SkateControls.scheme)
        assertNotEquals(presets, ActionPresets.revision, "the skate actions are redefined on the next frame")

        option.apply("unknown")
        assertEquals(ControlSchemes.DEFAULT, SkateControls.scheme)
    }
}
