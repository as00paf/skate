package com.pafoid.skate.editor.input

import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.BindingProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BindingProfileStoreTest {
    @TempDir
    lateinit var dir: File

    private fun store() = BindingProfileStore(File(dir, "profiles"))

    @Test
    fun BindingProfileStore_SaveLoadRoundTrip_ListAndDelete() {
        val store = store()
        assertTrue(store.names().isEmpty())
        val profile = BindingProfile("Lefty", overrides = mapOf("jump" to listOf(ActionBinding.Key(81), ActionBinding.GamepadButton(2))))
        store.save(profile)
        assertEquals(listOf("Lefty"), store.names())
        assertEquals(profile, store.load("Lefty").getOrThrow())
        assertTrue(store.delete("Lefty"))
        assertTrue(store.names().isEmpty())
    }

    @Test
    fun BindingProfileStore_LoadMissingOrCorrupt_Fails() {
        val store = store()
        assertTrue(store.load("nope").isFailure)
        store.save(BindingProfile("Bad"))
        File(dir, "profiles/Bad.json").writeText("{ not json")
        assertTrue(store.load("Bad").isFailure)
    }

    @Test
    fun BindingProfileStore_SanitizesNames_AgainstPathTraversal() {
        assertFalse(BindingProfileStore.sanitize("../../evil").contains('/'))
        assertEquals("Profile", BindingProfileStore.sanitize("  ..  "))
        val store = store()
        val file = store.save(BindingProfile("../x"))
        assertEquals(File(dir, "profiles"), file.parentFile)
    }
}
