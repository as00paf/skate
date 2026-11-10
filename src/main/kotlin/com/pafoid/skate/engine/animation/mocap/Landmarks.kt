package com.pafoid.skate.engine.animation.mocap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.joml.Vector3f

/** MediaPipe Pose landmark indices (33 points; "left" is the subject's left). */
object PoseLandmark {
    const val COUNT = 33
    const val NOSE = 0
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32

    /** Segments drawn by previews (pairs of landmark indices). */
    val SKELETON_LINES = listOf(
        LEFT_SHOULDER to RIGHT_SHOULDER, LEFT_HIP to RIGHT_HIP, LEFT_SHOULDER to LEFT_HIP, RIGHT_SHOULDER to RIGHT_HIP,
        LEFT_SHOULDER to LEFT_ELBOW, LEFT_ELBOW to LEFT_WRIST, RIGHT_SHOULDER to RIGHT_ELBOW, RIGHT_ELBOW to RIGHT_WRIST,
        LEFT_HIP to LEFT_KNEE, LEFT_KNEE to LEFT_ANKLE, RIGHT_HIP to RIGHT_KNEE, RIGHT_KNEE to RIGHT_ANKLE,
        LEFT_ANKLE to LEFT_HEEL, LEFT_HEEL to LEFT_FOOT_INDEX, LEFT_ANKLE to LEFT_FOOT_INDEX,
        RIGHT_ANKLE to RIGHT_HEEL, RIGHT_HEEL to RIGHT_FOOT_INDEX, RIGHT_ANKLE to RIGHT_FOOT_INDEX,
        LEFT_EAR to NOSE, RIGHT_EAR to NOSE,
    )
}

/**
 * One video frame. [world] holds 33 points in engine axes (metres, Y-up, the subject facing +Z, origin between the
 * hips), [visibility] 0..1 per point, [image] normalized image coordinates (x right, y down) for previews.
 * All three are null when no person was detected.
 */
class LandmarkFrame(val time: Float, val world: FloatArray?, val visibility: FloatArray?, val image: FloatArray?) {
    fun point(i: Int, out: Vector3f = Vector3f()): Vector3f = out.set(world!![i * 3], world[i * 3 + 1], world[i * 3 + 2])
    val detected: Boolean get() = world != null
}

class LandmarkSequence(
    val fps: Float,
    val width: Int,
    val height: Int,
    val frames: List<LandmarkFrame>,
    val source: String = "",
) {
    val duration: Float get() = frames.lastOrNull()?.time ?: 0f
    val detectedFrames: Int get() = frames.count { it.detected }
}

/**
 * Reads the JSON written by `mediapipe_extract.py` (format version 1, documented in docs/mocap-setup.md).
 *
 * MediaPipe world landmarks are in metres around the hips with x to the image right, y down and z away from the
 * camera. For a subject facing the camera that is (x, y, z) -> engine (x, -y, -z): a 180° turn about X, so
 * handedness is kept and the subject faces +Z with their left on +X. **Unverified against a real MediaPipe run**
 * (no MediaPipe install was available when this was written); [flipFacing] turns the result around if it is wrong.
 */
object MediaPipeJson {
    const val FORMAT_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String, flipFacing: Boolean = false): LandmarkSequence {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Landmark file is not valid JSON: ${e.message}", e)
        }
        val version = root["version"]?.jsonPrimitive?.int ?: 0
        require(version == FORMAT_VERSION) { "Unsupported landmark file version $version (expected $FORMAT_VERSION)" }
        val fps = root["fps"]?.jsonPrimitive?.float?.takeIf { it > 0f } ?: 30f
        val frames = root["frames"]?.jsonArray ?: JsonArray(emptyList())
        return LandmarkSequence(
            fps = fps,
            width = root["width"]?.jsonPrimitive?.int ?: 0,
            height = root["height"]?.jsonPrimitive?.int ?: 0,
            frames = frames.mapIndexed { i, f -> frame(f.jsonObject, i / fps, flipFacing) },
            source = (root["source"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "",
        )
    }

    private fun frame(f: JsonObject, defaultTime: Float, flip: Boolean): LandmarkFrame {
        val time = f["t"]?.jsonPrimitive?.float ?: defaultTime
        val world = f["world"].rows() ?: return LandmarkFrame(time, null, null, f["image"].rows()?.let(::image))
        require(world.size == PoseLandmark.COUNT) { "Frame at ${time}s has ${world.size} world landmarks, expected ${PoseLandmark.COUNT}" }
        val points = FloatArray(PoseLandmark.COUNT * 3)
        val visibility = FloatArray(PoseLandmark.COUNT)
        for ((i, row) in world.withIndex()) {
            val sx = if (flip) -1f else 1f
            points[i * 3] = row[0] * sx
            points[i * 3 + 1] = -row[1]
            points[i * 3 + 2] = -row[2] * sx
            visibility[i] = row.getOrElse(3) { 1f }
        }
        return LandmarkFrame(time, points, visibility, f["image"].rows()?.let(::image))
    }

    private fun image(rows: List<FloatArray>) = FloatArray(rows.size * 2) { rows[it / 2][it % 2] }

    private fun JsonElement?.rows(): List<FloatArray>? {
        if (this == null || this is JsonNull) return null
        return jsonArray.map { r -> r.jsonArray.map { it.jsonPrimitive.float }.toFloatArray() }
    }
}
