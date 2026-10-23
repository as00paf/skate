package com.pafoid.skate.engine.render.state

import com.pafoid.skate.engine.render.EngineStats
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL30.glBindVertexArray

/** Global VAO/texture binding cache for the GL context. */
object GLBindings {
    val cache = BindingCache(
        glBindVao = { glBindVertexArray(it) },
        glActiveTexture = { glActiveTexture(GL_TEXTURE0 + it) },
        glBindTexture2D = { glBindTexture(GL_TEXTURE_2D, it) },
        onStateChange = { EngineStats.stateChanges.incrementAndGet() },
    )
}
