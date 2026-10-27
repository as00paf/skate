package com.pafoid.skate.engine.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json

class ActionMapTest {
    private fun map() = DefaultActions.register(ActionMap())

    @Test
    fun ActionMap_ConflictDetectedWithinSameContext() {
        val m = map()
        val result = m.rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.LEFT_SHIFT))
        assertTrue(result is RebindResult.Conflict)
        assertEquals(listOf(DefaultActions.SPRINT), (result as RebindResult.Conflict).conflicts.map { it.actionId })
        // rejected: nothing changed
        assertEquals(ActionBinding.Key(Keys.SPACE), m.bindings(DefaultActions.JUMP)[0])
        assertFalse(m.isCustomized(DefaultActions.JUMP))
    }

    @Test
    fun ActionMap_NoConflictAcrossContexts() {
        val m = map()
        // ui.confirm already uses Enter in UI context; gameplay jump may take it
        assertEquals(RebindResult.Success, m.rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.ENTER)))
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
            m.rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.LEFT_SHIFT), ConflictResolution.UNBIND_OTHERS)
        )
        assertEquals(ActionBinding.Key(Keys.LEFT_SHIFT), m.bindings(DefaultActions.JUMP)[0])
        assertFalse(m.bindings(DefaultActions.SPRINT).any { it == ActionBinding.Key(Keys.LEFT_SHIFT) })
    }

    @Test
    fun ActionMap_SwapGivesOtherActionTheReplacedBinding() {
        val m = map()
        m.rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.LEFT_SHIFT), ConflictResolution.SWAP)
        assertEquals(ActionBinding.Key(Keys.LEFT_SHIFT), m.bindings(DefaultActions.JUMP)[0])
        assertTrue(m.bindings(DefaultActions.SPRINT).contains(ActionBinding.Key(Keys.SPACE)))
        assertFalse(m.bindings(DefaultActions.SPRINT).contains(ActionBinding.Key(Keys.LEFT_SHIFT)))
    }

    @Test
    fun ActionMap_InvalidRebindIsReported() {
        val m = map()
        assertTrue(m.rebind("missing", null, ActionBinding.Key(1)) is RebindResult.Invalid)
        assertTrue(m.rebind(DefaultActions.JUMP, 9, ActionBinding.Key(1)) is RebindResult.Invalid)
    }

    @Test
    fun ActionMap_AddRemoveClearAndReset() {
        val m = map()
        m.addBinding(DefaultActions.JUMP, ActionBinding.MouseButton(1))
        assertEquals(3, m.bindings(DefaultActions.JUMP).size)
        assertTrue(m.removeBinding(DefaultActions.JUMP, 2))
        assertFalse(m.isCustomized(DefaultActions.JUMP)) // back to defaults
        m.clearBindings(DefaultActions.JUMP)
        assertTrue(m.bindings(DefaultActions.JUMP).isEmpty())
        m.reset(DefaultActions.JUMP)
        assertEquals(2, m.bindings(DefaultActions.JUMP).size)
    }

    @Test
    fun ActionMap_ResetContextOnlyAffectsThatContext() {
        val m = map()
        m.rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.E))
        m.rebind(DefaultActions.UI_CONFIRM, 0, ActionBinding.Key(Keys.Q))
        m.resetContext(InputContext.GAMEPLAY)
        assertFalse(m.isCustomized(DefaultActions.JUMP))
        assertTrue(m.isCustomized(DefaultActions.UI_CONFIRM))
        m.resetAll()
        assertFalse(m.isCustomized(DefaultActions.UI_CONFIRM))
    }

    @Test
    fun ActionMap_ProfileRoundTripsThroughJsonAndStoresOnlyOverrides() {
        val m = map()
        m.rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.E))
        m.addBinding(DefaultActions.MOVE_X, ActionBinding.Key(Keys.RIGHT))
        val profile = m.toProfile("Mine")
        assertEquals(setOf(DefaultActions.JUMP, DefaultActions.MOVE_X), profile.overrides.keys)

        val loaded = BindingProfile.fromJson(profile.toJson())
        assertEquals(profile, loaded)
        val fresh = map()
        fresh.loadProfile(loaded)
        assertEquals(m.bindings(DefaultActions.JUMP), fresh.bindings(DefaultActions.JUMP))
        assertEquals(m.bindings(DefaultActions.MOVE_X), fresh.bindings(DefaultActions.MOVE_X))
    }

    @Test
    fun ActionMap_LoadProfileIgnoresUnknownActionsAndReplacesOldOverrides() {
        val m = map()
        m.rebind(DefaultActions.CROUCH, 0, ActionBinding.Key(Keys.Q))
        m.loadProfile(BindingProfile(overrides = mapOf("gone" to listOf(ActionBinding.Key(1)))))
        assertFalse(m.isCustomized(DefaultActions.CROUCH))
        assertTrue(m.definition("gone") == null)
    }

    @Test
    fun ActionMap_InputMappingsPersistBindingsAndDeadZones() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val m = map()
        m.rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.E))
        val mappings = InputMappings()
        mappings.storeBindings(m)
        val loaded = json.decodeFromString<InputMappings>(json.encodeToString(InputMappings.serializer(), mappings))
        val fresh = map().also { it.loadProfile(loaded.bindings) }
        assertEquals(ActionBinding.Key(Keys.E), fresh.bindings(DefaultActions.JUMP)[0])
        // old project files (pre-action-map fields) still load
        assertEquals(BindingProfile(), json.decodeFromString<InputMappings>("""{"jump":{"gamepadButton":2}}""").bindings)
    }
}
