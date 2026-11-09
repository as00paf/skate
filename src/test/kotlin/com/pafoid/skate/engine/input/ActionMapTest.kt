package com.pafoid.skate.engine.input

import com.pafoid.skate.engine.gameplay.CharacterActions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json

class ActionMapTest {
    private fun map() = CharacterActions.register(UiNavigationActions.register(ActionMap()))

    @Test
    fun ActionMap_ConflictDetectedWithinSameContext() {
        val m = map()
        val result = m.rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.LEFT_SHIFT))
        assertTrue(result is RebindResult.Conflict)
        assertEquals(listOf(CharacterActions.SPRINT), (result as RebindResult.Conflict).conflicts.map { it.actionId })
        // rejected: nothing changed
        assertEquals(ActionBinding.Key(Keys.SPACE), m.bindings(CharacterActions.JUMP)[0])
        assertFalse(m.isCustomized(CharacterActions.JUMP))
    }

    @Test
    fun ActionMap_NoConflictAcrossContexts() {
        val m = map()
        // ui.confirm already uses Enter in UI context; gameplay jump may take it
        assertEquals(RebindResult.Success, m.rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.ENTER)))
    }

    @Test
    fun ActionMap_AxisDirectionsDoNotConflict() {
        val m = map()
        m.define(ActionDefinition("a", InputContext.GAMEPLAY, "T", defaults = listOf(ActionBinding.GamepadAxis(6, 1f))))
        m.define(ActionDefinition("b", InputContext.GAMEPLAY, "T", defaults = listOf(ActionBinding.GamepadAxis(6, -1f))))
        assertTrue(m.findConflicts("a", ActionBinding.GamepadAxis(6, 0.5f)).isEmpty())
        assertEquals(1, m.findConflicts("b", ActionBinding.GamepadAxis(6, 1f)).size)
    }

    @Test
    fun ActionMap_UnbindOthersMovesBinding() {
        val m = map()
        assertEquals(
            RebindResult.Success,
            m.rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.LEFT_SHIFT), ConflictResolution.UNBIND_OTHERS)
        )
        assertEquals(ActionBinding.Key(Keys.LEFT_SHIFT), m.bindings(CharacterActions.JUMP)[0])
        assertFalse(m.bindings(CharacterActions.SPRINT).any { it == ActionBinding.Key(Keys.LEFT_SHIFT) })
    }

    @Test
    fun ActionMap_SwapGivesOtherActionTheReplacedBinding() {
        val m = map()
        m.rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.LEFT_SHIFT), ConflictResolution.SWAP)
        assertEquals(ActionBinding.Key(Keys.LEFT_SHIFT), m.bindings(CharacterActions.JUMP)[0])
        assertTrue(m.bindings(CharacterActions.SPRINT).contains(ActionBinding.Key(Keys.SPACE)))
        assertFalse(m.bindings(CharacterActions.SPRINT).contains(ActionBinding.Key(Keys.LEFT_SHIFT)))
    }

    @Test
    fun ActionMap_InvalidRebindIsReported() {
        val m = map()
        assertTrue(m.rebind("missing", null, ActionBinding.Key(1)) is RebindResult.Invalid)
        assertTrue(m.rebind(CharacterActions.JUMP, 9, ActionBinding.Key(1)) is RebindResult.Invalid)
    }

    @Test
    fun ActionMap_AddRemoveClearAndReset() {
        val m = map()
        m.addBinding(CharacterActions.JUMP, ActionBinding.MouseButton(1))
        assertEquals(3, m.bindings(CharacterActions.JUMP).size)
        assertTrue(m.removeBinding(CharacterActions.JUMP, 2))
        assertFalse(m.isCustomized(CharacterActions.JUMP)) // back to defaults
        m.clearBindings(CharacterActions.JUMP)
        assertTrue(m.bindings(CharacterActions.JUMP).isEmpty())
        m.reset(CharacterActions.JUMP)
        assertEquals(2, m.bindings(CharacterActions.JUMP).size)
    }

    @Test
    fun ActionMap_ResetContextOnlyAffectsThatContext() {
        val m = map()
        m.rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.Q))
        m.rebind(UiNavigationActions.CONFIRM, 0, ActionBinding.Key(Keys.Q))
        m.resetContext(InputContext.GAMEPLAY)
        assertFalse(m.isCustomized(CharacterActions.JUMP))
        assertTrue(m.isCustomized(UiNavigationActions.CONFIRM))
        m.resetAll()
        assertFalse(m.isCustomized(UiNavigationActions.CONFIRM))
    }

    @Test
    fun ActionMap_ProfileRoundTripsThroughJsonAndStoresOnlyOverrides() {
        val m = map()
        m.rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.Q))
        m.addBinding(CharacterActions.MOVE_X, ActionBinding.Key(Keys.RIGHT))
        val profile = m.toProfile("Mine")
        assertEquals(setOf(CharacterActions.JUMP, CharacterActions.MOVE_X), profile.overrides.keys)

        val loaded = BindingProfile.fromJson(profile.toJson())
        assertEquals(profile, loaded)
        val fresh = map()
        fresh.loadProfile(loaded)
        assertEquals(m.bindings(CharacterActions.JUMP), fresh.bindings(CharacterActions.JUMP))
        assertEquals(m.bindings(CharacterActions.MOVE_X), fresh.bindings(CharacterActions.MOVE_X))
    }

    @Test
    fun ActionMap_LoadProfileIgnoresUnknownActionsAndReplacesOldOverrides() {
        val m = map()
        m.rebind(CharacterActions.CROUCH, 0, ActionBinding.Key(Keys.Q))
        m.loadProfile(BindingProfile(overrides = mapOf("gone" to listOf(ActionBinding.Key(1)))))
        assertFalse(m.isCustomized(CharacterActions.CROUCH))
        assertTrue(m.definition("gone") == null)
    }

    @Test
    fun ActionMap_InputMappingsPersistBindingsAndDeadZones() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val m = map()
        m.rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.Q))
        val mappings = InputMappings()
        mappings.storeBindings(m)
        val loaded = json.decodeFromString<InputMappings>(json.encodeToString(InputMappings.serializer(), mappings))
        val fresh = map().also { it.loadProfile(loaded.bindings) }
        assertEquals(ActionBinding.Key(Keys.Q), fresh.bindings(CharacterActions.JUMP)[0])
        // old project files (pre-action-map fields) still load
        assertEquals(BindingProfile(), json.decodeFromString<InputMappings>("""{"jump":{"gamepadButton":2}}""").bindings)
    }

    @Test
    fun ActionMap_OverridesOfLaterDefinedActionsApplyAndSurviveSaving() {
        val m = ActionMap()
        m.loadProfile(BindingProfile(overrides = mapOf("game.horn" to listOf(ActionBinding.Key(Keys.Q)))))
        assertEquals(setOf("game.horn"), m.toProfile().overrides.keys, "unknown overrides are kept when saving")
        m.define(ActionDefinition("game.horn", InputContext("vehicle"), "Vehicle", defaults = listOf(ActionBinding.Key(Keys.E))))
        assertEquals(listOf<ActionBinding>(ActionBinding.Key(Keys.Q)), m.bindings("game.horn"))
        assertTrue(m.isCustomized("game.horn"))
        m.resetAll()
        assertEquals(listOf<ActionBinding>(ActionBinding.Key(Keys.E)), m.bindings("game.horn"))
    }
}
