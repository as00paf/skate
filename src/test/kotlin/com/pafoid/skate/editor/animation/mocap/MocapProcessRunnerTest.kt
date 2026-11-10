package com.pafoid.skate.editor.animation.mocap

import com.pafoid.skate.editor.settings.MocapSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class MocapProcessRunnerTest {
    @TempDir lateinit var dir: File

    @Test
    fun `Runner_ReportsProgressKeepsMessagesAndIgnoresOtherLines`() {
        val progress = ArrayList<Float>()
        val runner = MocapProcessRunner({ _, _ -> FakeProcess(listOf("""{"progress":0.25}""", "hello", """{"progress":2}""", """{"done":true}""")) })
        val r = runner.run(listOf("tool"), onProgress = { progress += it })
        assertTrue(r.ok)
        assertEquals(listOf(0.25f, 1f), progress, "progress is clamped to 0..1")
        assertEquals(1, r.messages.size)
    }

    @Test
    fun `Runner_SummarisesFailures`() {
        val withError = MocapProcessRunner({ _, _ -> FakeProcess(listOf("""{"error":"Cannot open video: x.mp4"}"""), listOf("trace"), exit = 1) }).run(listOf("t"))
        assertFalse(withError.ok)
        assertEquals("Cannot open video: x.mp4", withError.errorSummary())
        val stderrOnly = MocapProcessRunner({ _, _ -> FakeProcess(emptyList(), listOf("Traceback", "ModuleNotFoundError: cv2", ""), exit = 1) }).run(listOf("t"))
        assertEquals("ModuleNotFoundError: cv2", stderrOnly.errorSummary())
        val notStarted = MocapProcessRunner({ _, _ -> throw java.io.IOException("CreateProcess error=2") }).run(listOf("python"))
        assertTrue(notStarted.errorSummary().startsWith("Could not start python"))
    }

    @Test
    fun `Runner_CancelKillsTheProcess`() {
        val process = FakeProcess(listOf("""{"progress":0.1}"""), blockUntilDestroyed = true)
        val cancel = AtomicBoolean(false)
        thread { Thread.sleep(200); cancel.set(true) }
        val r = MocapProcessRunner({ _, _ -> process }).run(listOf("t"), cancel = cancel)
        assertTrue(process.destroyed)
        assertTrue(r.cancelled)
        assertFalse(r.ok)
    }

    @Test
    fun `Backend_BuildsArgvWithoutAShell`() {
        val tool = FakeMocapTool()
        val script = File(dir, "my scripts/extract.py")
        val backend = MediaPipeBackend(MocapProcessRunner(tool), { MocapSettings("C:/Py thon/python.exe", "C:/m/pose.task") }, { script })
        backend.checkSetup()
        assertEquals(listOf("C:/Py thon/python.exe", script.absolutePath, "check", "--model", "C:/m/pose.task"), tool.calls.single())
    }

    @Test
    fun `Backend_CheckSetupExplainsWhatIsMissing`() {
        val tool = FakeMocapTool()
        val backend = MediaPipeBackend(MocapProcessRunner(tool), { MocapSettings() }, { File(dir, "s.py") })
        val ok = backend.checkSetup()
        assertTrue(ok.ok); assertEquals("0.10.14", ok.mediapipe); assertEquals("solutions", ok.api)

        val store = MediaPipeBackend(MocapProcessRunner({ _, _ ->
            FakeProcess(emptyList(), listOf("Python was not found; run without arguments to install from the Microsoft Store"), exit = 9009)
        }), { MocapSettings() }, { File(dir, "s.py") }).checkSetup()
        assertFalse(store.ok); assertEquals(SetupHint.INSTALL_PYTHON, store.hint)

        tool.checkLine = """{"error":"MediaPipe is not installed: pip install mediapipe"}"""
        val missing = MediaPipeBackend(MocapProcessRunner({ a, d -> tool.launch(a, d).let { FakeProcess(listOf(tool.checkLine), exit = 1) } }), { MocapSettings() }, { File(dir, "s.py") }).checkSetup()
        assertEquals(SetupHint.INSTALL_MEDIAPIPE, missing.hint)
        assertEquals(SetupHint.MODEL_FILE, MediaPipeBackend.hintFor("Model file not found: x.task"))
    }

    @Test
    fun `Backend_ProbeAndExtractParseTheToolOutput`() {
        val tool = FakeMocapTool(extractError = "Cannot open video: v.mp4")
        val backend = MediaPipeBackend(MocapProcessRunner(tool), { MocapSettings() }, { File(dir, "s.py") })
        val info = backend.probe(File(dir, "v.mp4"), File(dir, "t.png"))
        assertEquals(1280, info.width); assertEquals(3f, info.duration); assertTrue(info.thumbnail!!.isFile)
        val e = assertThrows<MocapException> { backend.extract(File(dir, "v.mp4"), File(dir, "out.json"), AtomicBoolean(false)) {} }
        assertEquals("Cannot open video: v.mp4", e.message)
    }

    @Test
    fun `Scripts_TheAdapterShipsWithTheEditor`() {
        val script = MocapScripts.mediaPipe()
        assertTrue(script.isFile)
        val text = script.readText()
        listOf("def cmd_check", "def cmd_probe", "def cmd_extract", "\"version\": 1", "detect_for_video").forEach { assertTrue(it in text, it) }
        assertFalse(Regex("""urllib|requests\.|http://|https://""").containsMatchIn(text), "the adapter never downloads anything")
    }
}
