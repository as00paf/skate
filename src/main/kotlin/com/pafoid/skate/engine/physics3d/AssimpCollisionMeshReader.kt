package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.assets.loaders.toJomlMatrix
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AINode
import org.lwjgl.assimp.AIScene
import org.lwjgl.assimp.Assimp.aiGetErrorString
import org.lwjgl.assimp.Assimp.aiImportFile
import org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices
import org.lwjgl.assimp.Assimp.aiProcess_Triangulate
import org.lwjgl.assimp.Assimp.aiReleaseImport

/**
 * Reads a model file's triangles for collision, on the CPU only (no GL, any thread). Node transforms are baked into the
 * positions exactly like the render path (`AssimpLoader.processNode`), so the collider lines up with the rendered mesh.
 * Normals, UVs, materials and bones are not imported.
 */
object AssimpCollisionMeshReader {
    private const val FLAGS = aiProcess_Triangulate or aiProcess_JoinIdenticalVertices

    fun read(file: String): ColliderShape.TriangleMesh {
        val scene = aiImportFile(file, FLAGS) ?: error("cannot read model $file: ${aiGetErrorString()}")
        try {
            val vertices = ArrayList<Vector3f>()
            val indices = ArrayList<Int>()
            val root = scene.mRootNode() ?: error("model $file has no root node")
            collect(root, scene, Matrix4f(), vertices, indices)
            return ColliderShape.TriangleMesh(vertices, indices)
        } finally {
            aiReleaseImport(scene)
        }
    }

    private fun collect(node: AINode, scene: AIScene, parent: Matrix4f, vertices: MutableList<Vector3f>, indices: MutableList<Int>) {
        val transform = parent.mul(node.mTransformation().toJomlMatrix(), Matrix4f())
        val meshes = scene.mMeshes()
        val nodeMeshes = node.mMeshes()
        if (meshes != null && nodeMeshes != null) {
            for (i in 0 until node.mNumMeshes()) addMesh(AIMesh.create(meshes.get(nodeMeshes.get(i))), transform, vertices, indices)
        }
        val children = node.mChildren() ?: return
        for (i in 0 until node.mNumChildren()) collect(AINode.create(children.get(i)), scene, transform, vertices, indices)
    }

    private fun addMesh(mesh: AIMesh, transform: Matrix4f, vertices: MutableList<Vector3f>, indices: MutableList<Int>) {
        val base = vertices.size
        val positions = mesh.mVertices()
        for (v in 0 until mesh.mNumVertices()) {
            val p = positions.get(v)
            vertices += transform.transformPosition(Vector3f(p.x(), p.y(), p.z()))
        }
        val faces = mesh.mFaces()
        for (f in 0 until mesh.mNumFaces()) {
            val face = faces.get(f)
            if (face.mNumIndices() != 3) continue // points and lines left after triangulation
            val ids = face.mIndices()
            for (k in 0 until 3) indices += base + ids.get(k)
        }
    }
}
