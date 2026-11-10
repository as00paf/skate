package com.pafoid.skate.editor.animation.mocap

import com.pafoid.skate.editor.settings.MocapSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun backend(tool: ProcessLauncher, settings: MocapSettings = MocapSettings(), fetcher: FakeModelFetcher = FakeModelFetcher()) =
        MediaPipeBackend(MocapProcessRunner(tool), { settings }, fakeModels(dir, fetcher), { File(dir, "s.py") })

    @Test
    fun `Backend_BuildsArgvWithoutAShell`() {
        val tool = FakeMocapTool()
        val script = File(dir, "my scripts/extract.py")
        val model = File(dir, "my models/pose.task").apply { parentFile.mkdirs(); writeBytes(ByteArray(10)) }
        val fetcher = FakeModelFetcher()
        val backend = MediaPipeBackend(MocapProcessRunner(tool), { MocapSettings("C:/Py thon/python.exe", model.path) }, fakeModels(dir, fetcher), { script })
        val check = backend.checkSetup()
        assertEquals(listOf("C:/Py thon/python.exe", script.absolutePath, "check", "--model", model.absolutePath), tool.calls.single())
        assertEquals(0, fetcher.fetches, "a model set in Setup is never downloaded over")
        assertEquals("tasks", check.api)
    }

    @Test
    fun `Backend_CheckSetup_DownloadsTheModelAndReportsTheTasksApi`() {
        val tool = FakeMocapTool()
        val fetcher = FakeModelFetcher()
        val progress = ArrayList<Float>()
        val ok = backend(tool, fetcher = fetcher).checkSetup { progress += it }
        assertTrue(ok.ok, ok.problem)
        assertEquals("tasks", ok.api, "the Tasks API is the default")
        assertEquals("0.10.21", ok.mediapipe)
        assertTrue(ok.model!!.endsWith(PoseModelStore.FILE_NAME))
        assertNull(ok.fallback)
        assertEquals(0f, progress.first()); assertTrue(0.5f in progress); assertEquals(1f, progress.last())
        backend(tool, fetcher = fetcher).checkSetup()
        assertEquals(1, fetcher.fetches, "downloaded once")
    }

    @Test
    fun `Backend_CheckSetup_FallsBackToTheLegacyApiOnlyWhenTheTasksApiCannotRun`() {
        val offline = FakeModelFetcher(failure = "UnknownHostException")
        val legacy = backend(FakeMocapTool(mediapipe = "0.10.9", hasSolutions = true), fetcher = offline).checkSetup()
        assertTrue(legacy.ok, legacy.problem)
        assertEquals("solutions", legacy.api, "reports the API actually used")
        assertTrue(legacy.fallback!!.contains("Could not download the pose model"), legacy.fallback)

        val neither = backend(FakeMocapTool(), fetcher = offline).checkSetup()
        assertFalse(neither.ok)
        assertTrue(neither.problem!!.startsWith("Could not download the pose model"), "root cause first: ${neither.problem}")
        assertTrue(neither.problem!!.contains(MediaPipeBackend.NO_API))
        assertEquals(SetupHint.MODEL_FILE, neither.hint)

        val broken = backend(FakeMocapTool(checkLine = """{"error":"${MediaPipeBackend.NO_API}. Tasks API (pose model): OSError: libGLESv2.so.2. Legacy solutions API: not in MediaPipe 0.10.21."}""")).checkSetup()
        assertFalse(broken.ok)
        assertEquals(SetupHint.UPDATE_MEDIAPIPE, broken.hint, "the model is there, so MediaPipe itself is the problem")
    }

    @Test
    fun `Backend_CheckSetupExplainsWhatIsMissing`() {
        val store = MediaPipeBackend(MocapProcessRunner({ _, _ ->
            FakeProcess(emptyList(), listOf("Python was not found; run without arguments to install from the Microsoft Store"), exit = 9009)
        }), { MocapSettings() }, fakeModels(dir), { File(dir, "s.py") }).checkSetup()
        assertFalse(store.ok); assertEquals(SetupHint.INSTALL_PYTHON, store.hint)

        val missing = backend(FakeMocapTool(checkLine = """{"error":"MediaPipe is not installed: pip install mediapipe"}""")).checkSetup()
        assertEquals(SetupHint.INSTALL_MEDIAPIPE, missing.hint)
        assertEquals(SetupHint.MODEL_FILE, MediaPipeBackend.hintFor("Model file not found: x.task"))
        assertEquals(SetupHint.INSTALL_PYTHON, MediaPipeBackend.hintFor("Could not download the pose model from u: x. Could not start python: error=2"))

        val badOverride = backend(FakeMocapTool(), MocapSettings(modelPath = File(dir, "gone.task").path)).checkSetup()
        assertFalse(badOverride.ok)
        assertEquals(SetupHint.MODEL_FILE, badOverride.hint, badOverride.problem)
    }

    @Test
    fun `Backend_ProbeAndExtractParseTheToolOutput`() {
        val tool = FakeMocapTool(extractError = "Cannot open video: v.mp4")
        val backend = backend(tool)
        val info = backend.probe(File(dir, "v.mp4"), File(dir, "t.png"))
        assertEquals(1280, info.width); assertEquals(3f, info.duration); assertTrue(info.thumbnail!!.isFile)
        val e = assertThrows<MocapException> { backend.extract(File(dir, "v.mp4"), File(dir, "out.json"), AtomicBoolean(false)) {} }
        assertEquals("Cannot open video: v.mp4", e.message)
    }

    @Test
    fun `Backend_CancelDuringTheModelDownload_RunsNoDetection`() {
        val tool = FakeMocapTool()
        val cancel = AtomicBoolean(false)
        val e = assertThrows<MocapException> {
            backend(tool).extract(File(dir, "v.mp4"), File(dir, "out.json"), cancel, onModelProgress = { if (it >= 0.5f) cancel.set(true) }) {}
        }
        assertEquals("cancelled", e.message)
        assertTrue(tool.calls.isEmpty())
        assertFalse(File(dir, "models/${PoseModelStore.FILE_NAME}").exists(), "no half model is kept")
    }

    @Test
    fun `ModelStore_KeepsOnlyCompleteDownloads`() {
        val fetcher = FakeModelFetcher(size = 10)
        val e = assertThrows<MocapException> { fakeModels(dir, fetcher).resolve(null) }
        assertTrue(e.message!!.contains("incomplete"), e.message)
        assertFalse(File(dir, "models").listFiles().orEmpty().any(), "neither the model nor the .part file is left")
        fetcher.size = 2_000
        assertEquals(2_000L, fakeModels(dir, fetcher).resolve(null).length())
    }

    @Test
    fun `Scripts_TheAdapterShipsWithTheEditor`() {
        val script = MocapScripts.mediaPipe()
        assertTrue(script.isFile)
        val text = script.readText()
        listOf("def cmd_check", "def cmd_probe", "def cmd_extract", "\"version\": 1", "detect_for_video", "PoseLandmarker", MediaPipeBackend.NO_API)
            .forEach { assertTrue(it in text, it) }
        // ANI-18: the Tasks API is tried first; mp.solutions is only the fallback.
        assertTrue(text.indexOf("TasksDetector(mp, model)") in 0 until text.indexOf("SolutionsDetector(mp)", text.indexOf("def open_detector")))
        assertFalse(Regex("""urllib|requests\.|http://|https://""").containsMatchIn(text), "the adapter never downloads anything")
    }
}
