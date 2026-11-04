package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.assets.procedural.MeshBuilder
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The skateboard model, built in metres: a 0.81 m deck with concave, kicktails and round nose and tail, two trucks
 * and four wheels, about 3K triangles. Same frame as [Skateboard]: origin at the wheels' ground contact, centred,
 * deck +Y up, nose toward +Z. Parts are told apart by vertex colour (grip tape, wood edge, graphic, metal, urethane).
 * Written to `assets/obj/skateboard.obj` by `SkateparkShapesTest` like the obstacles.
 */
object SkateboardShape {

    const val LENGTH = 0.81f
    const val HALF_WIDTH = 0.1f
    const val DECK_BOTTOM = 0.084f
    const val DECK_THICKNESS = 0.012f
    /** Height of the grip tape on the centre line between the trucks; the rider's feet ([MountTuning.rideHeight]). */
    const val DECK_TOP = DECK_BOTTOM + DECK_THICKNESS
    /** Distance from the centre where the kicktails start to bend up, and how much they rise at the tips. */
    const val KICK_START = 0.27f
    const val KICK_RISE = 0.035f
    /** The rails sit this much higher than the centre line (concave). */
    const val CONCAVE = 0.005f
    const val TRUCK_Z = 0.23f
    const val WHEEL_RADIUS = 0.03f
    const val WHEEL_WIDTH = 0.032f
    const val WHEEL_X = 0.08f

    private const val ACROSS = 8
    private const val ALONG = 32
    private const val TIP_STEPS = 8
    private const val WHEEL_SECTORS = 20

    private val GRIP = Vector3f(0.035f, 0.035f, 0.04f)
    private val MAPLE = Vector3f(0.72f, 0.5f, 0.28f)
    private val GRAPHIC = Vector3f(0.65f, 0.1f, 0.06f)
    private val METAL = Vector3f(0.55f, 0.56f, 0.58f)
    private val URETHANE = Vector3f(0.92f, 0.86f, 0.68f)

    fun mesh(): MeshBuilder {
        val mesh = MeshBuilder()
        deck(mesh)
        for (z in listOf(-TRUCK_Z, TRUCK_Z)) {
            truck(mesh, z)
            for (x in listOf(-WHEEL_X, WHEEL_X)) mesh.append(wheel(), Matrix4f().translate(x, WHEEL_RADIUS, z).rotateZ(-PI.toFloat() / 2f))
        }
        return mesh
    }

    /** Height of the deck's centre line above [DECK_TOP] at [z]: flat between the kicks, then curving up. */
    fun kick(z: Float): Float {
        val a = abs(z) - KICK_START
        if (a <= 0f) return 0f
        val t = a / (LENGTH / 2f - KICK_START)
        return KICK_RISE * t * t
    }

    /** (z, half width) of each row along the deck: straight sides, then quarter circles round to the tips. */
    fun outline(): List<Vector2f> {
        val straight = LENGTH / 2f - HALF_WIDTH
        val tip = (1..TIP_STEPS).map { i ->
            val angle = (PI / 2 * i / TIP_STEPS).toFloat()
            Vector2f(straight + HALF_WIDTH * sin(angle), HALF_WIDTH * cos(angle))
        }
        val middle = (0..ALONG).map { i -> Vector2f(-straight + 2f * straight * i / ALONG, HALF_WIDTH) }
        return tip.reversed().map { Vector2f(-it.x, it.y) } + middle + tip
    }

    private fun deck(mesh: MeshBuilder) {
        val rows = outline()
        val top = { z: Float, x: Float -> Vector3f(x, DECK_TOP + kick(z) + CONCAVE * (x / HALF_WIDTH) * (x / HALF_WIDTH), z) }
        val bottom = { z: Float, x: Float -> top(z, x).sub(0f, DECK_THICKNESS, 0f) }
        val across = { row: Vector2f, point: (Float, Float) -> Vector3f ->
            (0..ACROSS).map { c -> point(row.x, row.y * (2f * c / ACROSS - 1f)) }
        }
        mesh.color = GRIP
        mesh.grid(rows.map { across(it, top) }) { MeshBuilder.UP }
        mesh.color = GRAPHIC
        mesh.grid(rows.map { across(it, bottom) }) { MeshBuilder.DOWN }
        // Edge band all round: away from the deck's centre line (radially at the round ends).
        val straight = LENGTH / 2f - HALF_WIDTH
        val outward = { p: Vector3f -> Vector3f(p.x, 0f, p.z - p.z.coerceIn(-straight, straight)) }
        mesh.color = MAPLE
        for (side in listOf(-1f, 1f)) {
            mesh.grid(rows.map { r -> listOf(bottom(r.x, side * r.y), top(r.x, side * r.y)) }, outward)
        }
        mesh.color = Vector3f(1f)
    }

    private fun truck(mesh: MeshBuilder, z: Float) {
        mesh.color = METAL
        // Baseplate under the deck, pivot block, hanger with its wedge, and the axle through the wheels.
        mesh.box(Vector3f(-0.03f, 0.076f, z - 0.032f), Vector3f(0.03f, DECK_BOTTOM + 0.0005f, z + 0.032f))
        mesh.box(Vector3f(-0.012f, 0.045f, z - 0.014f), Vector3f(0.012f, 0.076f, z + 0.014f))
        mesh.tube(Vector3f(-0.062f, 0.04f, z), Vector3f(0.062f, 0.04f, z), 0.011f, sides = 10)
        mesh.tube(Vector3f(-0.045f, 0.04f, z), Vector3f(0f, 0.054f, z), 0.009f, sides = 8)
        mesh.tube(Vector3f(0.045f, 0.04f, z), Vector3f(0f, 0.054f, z), 0.009f, sides = 8)
        mesh.tube(Vector3f(-HALF_WIDTH, WHEEL_RADIUS, z), Vector3f(HALF_WIDTH, WHEEL_RADIUS, z), 0.004f, sides = 8)
        mesh.color = Vector3f(1f)
    }

    /** One wheel around the Y axis, centred on the origin, with rounded lips. */
    private fun wheel(): MeshBuilder {
        val r = WHEEL_RADIUS; val h = WHEEL_WIDTH / 2f; val lip = 0.006f
        val profile = listOf(
            Vector2f(0f, h), Vector2f(r - lip, h), Vector2f(r, h - lip),
            Vector2f(r, -h + lip), Vector2f(r - lip, -h), Vector2f(0f, -h),
        )
        return MeshBuilder().apply { color = URETHANE }.revolve(profile, WHEEL_SECTORS)
    }
}
