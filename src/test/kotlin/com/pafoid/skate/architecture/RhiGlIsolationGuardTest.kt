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
 * REN-12: OpenGL belongs in the RHI's GL backend (`engine/render/rhi/opengl/`), strictly (migration complete).
 *
 * A file "uses GL" when its code (comments excluded) references `org.lwjgl.opengl.` or imgui-java's GL3 backend
 * (`imgui.gl3.`). The one exception is the editor's ImGui renderer, which drives imgui-java's own GL3 backend
 * (docs/design/REN-12.md §7, option 1). Outside the backend, only the window (the composition root that creates the
 * device) may name a backend class; everything else goes through `engine/render/rhi/`.
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
    fun GlUsers_OnlyTheGlBackendAndImGuiLayer() {
        val users = glUsers().filterNot { it.startsWith(GL_BACKEND) }.toSet()
        assertEquals(setOf(IMGUI_LAYER), users, "OpenGL outside $GL_BACKEND (go through the RHI instead)")
    }

    @Test
    fun VulkanApi_OnlyInTheVulkanBackend() {
        val users = mainFiles()
            .filter { (path, text) -> !path.startsWith(VK_BACKEND) && codeLines(text).any { VK_API.containsMatchIn(it) } }
            .map { it.first }
        assertTrue(users.isEmpty(), "Vulkan/shaderc outside $VK_BACKEND (REN-13 spike stays opt-in): $users")
    }

    @Test
    fun GlBackend_IsNamedOnlyByTheWindow() {
        val backendUsers = mainFiles()
            .filter { (path, text) -> !path.startsWith(GL_BACKEND) && codeLines(text).any { BACKEND_REF.containsMatchIn(it) } }
            .map { it.first }
            .toSet()
        assertEquals(setOf(COMPOSITION_ROOT), backendUsers, "Only the window creates the GL device; use the RHI interfaces")
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

        private const val VK_BACKEND = "engine/render/rhi/vulkan/"
        private val VK_API = Regex("""org\.lwjgl\.vulkan\.|org\.lwjgl\.util\.shaderc\.""")
        private val BACKEND_REF = Regex("""render\.rhi\.opengl\.""")
        private const val IMGUI_LAYER = "editor/imgui/ImGuiLayer.kt"
        private const val COMPOSITION_ROOT = "engine/core/Window.kt"

        private fun codeLines(text: String): Sequence<String> = text.lineSequence()
            .filterNot { it.trimStart().let { l -> l.startsWith("//") || l.startsWith("*") || l.startsWith("/*") } }
            .map { it.substringBefore("//") }

        fun usesGl(text: String): Boolean = codeLines(text).any { GL.containsMatchIn(it) }

    }
}
