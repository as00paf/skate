package com.pafoid.skate.engine.render.cache

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.Texture
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL30.glDeleteVertexArrays

/** GPU handle of an uploaded mesh part: one VAO and the VBOs it owns. */
class GpuMesh(val vaoId: Int, val vboIds: IntArray)

/**
 * Owns one [GpuResourceCache] per GPU resource kind. Key by a stable asset identity
 * (e.g. absolute path or asset GUID), so loading the same asset twice yields one GPU resource.
 * [disposeAll] frees everything and is called when the renderer/scene is torn down.
 */
class GpuResources(
    destroyShader: (Shader) -> Unit = { it.destroy() },
    destroyTexture: (Texture) -> Unit = { glDeleteTextures(it.texId) },
    destroyMesh: (GpuMesh) -> Unit = { mesh ->
        glDeleteVertexArrays(mesh.vaoId)
        mesh.vboIds.forEach { glDeleteBuffers(it) }
    },
    maxIdle: Int = GpuResourceCache.DEFAULT_MAX_IDLE,
) {
    val shaders = GpuResourceCache<String, Shader>("shaders", destroyShader, maxIdle)
    val textures = GpuResourceCache<String, Texture>("textures", destroyTexture, maxIdle)
    val meshes = GpuResourceCache<String, GpuMesh>("meshes", destroyMesh, maxIdle)

    val totalCount: Int get() = shaders.size + textures.size + meshes.size

    fun disposeAll() {
        var failure: Throwable? = null
        for (action in listOf(meshes::disposeAll, textures::disposeAll, shaders::disposeAll)) {
            try {
                action()
            } catch (t: Throwable) {
                if (failure == null) failure = t else failure.addSuppressed(t)
            }
        }
        failure?.let { throw it }
    }
}
