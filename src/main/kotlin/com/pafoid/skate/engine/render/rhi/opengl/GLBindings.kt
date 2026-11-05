package com.pafoid.skate.engine.render.rhi.opengl

import com.pafoid.skate.engine.render.EngineStats
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.GL_TEXTURE_CUBE_MAP
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D_ARRAY
import org.lwjgl.opengl.GL30.glBindVertexArray

/** VAO/texture binding cache of the GL context. */
internal object GLBindings {
    /** [BindingCache] kinds, indexed like [targets]. */
    const val KIND_2D = 0
    const val KIND_2D_ARRAY = 1
    const val KIND_CUBE = 2
    private val targets = intArrayOf(GL_TEXTURE_2D, GL_TEXTURE_2D_ARRAY, GL_TEXTURE_CUBE_MAP)

    val cache = BindingCache(
        glBindVao = { glBindVertexArray(it) },
        glActiveTexture = { glActiveTexture(GL_TEXTURE0 + it) },
        glBindTexture = { kind, id -> glBindTexture(targets[kind], id) },
        onStateChange = { EngineStats.stateChanges.incrementAndGet() },
    )
}
