package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.ImGuiStyleManager
import com.pafoid.skate.editor.imgui.loadFonts
import com.pafoid.skate.editor.ui.windows.AssetBrowserWindow
import com.pafoid.skate.engine.assets.AssetFolder
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.AssetState
import com.pafoid.skate.engine.assets.AssetTicket
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.audio.AudioEngine
import com.pafoid.skate.engine.audio.FakeAudioBackend
import com.pafoid.skate.engine.assets.data.SoundBuffer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.assertion.ImAssertCallback
import imgui.type.ImString
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * EDT-98 guard: every Asset Browser tab, with an item of every engine-badge state, survives selecting each item:
 * click, double-click, right-click (context menu) and press-and-drag over the whole tab. An ImGui assert aborts the
 * editor's JVM; here the assert callback records and prints it with the tab and mouse position. ImGui may still not
 * survive its own assert, so a failure can show as the test JVM dying (Gradle: SKIPPED, build failed) after the
 * `EDT-98 guard: ImGui assert in ...` line.
 */
class AssetBrowserSelectGuardTest {

    private val project = createTempDirectory("edt98").toFile()
    private val logger = LoggerService().apply { consoleSink = { _, _ -> } }
    private val strings = mockk<StringManager>(relaxed = true) {
        every { getString(any()) } answers { firstArg() }
        every { getString(any(), *anyVararg()) } answers { firstArg() }
    }
    private val al = FakeAudioBackend()
    private val audio = AudioEngine(logger, al.backend).also { it.init() }
    private val engine = mockk<Engine>(relaxed = true) {
        every { stringManager } returns strings
        every { logger } returns this@AssetBrowserSelectGuardTest.logger
        every { audioEngine } returns audio
        every { assetsManager.paths } returns AssetPaths().apply { projectRoot = project }
        every { assetsManager.getSound(any()) } answers { SoundBuffer(firstArg()).apply { bufferId = 5 } }
        // Prefab thumbnails stay placeholders (no GPU): their model and texture never finish loading
        every { assetsManager.requestModel(any()) } returns mockk<AssetTicket<`3dModel`>>(relaxed = true) { every { state } returns AssetState.Loading }
        every { assetsManager.requestTexture(any()) } returns mockk<AssetTicket<Texture>>(relaxed = true) { every { state } returns AssetState.Loading }
    }
    private val asserts = mutableListOf<String>()
    private var case = ""

    @BeforeEach
    fun setup() {
        ImGui.createContext()
        ImGui.setAssertCallback(object : ImAssertCallback() {
            override fun imAssertCallback(assertion: String, line: Int, file: String) {
                asserts += "$case: $assertion ($file:$line)"
                // ImGui may not survive its own assert (the editor aborts): name the case before the JVM goes.
                System.err.println("EDT-98 guard: ImGui assert in $case: $assertion ($file:$line)")
            }
        })
        val io = ImGui.getIO()
        io.iniFilename = null
        io.loadFonts(javaClass.getResourceAsStream(Assets.Fonts.FONTS_FILE)!!.readAllBytes())
        io.setDisplaySize(1600f, 1000f)
        io.deltaTime = 1f / 60f
        ImGuiStyleManager.invalidate()
        ImGuiStyleManager.sync("dark")
    }

    @AfterEach
    fun teardown() {
        ImGui.destroyContext()
        audio.close()
        project.deleteRecursively()
    }

    /** One item per badge state, plus a model texture for the Textures tab (shown with its checkbox). */
    private fun entriesFor(folder: AssetFolder): List<BrowserEntry> {
        val ext = when (folder) {
            AssetFolder.SOUNDS -> "wav"; AssetFolder.TEXTURES -> "png"; AssetFolder.ANIMATIONS -> "fbx"
            AssetFolder.MODELS -> "glb"; AssetFolder.MATERIALS -> "skmat"; AssetFolder.SCRIPTS -> "kts"
            AssetFolder.SHADERS -> "glsl"; AssetFolder.FONTS -> "ttf"; AssetFolder.PREFABS -> "prefab"
            else -> "json"
        }
        val dir = File(project, "Assets/${folder.dirName}").apply { mkdirs() }
        val states = listOf(
            emptyList(),
            listOf(EngineBadge.ENGINE),
            listOf(EngineBadge.ENGINE_COPY),
            listOf(EngineBadge.ENGINE_COPY, EngineBadge.MODIFIED, EngineBadge.UPDATE_AVAILABLE),
        )
        return states.mapIndexed { i, badges ->
            val file = File(dir, "item_$i.$ext").apply { writeBytes(ByteArray(4)) }
            BrowserEntry(
                file, folder, badges,
                reference = if (badges.isEmpty()) null else "engine:${folder.dirName}/item_$i.$ext",
                modelTexture = folder == AssetFolder.TEXTURES && i == 3,
            )
        }
    }

    private fun frame(tab: AssetBrowserTab, search: ImString) {
        ImGui.newFrame()
        ImGui.setNextWindowPos(0f, 0f)
        ImGui.setNextWindowSize(WIDTH, HEIGHT)
        ImGui.begin("Asset Browser##guard", imgui.flag.ImGuiWindowFlags.NoMove or imgui.flag.ImGuiWindowFlags.NoTitleBar)
        tab.imgui("guard", search)
        ImGui.end()
        ImGui.render()
    }

    @Test
    fun EveryTab_SelectingEveryItemType_DoesNotAssert() {
        val tabs = AssetBrowserWindow.createTabs(engine, mockk(relaxed = true))
        assertEquals(AssetBrowserTabs.ORDER.toSet(), tabs.keys, "the guard must cover every tab")
        val io = ImGui.getIO()
        val search = ImString(64)
        for ((folder, tab) in tabs) {
            case = folder.name
            tab.entries = entriesFor(folder)
            repeat(3) { frame(tab, search) }
            System.err.println("EDT-98 guard: ${folder.name}")
            var y = 4f
            while (y < SWEEP_HEIGHT && asserts.isEmpty()) {
                var x = 4f
                while (x < WIDTH && asserts.isEmpty()) {
                    case = "${folder.name} at ($x, $y)"
                    io.addMousePosEvent(x, y); frame(tab, search)
                    // Click, then double-click
                    repeat(2) {
                        io.addMouseButtonEvent(0, true); frame(tab, search)
                        io.addMouseButtonEvent(0, false); frame(tab, search)
                    }
                    // Right-click opens the item's menu; Escape closes it
                    io.addMouseButtonEvent(1, true); frame(tab, search)
                    io.addMouseButtonEvent(1, false); frame(tab, search)
                    io.addKeyEvent(imgui.flag.ImGuiKey.Escape, true); frame(tab, search)
                    io.addKeyEvent(imgui.flag.ImGuiKey.Escape, false); frame(tab, search)
                    // Press and drag
                    io.addMouseButtonEvent(0, true); frame(tab, search)
                    io.addMousePosEvent(x + 30f, y + 20f); frame(tab, search); frame(tab, search)
                    io.addMouseButtonEvent(0, false); frame(tab, search)
                    x += STEP_X
                }
                y += STEP_Y
                // MockK keeps every call it sees; tens of thousands of frames would exhaust the heap
                clearMocks(strings, engine, answers = false, recordedCalls = true, childMocks = true, verificationMarks = true, exclusionRules = false)
            }
        }
        assertTrue(asserts.isEmpty(), asserts.joinToString("\n"))
    }

    /** Presses each row's play button (the Sounds table's last column) until [stop] says it is done. */
    private fun pressPlayButtons(tab: AssetBrowserTab, stop: () -> Boolean) {
        val io = ImGui.getIO()
        val search = ImString(64)
        tab.entries = entriesFor(AssetFolder.SOUNDS)
        repeat(3) { frame(tab, search) }
        var y = 4f
        while (y < SWEEP_HEIGHT && !stop()) {
            case = "SOUNDS play at y=$y"
            io.addMousePosEvent(WIDTH - 24f, y); frame(tab, search)
            io.addMouseButtonEvent(0, true); frame(tab, search)
            io.addMouseButtonEvent(0, false); frame(tab, search)
            y += 4f
        }
    }

    @Test
    fun SoundsTab_PlayButton_PreviewsThroughTheAudioEngine() {
        val tab = AssetBrowserWindow.createTabs(engine, mockk(relaxed = true)).getValue(AssetFolder.SOUNDS)
        pressPlayButtons(tab) { al.playing.isNotEmpty() }
        val source = al.playing.single()
        assertEquals(5, source.buffer)
        assertTrue(source.relative)
        assertTrue(asserts.isEmpty(), asserts.joinToString("\n"))
    }

    @Test
    fun SoundsTab_PlayButton_WithoutAudioDevice_WarnsInsteadOfCrashing() {
        val noDevice = AudioEngine(logger, al.backend) // never initialised: no OpenAL context
        every { engine.audioEngine } returns noDevice
        val tab = AssetBrowserWindow.createTabs(engine, mockk(relaxed = true)).getValue(AssetFolder.SOUNDS)
        val warned = { logger.logs.any { "Audio is unavailable" in it.message } }
        pressPlayButtons(tab, warned)
        assertTrue(warned(), "pressing play without a device warns")
        assertTrue(al.sources.isEmpty())
        assertTrue(asserts.isEmpty(), asserts.joinToString("\n"))
    }

    private companion object {
        const val WIDTH = 520f
        const val HEIGHT = 420f
        /** Down to the last row of the tallest tab (Prefabs' catalog grows past this; its tiles are all alike). */
        const val SWEEP_HEIGHT = 400f
        const val STEP_X = 26f
        const val STEP_Y = 18f
    }
}
