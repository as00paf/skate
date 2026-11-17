package com.pafoid.skate.editor.assets

import com.pafoid.skate.editor.commands.objects.EditMaterialCommand
import com.pafoid.skate.editor.commands.objects.MaterialState
import com.pafoid.skate.editor.commands.project.CreateFileCommand
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.imgui.components.MaterialEditSession
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.render.shader.ShaderTemplates
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** EDT-80: the material Shader field's choices, New Shader's file, and create → assign → undo. */
class ProjectShadersTest {
    @TempDir
    lateinit var project: File

    private fun glsl(path: String) = File(project, path).apply { parentFile.mkdirs(); writeText("// shader") }

    @Test
    fun options_ListEngineMaterialShadersThenProjectShadersWithoutBasicCopies() {
        glsl("Assets/Shaders/water.glsl")
        glsl("Assets/Shaders/shader_3d_default.glsl") // basic copy: already the engine entry
        glsl("Assets/Shaders/fog.glsl") // basic include copy
        glsl("Assets/Effects/glow.GLSL")
        glsl("Assets/.hidden/secret.glsl")
        File(project, "Assets/Shaders/notes.txt").writeText("x")

        assertEquals(
            listOf(
                EngineDefaults.Shaders.DEFAULT_3D, EngineDefaults.Shaders.TEXTURE_3D,
                "Assets/Effects/glow.GLSL", "Assets/Shaders/water.glsl",
            ),
            ProjectShaders.options(project, current = null),
        )
    }

    @Test
    fun options_KeepAnUnlistedCurrentReferenceAndWorkWithoutAProject() {
        assertEquals(ProjectShaders.ENGINE_MATERIAL_SHADERS, ProjectShaders.options(null, null))
        assertEquals(
            ProjectShaders.ENGINE_MATERIAL_SHADERS + "Assets/Shaders/missing.glsl",
            ProjectShaders.options(null, "Assets/Shaders/missing.glsl"),
        )
        assertEquals(ProjectShaders.ENGINE_MATERIAL_SHADERS, ProjectShaders.options(null, EngineDefaults.Shaders.DEFAULT_3D))
    }

    @Test
    fun label_ShowsTheFileNameAndMarksEngineShaders() {
        assertEquals("shader_3d_default.glsl (engine)", ProjectShaders.label(EngineDefaults.Shaders.DEFAULT_3D, "engine"))
        assertEquals("water.glsl", ProjectShaders.label("Assets/Shaders/water.glsl", "engine"))
    }

    @Test
    fun newShaderFile_IsInAssetsShadersAndNeverOverwrites() {
        assertEquals(File(project, "Assets/Shaders/NewShader.glsl"), ProjectShaders.newShaderFile(project))
        glsl("Assets/Shaders/NewShader.glsl")
        glsl("Assets/Shaders/NewShader1.glsl")
        assertEquals(File(project, "Assets/Shaders/NewShader2.glsl"), ProjectShaders.newShaderFile(project))
    }

    @Test
    fun newShader_CanBeCreatedAssignedToAMaterialSavedAndUndone() {
        // New Shader: the template, in Assets/Shaders/, through the undoable create command
        val file = ProjectShaders.newShaderFile(project)
        val create = CreateFileCommand(file.absolutePath, false, mockk(relaxed = true), ShaderTemplates.PBR_MATERIAL)
        create.execute()
        assertTrue(create.wasSuccessful())
        assertEquals(ShaderTemplates.PBR_MATERIAL, file.readText())
        val reference = "Assets/Shaders/NewShader.glsl"
        assertTrue(reference in ProjectShaders.options(project, null))

        // Assigned in the inspector: one gesture, one EditMaterialCommand
        val material = Material()
        val session = MaterialEditSession()
        session.preview(material, session.current(material).copy(shader = reference))
        assertEquals(reference, material.shader)
        val edit = session.settle(anyItemActive = false).single()
        val command = EditMaterialCommand(edit.material, edit.before, edit.after)
        assertFalse(command.isNoOp)

        // Saved into the .skmat
        assertEquals(reference, MaterialAsset.from(material).shader)

        command.undo()
        assertNull(material.shader)
        command.execute()
        assertEquals(reference, material.shader)

        create.undo()
        assertFalse(file.exists())
    }

    @Test
    fun optionsCache_ScansOncePerOpeningAndIsKeyedByMaterial() {
        var scans = 0
        val cache = ProjectShaders.OptionsCache { _, _ -> scans++; listOf("scan$scans") }
        val a = Any()
        val b = Any()

        assertEquals(emptyList<String>(), cache.frame(a, open = false, projectRoot = null, current = null))
        assertEquals(0, scans)
        repeat(5) {
            assertEquals(listOf("scan1"), cache.frame(a, open = true, projectRoot = null, current = null))
            cache.frame(b, open = false, projectRoot = null, current = null) // another inspector, closed
        }
        assertEquals(1, scans, "one scan while the combo stays open")

        cache.frame(a, open = false, projectRoot = null, current = null)
        assertEquals(listOf("scan2"), cache.frame(a, open = true, projectRoot = null, current = null))
        assertEquals(2, scans, "reopening scans again, so new shaders appear")
    }

    @Test
    fun requestNewShader_CreatesTheTemplateThenRevealsIt() {
        val events = EventSystem()
        val order = mutableListOf<String>()
        events.subscribe<ProjectEvent.CreateFileRequested> {
            order += "create"
            CreateFileCommand(it.path, it.isDirectory, mockk(relaxed = true), it.content).execute()
        }
        events.subscribe<FileSystemEvent.RevealInProject> { order += "reveal:" + File(it.path).name }

        val file = ProjectShaders.requestNewShader(project, events)

        assertEquals(File(project, "Assets/Shaders/NewShader.glsl").absoluteFile, file?.absoluteFile)
        assertEquals(ShaderTemplates.PBR_MATERIAL, file?.readText())
        assertEquals(listOf("create", "reveal:NewShader.glsl"), order)
    }

    @Test
    fun requestNewShader_RevealsNothingWhenTheCreateFails() {
        val events = EventSystem()
        var revealed = false
        events.subscribe<FileSystemEvent.RevealInProject> { revealed = true }

        assertNull(ProjectShaders.requestNewShader(project, events)) // nobody creates the file
        assertFalse(revealed)
    }

    @Test
    fun materialState_CopiesTheShaderBothWays() {
        val m = Material(shader = EngineDefaults.Shaders.TEXTURE_3D)
        val state = MaterialState.of(m)
        assertEquals(EngineDefaults.Shaders.TEXTURE_3D, state.shader)
        assertEquals(EngineDefaults.Shaders.TEXTURE_3D, state.toMaterial().shader)
        assertEquals(state, state.validated())
    }
}
