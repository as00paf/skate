package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * REN-12 ratchet: OpenGL belongs in the RHI's GL backend (`engine/render/rhi/opengl/`).
 *
 * A file "uses GL" when its code (comments excluded) references `org.lwjgl.opengl.` or imgui-java's GL3 backend
 * (`imgui.gl3.`). [ALLOWED] lists the files that still do while the migration runs (docs/design/REN-12.md §5). The
 * guard fails when a file outside the backend starts using GL, and when a listed file stops using GL: remove it from
 * the list in the same change, so the list only shrinks. At step 7 only `editor/imgui/ImGuiLayer.kt` stays (§7).
 */
class RhiGlIsolationGuardTest {

    private val sourceRoot: Path =
        Paths.get(System.getProperty("user.dir")).toAbsolutePath().resolve("src/main/kotlin/com/pafoid/skate")

    private fun mainFiles(): List<Pair<String, String>> = Files.walk(sourceRoot).use { paths ->
        paths.filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
            .map { sourceRoot.relativize(it).invariantSeparatorsPathString to it.readText() }
            .toList()
    }

    private fun glUsers(): Set<String> = mainFiles().filter { (_, text) -> usesGl(text) }.map { it.first }.toSet()

    @Test
    fun GlUsers_OutsideTheGlBackend_OnlyShrink() {
        val users = glUsers().filterNot { it.startsWith(GL_BACKEND) }.toSet()
        val added = users - ALLOWED
        val stale = ALLOWED - users
        assertTrue(added.isEmpty(), "New OpenGL users outside $GL_BACKEND (go through the RHI instead): ${added.sorted()}")
        assertTrue(stale.isEmpty(), "These files no longer use OpenGL, remove them from ALLOWED: ${stale.sorted()}")
    }

    @Test
    fun RhiInterfaces_AreGlFree() {
        val leaking = glUsers().filter { it.startsWith(RHI) && !it.startsWith(GL_BACKEND) }
        assertTrue(leaking.isEmpty(), "engine/render/rhi/ outside opengl/ must stay backend-free: $leaking")
    }

    @Test
    fun Detection_IgnoresCommentsAndInjectedFunctionNames() {
        assertTrue(usesGl("import org.lwjgl.opengl.GL11.glDrawElements"))
        assertTrue(usesGl("val x = org.lwjgl.opengl.GL11.GL_TRIANGLES"))
        assertTrue(usesGl("private val gl3 = imgui.gl3.ImGuiImplGl3()"))
        assertEquals(false, usesGl("// org.lwjgl.opengl.GL11 is wrapped elsewhere\n * see org.lwjgl.opengl.GL30"))
        assertEquals(false, usesGl("class BindingCache(val glBindVao: (Int) -> Unit) { fun f() = glBindVao(1) }"))
    }

    companion object {
        private const val RHI = "engine/render/rhi/"
        private const val GL_BACKEND = "engine/render/rhi/opengl/"
        private val GL = Regex("""org\.lwjgl\.opengl\.|imgui\.gl3\.""")

        fun usesGl(text: String): Boolean = text.lineSequence()
            .filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }
            .any { GL.containsMatchIn(it.substringBefore("//")) }

        /** GL users outside the backend; shrink only (REN-12 steps 1-7). */
        val ALLOWED = setOf(
            "editor/imgui/ImGuiLayer.kt",
            "engine/assets/data/Shader.kt",
            "engine/assets/loaders/ShaderLoader.kt",
            "engine/render/ColorTarget.kt",
            "engine/render/FrameBuffer.kt",
            "engine/render/PickingTexture.kt",
            "engine/render/RenderResourcesFactory.kt",
            "engine/render/ShadowMap.kt",
            "engine/render/cache/GpuResources.kt",
            "engine/render/data/RenderBatch2D.kt",
            "engine/render/data/RenderBatch3D.kt",
            "engine/render/renderer/DebugRenderer.kt",
            "engine/render/renderer/LightingUniformsLoader.kt",
            "engine/render/renderer/ModelRenderer.kt",
            "engine/render/renderer/PickingRenderer.kt",
            "engine/render/renderer/ShadowRenderer.kt",
            "engine/render/renderer/SkyDomeRenderer.kt",
            "engine/render/renderer/SkyboxRenderer.kt",
            "engine/render/renderer/SplashRenderer.kt",
            "engine/render/renderer/ThumbnailRenderer.kt",
            "engine/render/renderer/passes/DebugPass.kt",
            "engine/render/renderer/passes/GeometryPass.kt",
            "engine/render/renderer/passes/ParticlePass.kt",
            "engine/render/renderer/passes/PickingPass.kt",
            "engine/render/renderer/passes/PostProcessPass.kt",
            "engine/render/ui/UiBatchRenderer.kt",
            "engine/render/utils/GLState.kt",
            "engine/utils/ScreenshotUtils.kt",
            "engine/utils/testing/FrameCapture.kt",
            "game/GameMain.kt",
        )
    }
}
