package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.assets.procedural.ConvexPieces
import com.pafoid.skate.engine.assets.procedural.MeshBuilder
import org.joml.Vector2f
import org.joml.Vector3f
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/** A generated obstacle: its render mesh and the convex pieces of its collision. */
class ObstacleShape(val mesh: MeshBuilder, val hulls: List<List<Vector3f>>)

/**
 * Skatepark obstacle geometry (see docs/obstacles.md). Every shape is in metres with its footprint centred on the
 * origin, the bottom at y = 0 and the riding face toward +Z (the direction a rider spawned with GPL-14's
 * orientation rolls). Render meshes are written to OBJ files by [SkateparkShapesTest] (golden files in
 * `assets/Models/`); colliders are rebuilt from the same numbers when a prefab spawns.
 */
object SkateparkShapes {

    const val TRANSITION_STEPS = 10
    const val RISER = 0.17f
    const val TREAD = 0.3f
    const val LANDING = 1.2f

    fun quarterPipe(height: Float = 1.5f, radius: Float = 1.8f, deck: Float = 1.0f, width: Float = 3f): ObstacleShape {
        val curve = transition(height, radius)
        val length = deck + curve.last().x
        val toe = length / 2f
        // Walk from the back of the deck (-Z) down the transition to the toe (+Z).
        val profile = listOf(Vector2f(-toe, height)) + curve.reversed().map { Vector2f(toe - it.x, it.y) }
        return extruded(profile, width)
    }

    /** Mini/half pipe: two transitions facing each other across a flat bottom. */
    fun halfPipe(height: Float = 1.2f, radius: Float = 1.8f, flat: Float = 2f, deck: Float = 0.8f, width: Float = 3f): ObstacleShape {
        val curve = transition(height, radius)
        val half = flat / 2f + curve.last().x + deck
        val left = listOf(Vector2f(-half, height)) + curve.reversed().map { Vector2f(-flat / 2f - it.x, it.y) }
        val right = curve.map { Vector2f(flat / 2f + it.x, it.y) } + Vector2f(half, height)
        return extruded(left + right, width)
    }

    /** Flat incline up to a deck. */
    fun bank(height: Float = 0.6f, run: Float = 1.5f, deck: Float = 0.6f, width: Float = 2f): ObstacleShape {
        val half = (run + deck) / 2f
        return extruded(listOf(Vector2f(-half, height), Vector2f(-half + deck, height), Vector2f(half, 0f)), width)
    }

    /** Small curved launch ramp with no deck. */
    fun kicker(height: Float = 0.5f, radius: Float = 1.2f, width: Float = 1.2f): ObstacleShape {
        val curve = transition(height, radius)
        val half = curve.last().x / 2f
        return extruded(curve.reversed().map { Vector2f(half - it.x, it.y) }, width)
    }

    /** Platform with a bank on each end. */
    fun funbox(height: Float = 0.5f, top: Float = 1.6f, bankRun: Float = 1.2f, width: Float = 2.4f): ObstacleShape {
        val half = top / 2f + bankRun
        return extruded(
            listOf(Vector2f(-half, 0f), Vector2f(-top / 2f, height), Vector2f(top / 2f, height), Vector2f(half, 0f)),
            width,
        )
    }

    /** Long low box, ridden along its length (Z). */
    fun grindBox(height: Float = 0.4f, length: Float = 2.4f, width: Float = 0.6f): ObstacleShape =
        extruded(listOf(Vector2f(-length / 2f, height), Vector2f(length / 2f, height)), width)

    /** Ledge of [height] above the step noses of a [steps]-stair: placed beside that stair set it follows its slope. */
    fun hubba(steps: Int = 5, height: Float = 0.45f, width: Float = 0.5f): ObstacleShape =
        extruded(noseLine(steps).map { Vector2f(it.x, it.y + height) }, width)

    /** [steps]-stair set going down toward +Z from a top landing. */
    fun stairs(
        steps: Int, riser: Float = RISER, tread: Float = TREAD, landing: Float = LANDING, width: Float = 2f,
    ): ObstacleShape {
        require(steps >= 1)
        val length = stairLength(steps, tread, landing)
        var s = -length / 2f + landing
        val profile = mutableListOf(Vector2f(-length / 2f, steps * riser), Vector2f(s, steps * riser))
        for (k in 1 until steps) {
            profile += Vector2f(s, (steps - k) * riser)
            s += tread
            profile += Vector2f(s, (steps - k) * riser)
        }
        return extruded(profile, width)
    }

    /**
     * Euro-gap deck (GPL-23): a bank of [bankRun] up to a flat deck of [deck] at [height], rising toward +Z. Its
     * +Z end is a vertical drop, so set flush against the back of a [steps]-stair's landing it leads onto the stairs
     * ([height] defaults to that landing, [euroGapOffset] gives the placement).
     */
    fun euroGap(steps: Int = 5, height: Float = steps * RISER, bankRun: Float = 2.0f, deck: Float = 1.5f, width: Float = 2f): ObstacleShape {
        val half = (bankRun + deck) / 2f
        return extruded(listOf(Vector2f(-half, 0f), Vector2f(-half + bankRun, height), Vector2f(half, height)), width)
    }

    /** Z offset from a [steps]-stair's centre to a default [euroGap] centre that ends at the stairs' landing. */
    fun euroGapOffset(steps: Int = 5): Float = -(stairLength(steps, TREAD, LANDING) / 2f + EURO_GAP_LENGTH / 2f)

    /** Length (Z) of the default [euroGap]. */
    const val EURO_GAP_LENGTH = 3.5f

    /** Rail [height] above the step noses of a [steps]-stair (flat over the landing, then down the steps). */
    fun handrail(steps: Int = 5, height: Float = 0.8f, radius: Float = 0.03f): ObstacleShape =
        rail(noseLine(steps).map { Vector3f(0f, it.y + height, it.x) }, radius)

    /**
     * (s, height) of the back of the landing, the landing edge and the last step nose of the default [stairs] set,
     * in the stairs' own frame.
     */
    fun noseLine(steps: Int, riser: Float = RISER, tread: Float = TREAD, landing: Float = LANDING): List<Vector2f> {
        val half = stairLength(steps, tread, landing) / 2f
        return listOf(Vector2f(-half, steps * riser), Vector2f(-half + landing, steps * riser), Vector2f(half, riser))
    }

    private fun stairLength(steps: Int, tread: Float, landing: Float) = landing + (steps - 1) * tread

    /** Flat-down-flat rail. */
    fun kinkedRail(radius: Float = 0.03f): ObstacleShape = rail(
        listOf(Vector3f(0f, 0.9f, -1.6f), Vector3f(0f, 0.9f, -0.5f), Vector3f(0f, 0.5f, 0.5f), Vector3f(0f, 0.5f, 1.6f)),
        radius,
    )

    /**
     * Round bowl: a flat bottom of [floorRadius], a transition wall up to [height] and a deck ring of [deck].
     * The bottom is open (the park floor shows through).
     */
    fun bowl(
        floorRadius: Float = 1.5f,
        height: Float = 1.5f,
        radius: Float = 1.8f,
        deck: Float = 0.6f,
        sectors: Int = 32,
        collisionSectors: Int = 16,
    ): ObstacleShape {
        val wall = transition(height, radius).map { Vector2f(floorRadius + it.x, it.y) }
        val outer = wall.last().x + deck
        val profile = wall + Vector2f(outer, height) + Vector2f(outer, 0f)
        // One hull per sector and wall step: a coarser wall keeps the collider count down (~2 cm chord error).
        val collisionWall = transition(height, radius, steps = TRANSITION_STEPS / 2).map { Vector2f(floorRadius + it.x, it.y) }
        return ObstacleShape(
            MeshBuilder().revolve(profile, sectors),
            ConvexPieces.revolvedBands(collisionWall, outer, collisionSectors),
        )
    }

    /** (horizontal distance from the toe, height) points of a circular transition, from the toe up to [height]. */
    fun transition(height: Float, radius: Float, steps: Int = TRANSITION_STEPS): List<Vector2f> {
        require(height in 0f..radius) { "transition height must be within its radius" }
        val maxAngle = acos(1f - height / radius)
        return (0..steps).map { i ->
            val angle = maxAngle * i / steps
            Vector2f(radius * sin(angle), radius - radius * cos(angle))
        }
    }

    private fun extruded(profile: List<Vector2f>, width: Float) = ObstacleShape(
        MeshBuilder().extrudeProfile(profile, -width / 2f, width / 2f),
        ConvexPieces.profileColumns(profile, -width / 2f, width / 2f),
    )

    private fun rail(points: List<Vector3f>, radius: Float): ObstacleShape {
        val mesh = MeshBuilder()
        points.zipWithNext().forEach { (a, b) -> mesh.tube(a, b, radius) }
        // Posts under the ends and every bend, a little thinner than the bar. They collide too, so nothing passes
        // through them; the board only grinds the bar on top.
        val posts = points.map { p -> Vector3f(p.x, 0f, p.z) to Vector3f(p.x, p.y - radius, p.z) }
        posts.forEach { (foot, top) -> mesh.tube(foot, top, radius * 0.8f) }
        val hulls = points.zipWithNext().map { (a, b) -> ConvexPieces.segment(a, b, radius) } +
            posts.map { (foot, top) -> ConvexPieces.segment(foot, top, radius * 0.8f) }
        return ObstacleShape(mesh, hulls)
    }
}
