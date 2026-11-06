package com.pafoid.skate.editor.gizmos

import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * EDT-44: camera/light icons keep their shape and screen size under any editor camera motion, and light icons are
 * tinted with the light color. Icons are projected the way the renderer does it (CameraManager's view/projection).
 */
class EditorIconBillboardTest {
    private val fov = 45f
    private val zoom = 1f
    private val width = 1920
    private val height = 1080
    private val fraction = 0.03f

    /** View matrix built exactly like CameraManager.calculateView. */
    private fun view(rotationDegrees: Vector3f, position: Vector3f): Matrix4f = Matrix4f()
        .rotate(Math.toRadians(rotationDegrees.x.toDouble()).toFloat(), Vector3f(1f, 0f, 0f))
        .rotate(Math.toRadians(rotationDegrees.y.toDouble()).toFloat(), Vector3f(0f, 1f, 0f))
        .rotate(Math.toRadians(rotationDegrees.z.toDouble()).toFloat(), Vector3f(0f, 0f, 1f))
        .translate(Vector3f(position).negate())

    private fun projection() = Matrix4f().perspective(
        Math.toRadians(fov.toDouble()).toFloat() * zoom, width.toFloat() / height, 0.1f, 1000f,
    )

    /** Pixel coordinates of every icon vertex for a camera at [camPos]/[camRot] and an icon at [iconPos]. */
    private fun projectedIcon(camRot: Vector3f, camPos: Vector3f, iconPos: Vector3f, camera: Boolean): List<Pair<Float, Float>> {
        val basis = EditorVisuals.viewBasis(camRot)
        val depth = EditorVisuals.viewDepth(camPos, basis.forward, iconPos)
        val size = GizmoMath.gizmoSize(false, depth, fov, zoom, height, fraction)
        val segs = if (camera) EditorVisuals.cameraIcon(iconPos, basis.right, basis.up, size)
        else EditorVisuals.lightIcon(iconPos, basis.right, basis.up, size)
        val clip = Matrix4f(projection()).mul(view(camRot, camPos))
        return segs.flatMap { listOf(it.a, it.b) }.map { p ->
            val v = clip.transform(Vector4f(p, 1f))
            ((v.x / v.w + 1f) * 0.5f * width) to ((v.y / v.w + 1f) * 0.5f * height)
        }
    }

    /** Icon vertices relative to the icon's first vertex, in pixels: equal lists mean equal shape and size. */
    private fun shape(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val (x0, y0) = points.first()
        return points.map { (x, y) -> (x - x0) to (y - y0) }
    }

    private fun assertSameShape(expected: List<Pair<Float, Float>>, actual: List<Pair<Float, Float>>, tolPx: Float = 0.05f) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertEquals(e.first, a.first, tolPx, "x")
            assertEquals(e.second, a.second, tolPx, "y")
        }
    }

    @Test
    fun ViewBasis_MatchesCameraManagerViewRows() {
        listOf(Vector3f(), Vector3f(30f, 45f, 0f), Vector3f(-80f, 170f, 10f), Vector3f(12f, -300f, 0f)).forEach { rot ->
            val basis = EditorVisuals.viewBasis(rot)
            val fromView = EditorVisuals.viewBasis(view(rot, Vector3f(3f, 4f, 5f)))
            assertTrue(basis.right.equals(fromView.right, 1e-5f))
            assertTrue(basis.up.equals(fromView.up, 1e-5f))
            assertTrue(basis.forward.equals(fromView.forward, 1e-5f))
            // The camera looks down -Z of its view: forward maps to (0, 0, -1) in view space.
            val f = view(rot, Vector3f()).transformDirection(Vector3f(basis.forward))
            assertTrue(f.equals(Vector3f(0f, 0f, -1f), 1e-5f), "forward for $rot was $f")
        }
    }

    @Test
    fun ViewDepth_IsDistanceAlongForwardNotStraightLine() {
        val forward = Vector3f(0f, 0f, -1f)
        assertEquals(10f, EditorVisuals.viewDepth(Vector3f(), forward, Vector3f(5f, 3f, -10f)), 1e-5f)
        // Behind the camera clamps to the minimum depth instead of going negative.
        assertEquals(0.1f, EditorVisuals.viewDepth(Vector3f(), forward, Vector3f(0f, 0f, 4f), 0.1f), 1e-6f)
    }

    @Test
    fun Icons_KeepShapeAndScreenSize_WhenCameraRotates() {
        val iconPos = Vector3f(0f, 2f, -10f)
        val camPos = Vector3f(0f, 2f, 0f)
        for (camera in listOf(true, false)) {
            val centred = shape(projectedIcon(Vector3f(0f, 0f, 0f), camPos, iconPos, camera))
            // Turning the camera moves the icon towards the screen edges; it must not stretch or grow there.
            listOf(Vector3f(0f, 25f, 0f), Vector3f(0f, -30f, 0f), Vector3f(15f, 20f, 0f), Vector3f(-18f, -25f, 0f)).forEach { rot ->
                assertSameShape(centred, shape(projectedIcon(rot, camPos, iconPos, camera)))
            }
        }
    }

    @Test
    fun Icons_KeepShapeAndScreenSize_WhenCameraMoves() {
        val iconPos = Vector3f(1f, 0f, 0f)
        val rot = Vector3f(20f, 35f, 0f)
        val basis = EditorVisuals.viewBasis(rot)
        val reference = shape(projectedIcon(rot, Vector3f(iconPos).sub(Vector3f(basis.forward).mul(10f)), iconPos, false))
        // Dolly in/out and strafe: same pixels every time.
        listOf(3f, 40f, 250f).forEach { d ->
            val camPos = Vector3f(iconPos).sub(Vector3f(basis.forward).mul(d)).add(Vector3f(basis.right).mul(d * 0.3f))
            assertSameShape(reference, shape(projectedIcon(rot, camPos, iconPos, false)))
        }
    }

    @Test
    fun Icons_ScreenHeightIsTheConfiguredFractionOfTheViewport() {
        val pts = projectedIcon(Vector3f(10f, 40f, 0f), Vector3f(0f, 0f, 0f), Vector3f(-20f, -5f, -30f), false)
        val heightPx = pts.maxOf { it.second } - pts.minOf { it.second }
        // Light icon spans [-size, size] vertically: two icon sizes, each `fraction` of the viewport height.
        assertEquals(2f * fraction * height, heightPx, 0.5f)
    }

    @Test
    fun StraightLineDistance_WouldGrowIconsAtScreenEdges() {
        // Regression guard for the old sizing: the same icon off-centre is farther than it is deep.
        val forward = Vector3f(0f, 0f, -1f)
        val offCentre = Vector3f(8f, 0f, -10f)
        assertTrue(Vector3f().distance(offCentre) > EditorVisuals.viewDepth(Vector3f(), forward, offCentre) * 1.2f)
    }

    @Test
    fun LightTint_FollowsLightColor() {
        assertTrue(EditorVisuals.lightTint(Vector3f(1f, 0.2f, 0.1f)).equals(Vector3f(1f, 0.2f, 0.1f), 1e-6f))
        // HDR colors keep their hue inside 0..1.
        assertTrue(EditorVisuals.lightTint(Vector3f(4f, 2f, 0f)).equals(Vector3f(1f, 0.5f, 0f), 1e-6f))
        // Very dark colors are lifted to stay visible, still with their hue.
        val dark = EditorVisuals.lightTint(Vector3f(0f, 0f, 0.05f))
        assertEquals(EditorVisuals.MIN_TINT_BRIGHTNESS, dark.z, 1e-6f)
        assertEquals(0f, dark.x + dark.y, 1e-6f)
        // Black (or negative) falls back to grey.
        assertTrue(EditorVisuals.lightTint(Vector3f(-1f, 0f, 0f)).equals(Vector3f(EditorVisuals.MIN_TINT_BRIGHTNESS), 1e-6f))
        assertTrue(abs(EditorVisuals.lightTint(Vector3f(0.5f)).x - 0.5f) < 1e-6f)
    }
}
