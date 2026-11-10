package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.edit.EditTestData.assertSameRotation
import com.pafoid.skate.engine.animation.edit.EditTestData.assertVec
import com.pafoid.skate.engine.animation.edit.EditTestData.rotTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.vecTrack
import com.pafoid.skate.engine.animation.edit.EditTestData.yaw
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.AnimationChannel
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.AnimationSampler
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import com.pafoid.skate.engine.assets.loaders.AnimationLoader
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer

class SkAnimFormatTest {
    private val clip = SkClip(
        name = "wave",
        fps = 24f,
        duration = 1f,
        tracks = listOf(
            vecTrack("Hips", 0f to floatArrayOf(0f, 1f, 0f), 1f to floatArrayOf(0f, 1.2f, 0.5f), interpolation = InterpolationType.CUBIC_SPLINE),
            rotTrack("Spine", 0f to yaw(0f), 0.5f to yaw(90f), 1f to yaw(0f)),
        ),
        events = listOf(SkEvent(0.25f, "footstep")),
    )

    @Test
    fun `SkAnim_EncodeDecodeRoundTripsExactly`() {
        val text = SkAnimFormat.encode(clip)
        assertTrue(text.contains("\"version\": 1"), "version is always written")
        assertEquals(clip, SkAnimFormat.decode(text))
    }

    @Test
    fun `SkAnim_RejectsUnsupportedVersionAndMalformedKeys`() {
        assertThrows<IllegalArgumentException> { SkAnimFormat.decode(SkAnimFormat.encode(clip).replace("\"version\": 1", "\"version\": 99")) }
        val badSize = clip.copy(tracks = listOf(SkTrack("Hips", AnimationPath.ROTATION, keys = listOf(SkKey(0f, listOf(0f, 0f, 0f))))))
        assertThrows<IllegalArgumentException> { SkAnimFormat.encode(badSize) }
        val unsorted = clip.copy(tracks = listOf(vecTrack("Hips", 0.5f to floatArrayOf(0f, 0f, 0f), 0.1f to floatArrayOf(0f, 0f, 0f))))
        assertThrows<IllegalArgumentException> { SkAnimFormat.encode(unsorted) }
        assertThrows<IllegalArgumentException> { SkAnimFormat.decode("{ not json") }
    }

    @Test
    fun `SkAnim_ToAnimationPlaysTheKeys`() {
        val anim = SkAnimFormat.toAnimation(clip, "wave.skanim")
        assertEquals("wave", anim.name)
        assertEquals(1f, anim.duration)
        val spine = anim.channels.single { it.targetNodeName == "Spine" }
        val q = Quaternionf().also { spine.sampler.sampleQuaternionf(0.5f, it) }
        assertSameRotation(QuatMath.toRow(yaw(90f)), QuatMath.toRow(q))
        val hips = anim.channels.single { it.targetNodeName == "Hips" }
        assertEquals(InterpolationType.CUBIC_SPLINE, hips.sampler.interpolation)
        val v = Vector3f().also { hips.sampler.sampleVector3f(1f, it) }
        assertVec(floatArrayOf(0f, 1.2f, 0.5f), floatArrayOf(v.x, v.y, v.z))
    }

    @Test
    fun `SkAnim_FromAnimationKeepsCubicTangentsAndSamplesIdentically`() {
        // in, value, out per key: a bump that a recomputed AUTO tangent would not reproduce.
        val values = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 5f, 0f, 0f, /* key 1 */ -5f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f)
        val sampler = AnimationSampler(floatArrayOf(0f, 1f), values, InterpolationType.CUBIC_SPLINE, 3)
        val source = Animation("bump", listOf(AnimationChannel(sampler, "Hips", AnimationPath.TRANSLATION)), 1f, "bump.fbx")

        val editable = SkAnimFormat.fromAnimation(source)
        assertEquals(TangentMode.FREE, editable.tracks.single().keys[0].tangentMode)
        val back = SkAnimFormat.toAnimation(SkAnimFormat.decode(SkAnimFormat.encode(editable)), "bump.skanim").channels.single().sampler
        for (t in listOf(0f, 0.2f, 0.5f, 0.8f, 1f)) {
            val a = Vector3f().also { sampler.sampleVector3f(t, it) }
            val b = Vector3f().also { back.sampleVector3f(t, it) }
            assertEquals(a.x, b.x, 1e-5f, "t=$t")
        }
    }

    @Test
    fun `SkAnim_FromAnimationDropsRepeatedKeyTimes`() {
        val sampler = AnimationSampler(floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, 0f, 9f, 9f, 9f, 1f, 1f, 1f), InterpolationType.LINEAR, 3)
        val editable = SkAnimFormat.fromAnimation(Animation("dup", listOf(AnimationChannel(sampler, "Hips", AnimationPath.TRANSLATION)), 1f, "dup.fbx"))
        assertEquals(listOf(0f, 1f), editable.tracks.single().keys.map { it.time })
        SkAnimFormat.validate(editable)
    }

    @Test
    fun `SkAnim_AnimationLoaderReadsSkanimFromFileAndBuffer`(@TempDir dir: File) {
        val file = File(dir, "wave.skanim").apply { writeText(SkAnimFormat.encode(clip)) }
        val fromFile = AnimationLoader().loadAnimations(file.path).single()
        assertEquals("wave", fromFile.name)
        assertEquals(2, fromFile.channels.size)

        val buffer = ByteBuffer.wrap(file.readBytes())
        val fromBuffer = AnimationLoader().loadAnimations(buffer, "Assets/wave.SKANIM").single()
        assertEquals(fromFile.channels.map { it.targetNodeName }, fromBuffer.channels.map { it.targetNodeName })
        assertEquals(file.length().toInt(), buffer.remaining(), "the caller's buffer is not consumed")
    }
}
