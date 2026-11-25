package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.components.AssetRefs
import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.ecs.components.PlayerController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AssetRefsTest {

    @Test
    fun AssetRefs_AnnotationGivesTheFieldKind() {
        val pc = PlayerController::class.java
        assertEquals(AssetFolder.DATA, AssetRefs.kindOf(pc.getDeclaredField("configPath")))
        assertNull(AssetRefs.kindOf(pc.getDeclaredField("behaviorId")))
    }

    @Test
    fun AssetRefs_AcceptsOnlyFilesOfTheKind() {
        assertTrue(AssetRefs.accepts(AssetFolder.MODELS, "Assets/Models/rock.glb"))
        assertTrue(AssetRefs.accepts(AssetFolder.MODELS, "engine:Models/cube.obj"))
        assertTrue(AssetRefs.accepts(AssetFolder.TEXTURES, "C:\\p\\Assets\\Textures\\a.PNG"))
        assertFalse(AssetRefs.accepts(AssetFolder.TEXTURES, "Assets/Models/rock.glb"))
        assertFalse(AssetRefs.accepts(AssetFolder.SOUNDS, ""))
    }

    @Test
    fun AssetRefs_DropValueRejectsOtherKinds() {
        assertEquals("a/b.wav", AssetRefs.dropValue(AssetFolder.SOUNDS, "a/b.wav"))
        assertNull(AssetRefs.dropValue(AssetFolder.SOUNDS, "a/b.png"))
        assertNull(AssetRefs.dropValue(AssetFolder.SOUNDS, null))
    }

    @Test
    fun AssetRefs_FlagsMissingFiles(@TempDir root: File) {
        val paths = AssetPaths().apply { projectRoot = root }
        File(root, "Assets/Models").mkdirs()
        File(root, "Assets/Models/rock.glb").writeText("x")
        assertTrue(AssetRefs.exists(paths, "Assets/Models/rock.glb"))
        assertTrue(AssetRefs.exists(paths, File(root, "Assets/Models/rock.glb").path))
        assertFalse(AssetRefs.exists(paths, "Assets/Models/gone.glb"))
        assertFalse(AssetRefs.exists(paths, "engine:Models/does_not_exist.obj"))
        assertTrue(AssetRefs.exists(paths, ""), "an empty reference is not a missing file")
    }

    @Test
    fun AssetRefs_FilterMatchesAnywhereIgnoringCase() {
        val all = listOf("Assets/Models/Rock.glb", "engine:Models/cube.obj")
        assertEquals(all, AssetRefs.filter(all, " "))
        assertEquals(listOf("Assets/Models/Rock.glb"), AssetRefs.filter(all, "rock"))
    }
}
