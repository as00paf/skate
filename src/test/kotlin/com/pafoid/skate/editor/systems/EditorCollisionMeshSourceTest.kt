package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.AssetRegistry
import com.pafoid.skate.engine.assets.ProjectLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** AST-35: the editor resolves meshRef GUIDs from the open project's registry, which it does not scan on open. */
class EditorCollisionMeshSourceTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun UnscannedRegistry_IsScannedOnTheFirstMiss() {
        ProjectLayout.create(dir)
        val obj = File(ProjectLayout.assetsDir(dir), "Models/ramp.obj").apply {
            parentFile.mkdirs()
            writeText("v 0 0 0\nv 1 0 0\nv 0 0 1\nf 1 2 3\n")
        }
        val guid = AssetRegistry(dir).register(obj).value // the sidecar exists; the editor's registry is not scanned
        val source = editorCollisionMeshSource(AssetRegistry(dir), AssetPaths().apply { projectRoot = dir })

        assertEquals(3, source.load(guid).indices.size)
    }
}
