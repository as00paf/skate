package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.assets.AssetRegistry
import java.io.File
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Loads the triangles of the collision model named by a `MeshCollider3D.meshRef`. Returns null, or throws with a
 * reason, when it cannot.
 */
fun interface CollisionMeshSource {
    fun load(reference: String): ColliderShape.TriangleMesh?

    companion object {
        /** No source configured: every reference is unavailable. */
        val NONE = CollisionMeshSource { error("no collision mesh source is configured") }
    }
}

/**
 * Model files as collision meshes (AST-17 / PHY-24). [resolve] turns a reference (an asset GUID; a project-relative
 * path also works) into a path, [localPath] turns that into a file on disk (an exported game extracts it from the
 * pack), and [read] imports its triangles.
 */
class ModelCollisionMeshSource(
    private val resolve: (String) -> String?,
    private val localPath: (String) -> String = { it },
    private val read: (String) -> ColliderShape.TriangleMesh = AssimpCollisionMeshReader::read,
) : CollisionMeshSource {
    override fun load(reference: String): ColliderShape.TriangleMesh {
        val path = resolve(reference) ?: error("unknown asset reference")
        return read(localPath(path))
    }

    companion object {
        /** Resolves GUIDs (and paths) through a project's [registry]; files are read from the project folder. */
        fun forRegistry(registry: AssetRegistry) = ModelCollisionMeshSource(
            resolve = registry::resolve,
            localPath = { File(registry.root, it).path },
        )
    }
}

/** A `meshRef` that could not be turned into a collider, and why. */
data class CollisionMeshFailure(val reference: String, val reason: String)

/**
 * Process-wide cache of the collision meshes that `MeshCollider3D.meshRef` points to. Each model is read once, and its
 * triangles are shared by every collider, body and backend that use it. Failures are cached as well and reported once
 * per reference through [drainFailures] (published by `PhysicsSystem` as `CollisionMeshUnavailable`).
 *
 * The host sets [source]: the editor from the open project's `AssetRegistry`, an exported game from its pack.
 */
object CollisionMeshes {
    private val cache = ConcurrentHashMap<String, Optional<ColliderShape.TriangleMesh>>()
    private val failures = ConcurrentLinkedQueue<CollisionMeshFailure>()

    /** Where triangles come from. Setting it drops everything cached. */
    @Volatile
    var source: CollisionMeshSource = CollisionMeshSource.NONE
        set(value) {
            field = value
            clear()
        }

    /** The triangles for [reference], or null when it cannot be loaded (reported once, see [drainFailures]). */
    fun get(reference: String): ColliderShape.TriangleMesh? =
        cache.computeIfAbsent(reference) { Optional.ofNullable(load(it)) }.orElse(null)

    private fun load(reference: String): ColliderShape.TriangleMesh? {
        val mesh = try {
            source.load(reference) ?: return fail(reference, "not found")
        } catch (e: Exception) {
            return fail(reference, e.message ?: e::class.simpleName.orEmpty())
        }
        val complete = mesh.indices.size - mesh.indices.size % 3
        if (complete == 0 || mesh.indices.any { it !in mesh.vertices.indices }) return fail(reference, "the model has no valid triangles")
        return if (complete == mesh.indices.size) mesh else ColliderShape.TriangleMesh(mesh.vertices, mesh.indices.subList(0, complete))
    }

    private fun fail(reference: String, reason: String): ColliderShape.TriangleMesh? {
        failures += CollisionMeshFailure(reference, reason)
        return null
    }

    /** Failures not reported yet, oldest first; each reference fails at most once until [invalidate] or [clear]. */
    fun drainFailures(): List<CollisionMeshFailure> = generateSequence { failures.poll() }.toList()

    /** Forgets [reference] so the next use reads it again (e.g. after the model file changed). */
    fun invalidate(reference: String) {
        cache.remove(reference)
    }

    fun clear() {
        cache.clear()
        failures.clear()
    }
}
