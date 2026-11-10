package com.pafoid.skate.editor.animation.mocap

import com.pafoid.skate.editor.commands.animation.CreateAnimationClipCommand
import com.pafoid.skate.editor.settings.MocapSettings
import com.pafoid.skate.engine.animation.edit.SkAnimFormat
import com.pafoid.skate.engine.animation.mocap.Bvh
import com.pafoid.skate.engine.animation.mocap.PoseSolver
import com.pafoid.skate.engine.animation.mocap.SolveOptions
import com.pafoid.skate.engine.animation.mocap.SyntheticPose
import com.pafoid.skate.engine.animation.mocap.Smoothing
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import com.pafoid.skate.engine.assets.loaders.AssimpLoader
import io.mockk.mockk
import org.joml.Quaternionf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** ANI-17 acceptance: a local video becomes a `.skanim` on the skater rig, with the tool faked (no Python needed). */
class VideoToAnimationControllerTest {
    @TempDir lateinit var dir: File

    private val skater: Skeleton by lazy { AssimpLoader(mockk(relaxed = true), mockk(relaxed = true)).loadModel(JAMES).skeleton!! }
    private val tool = FakeMocapTool(landmarkJson = SyntheticPose.json(SyntheticPose.sequence(30f, (0 until 12).map { f ->
        SyntheticPose.landmarks(mapOf(
            "LeftArm" to Quaternionf().rotationZ(SyntheticPose.deg(-10f * f)),
            "RightUpLeg" to Quaternionf().rotationX(SyntheticPose.deg(-3f * f)),
            "RightLeg" to Quaternionf().rotationX(SyntheticPose.deg(4f * f)),
        ))
    })))
    private val fetcher = FakeModelFetcher()
    private val created = ArrayList<File>()
    private var project: File? = null

    private fun controller(rig: Skeleton? = skater) = VideoToAnimationController(
        backend = MediaPipeBackend(MocapProcessRunner(tool), { MocapSettings() }, fakeModels(dir, fetcher), { File(dir, "extract.py") }),
        workDir = { File(dir, "work") },
        animationsDir = { project?.let { File(it, "Assets/Animations") } },
        targetSkeleton = { rig },
        loadAnimationFile = { f -> AnimationLoader().loadAnimations(f.absolutePath).first() to null },
        saveClip = { clip, file -> CreateAnimationClipCommand(clip, file).execute() },
        onCreated = { created += it },
        background = { it() },
        main = { it() },
    )

    private fun video() = File(dir, "my kickflip.mp4").apply { writeBytes(ByteArray(8)) }

    @Test
    fun `VideoToAnimation_OneClickMakesAClipThatPlaysOnTheSkater`() {
        project = File(dir, "Game")
        val c = controller()
        assertFalse(c.canCreate, "nothing to do before a video is chosen")
        c.selectVideo(video())
        assertEquals(MocapPhase.READY, c.phase)
        assertEquals(1280, c.info!!.width)
        assertTrue(c.canCreate)

        c.create()
        assertEquals(MocapPhase.DONE, c.phase, "error: ${c.error}")
        val file = c.result!!
        assertEquals(File(project, "Assets/Animations/my kickflip.skanim").absolutePath, file.absolutePath)
        assertEquals(listOf(file), created)
        assertEquals(12, c.landmarks!!.frames.size, "landmarks stay for the preview")
        assertTrue(c.coverage > 0.9f, "coverage ${c.coverage}")

        val clip = AnimationLoader().loadAnimations(file.absolutePath).single()
        assertEquals("my kickflip", clip.name)
        assertTrue(clip.channels.isNotEmpty())
        assertTrue(clip.channels.all { skater.getBoneByName(it.targetNodeName) != null }, "every channel drives a skater bone")
        assertTrue(listOf("LeftArm", "RightUpLeg", "RightLeg").all { b -> clip.channels.any { it.targetNodeName.endsWith(b) } })

        c.create()
        assertTrue(c.result!!.name == "my kickflip 2.skanim", "a second run never overwrites: ${c.result!!.name}")
    }

    @Test
    fun `VideoToAnimation_ShowsTheToolErrorAndRecovers`() {
        project = File(dir, "Game")
        tool.extractError = "Cannot open video: corrupt"
        val c = controller()
        c.selectVideo(video())
        c.create()
        assertEquals(MocapPhase.FAILED, c.phase)
        assertEquals("Cannot open video: corrupt", c.error)
        assertNull(c.result)
        assertTrue(c.canCreate, "the user can retry")
    }

    @Test
    fun `VideoToAnimation_DownloadsThePoseModelOnceThenUsesTheTasksApi`() {
        project = File(dir, "Game")
        val c = controller()
        c.selectVideo(video())
        c.create()
        assertEquals(MocapPhase.DONE, c.phase, "error: ${c.error}")
        c.create()
        assertEquals(1, fetcher.fetches, "the model is downloaded once and reused")
        val extracts = tool.calls.filter { it[2] == "extract" }
        assertEquals(2, extracts.size)
        assertTrue(extracts.all { it.contains("--model") && it.last().endsWith(PoseModelStore.FILE_NAME) }, "extract gets the model: $extracts")
    }

    @Test
    fun `VideoToAnimation_OfflineWithoutTheLegacyApi_SaysWhy`() {
        project = File(dir, "Game")
        fetcher.failure = "UnknownHostException: storage.googleapis.com"
        val c = controller()
        c.selectVideo(video())
        c.create()
        assertEquals(MocapPhase.FAILED, c.phase)
        val error = c.error!!
        assertTrue(error.startsWith("Could not download the pose model"), error)
        assertTrue(error.contains(MediaPipeBackend.NO_API), error)
        assertTrue(c.canCreate, "the user can retry once online")

        tool.hasSolutions = true
        c.create()
        assertEquals(MocapPhase.DONE, c.phase, "an older MediaPipe still works offline through the legacy API: ${c.error}")
    }

    @Test
    fun `VideoToAnimation_NeedsAProjectAndARig`() {
        project = null
        val c = controller()
        c.selectVideo(video())
        assertFalse(c.canCreate, "no project: nowhere to save")

        project = File(dir, "Game")
        val noRig = controller(rig = null)
        noRig.selectVideo(video())
        noRig.create()
        assertEquals(MocapPhase.FAILED, noRig.phase)
        assertTrue(noRig.error!!.contains("skeleton"))
    }

    @Test
    fun `VideoToAnimation_ImportsABvhFromAnotherTool`() {
        project = File(dir, "Game")
        val solved = PoseSolver.solve(MediaPipeJsonAccess.parse(tool.landmarkJson), "walk", SolveOptions(smoothing = Smoothing.NONE))
        val bvh = File(dir, "walk.bvh").apply { writeText(Bvh.write(solved.skeleton, solved.clip)) }
        val c = controller()
        c.importFile(bvh)
        assertEquals(MocapPhase.DONE, c.phase, "error: ${c.error}")
        val clip = SkAnimFormat.decode(c.result!!.readText())
        assertTrue(clip.tracks.all { skater.getBoneByName(it.bone) != null })
    }

    @Test
    fun `CreateAnimationClip_PicksAFreeName`() {
        val d = File(dir, "a").apply { mkdirs() }
        assertEquals("run.skanim", CreateAnimationClipCommand.freeFile(d, "run").name)
        File(d, "run.skanim").writeText("x")
        assertEquals("run 2.skanim", CreateAnimationClipCommand.freeFile(d, "run").name)
        assertEquals("a_b.skanim", CreateAnimationClipCommand.freeFile(d, "a/b").name)
    }

    private companion object {
        const val JAMES = "assets/characters/james.glb"
    }
}

private object MediaPipeJsonAccess {
    fun parse(text: String) = com.pafoid.skate.engine.animation.mocap.MediaPipeJson.parse(text)
}
