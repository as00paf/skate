package com.pafoid.skate.engine.assets.procedural

import org.joml.Vector3f

/** Generic, game-agnostic generated props. Sizes in metres, footprint centred on the origin, bottom at y = 0. */
object PropShapes {

    private val benchParts = listOf(
        Vector3f(-0.9f, 0.40f, -0.2f) to Vector3f(0.9f, 0.45f, 0.2f), // seat
        Vector3f(-0.8f, 0f, -0.15f) to Vector3f(-0.7f, 0.40f, 0.15f), // legs
        Vector3f(0.7f, 0f, -0.15f) to Vector3f(0.8f, 0.40f, 0.15f),
    )

    /** Park bench: a seat on two legs. */
    fun bench(): MeshBuilder = MeshBuilder().also { m -> benchParts.forEach { (min, max) -> m.box(min, max) } }

    /** Collision pieces of [bench]. */
    fun benchHulls(): List<List<Vector3f>> = benchParts.map { (min, max) -> ConvexPieces.box(min, max) }

    /** Editor-picker marker for spawn points: a flat pad with an arrow pointing along +Z (the spawn heading). */
    fun spawnMarker(): MeshBuilder = MeshBuilder()
        .box(Vector3f(-0.4f, 0f, -0.4f), Vector3f(0.4f, 0.03f, 0.4f))
        .box(Vector3f(-0.06f, 0.03f, -0.3f), Vector3f(0.06f, 0.08f, 0.2f))
        .also { m ->
            m.triangle(Vector3f(-0.2f, 0.08f, 0.2f), Vector3f(0.2f, 0.08f, 0.2f), Vector3f(0f, 0.08f, 0.4f), MeshBuilder.UP)
        }
}
