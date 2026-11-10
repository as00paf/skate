package com.pafoid.skate.engine.animation.mocap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MediaPipeJsonTest {
    private fun doc(world: String) = """{"version":1,"fps":25,"width":1280,"height":720,"frames":[{"t":0,"world":$world,"image":null},{"t":0.04,"world":null}]}"""
    private val leftHandRaisedTowardCamera = (0 until PoseLandmark.COUNT).joinToString(",", "[", "]") { i ->
        if (i == PoseLandmark.LEFT_WRIST) "[0.3,-0.5,-0.2,0.8]" else "[0,0,0,1]"
    }

    @Test
    fun `MediaPipeJson_ConvertsToEngineAxes`() {
        val seq = MediaPipeJson.parse(doc(leftHandRaisedTowardCamera))
        assertEquals(25f, seq.fps); assertEquals(1280, seq.width); assertEquals(2, seq.frames.size)
        val wrist = seq.frames[0].point(PoseLandmark.LEFT_WRIST)
        // Image right (the subject's left) stays +X; MediaPipe's y-down becomes up; toward the camera becomes +Z (facing).
        assertEquals(0.3f, wrist.x, 1e-6f); assertEquals(0.5f, wrist.y, 1e-6f); assertEquals(0.2f, wrist.z, 1e-6f)
        assertEquals(0.8f, seq.frames[0].visibility!![PoseLandmark.LEFT_WRIST])
        assertFalse(seq.frames[1].detected)
        assertNull(seq.frames[1].image)
        assertEquals(1, seq.detectedFrames)
    }

    @Test
    fun `MediaPipeJson_FlipFacingTurnsAroundY`() {
        val wrist = MediaPipeJson.parse(doc(leftHandRaisedTowardCamera), flipFacing = true).frames[0].point(PoseLandmark.LEFT_WRIST)
        assertEquals(-0.3f, wrist.x, 1e-6f); assertEquals(0.5f, wrist.y, 1e-6f); assertEquals(-0.2f, wrist.z, 1e-6f)
    }

    @Test
    fun `MediaPipeJson_RejectsOtherVersionsAndBadFrames`() {
        assertThrows<IllegalArgumentException> { MediaPipeJson.parse("""{"version":2,"frames":[]}""") }
        assertThrows<IllegalArgumentException> { MediaPipeJson.parse("not json") }
        assertThrows<IllegalArgumentException> { MediaPipeJson.parse(doc("[[0,0,0,1]]")) }
    }

    @Test
    fun `MediaPipeJson_RoundTripsTheSyntheticWriter`() {
        val seq = SyntheticPose.sequence(30f, List(2) { SyntheticPose.landmarks(SyntheticPose.samplePose) })
        val back = MediaPipeJson.parse(SyntheticPose.json(seq))
        for (i in 0 until PoseLandmark.COUNT * 3) assertEquals(seq.frames[1].world!![i], back.frames[1].world!![i], 1e-5f)
    }
}
