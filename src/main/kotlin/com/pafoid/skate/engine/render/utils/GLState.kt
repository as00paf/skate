package com.pafoid.skate.engine.render.utils

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Texture
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30

/**
 * Extension function for consistent VAO binding with attribute enabling.
 */
fun Int.bindVAO(attributes: List<Int>) {
    GL30.glBindVertexArray(this)
    attributes.forEach { GL20.glEnableVertexAttribArray(it) }
}

/**
 * Extension function for consistent VAO unbinding with attribute disabling.
 */
fun Int.unbindVAO(attributes: List<Int>) {
    attributes.forEach { GL20.glDisableVertexAttribArray(it) }
    GL30.glBindVertexArray(0)
}

/**
 * Extension function for binding a texture to a specified slot with fallback.
 * 
 * @param slot The texture unit slot (0, 1, 2, etc.)
 * @param texture The texture to bind, or null to use the default texture
 * @param resourceManager The resource manager for loading the default texture
 */
fun bindTexture(
    slot: Int,
    texture: Texture?,
    resourceManager: ResourceManager
) {
    GL13.glActiveTexture(GL13.GL_TEXTURE0 + slot)
    (texture ?: resourceManager.loadTextureSync(Assets.Textures.DEFAULT)).bind()
}

/**
 * Extension function for scoping depth function changes.
 * Temporarily changes the depth comparison function and restores it after the block executes.
 * 
 * @param func The depth function to use during the block (e.g., GL_LESS, GL_LEQUAL)
 * @param block The code block to execute with the modified depth function
 */
inline fun withDepthFunc(func: Int, block: () -> Unit) {
    val previousDepthFunc = GL11.glGetInteger(GL30.GL_DEPTH_FUNC)
    try {
        GL30.glDepthFunc(func)
        block()
    } finally {
        GL30.glDepthFunc(previousDepthFunc)
    }
}

/**
 * Extension function for scoping blend state changes.
 * Temporarily enables or disables blending and restores the previous state after the block executes.
 * 
 * @param enabled Whether blending should be enabled during the block
 * @param block The code block to execute with the modified blend state
 */
inline fun withBlendState(enabled: Boolean, block: () -> Unit) {
    val previousBlendEnabled = GL11.glIsEnabled(GL11.GL_BLEND)
    try {
        if (enabled) {
            GL11.glEnable(GL11.GL_BLEND)
        } else {
            GL11.glDisable(GL11.GL_BLEND)
        }
        block()
    } finally {
        if (previousBlendEnabled) {
            GL11.glEnable(GL11.GL_BLEND)
        } else {
            GL11.glDisable(GL11.GL_BLEND)
        }
    }
}

/**
 * Extension function for scoping depth mask changes.
 * Temporarily changes the depth write mask and restores it after the block executes.
 * 
 * @param mask The depth mask value (true to enable depth writes, false to disable)
 * @param block The code block to execute with the modified depth mask
 */
inline fun withDepthMask(mask: Boolean, block: () -> Unit) {
    val previousMask = GL11.glGetBoolean(GL30.GL_DEPTH_WRITEMASK)
    try {
        GL30.glDepthMask(mask)
        block()
    } finally {
        GL30.glDepthMask(previousMask)
    }
}

/**
 * Extension function for scoping face culling changes.
 * Temporarily enables or disables face culling and restores the previous state after the block executes.
 * 
 * @param enabled Whether face culling should be enabled during the block
 * @param block The code block to execute with the modified cull state
 */
inline fun withCullFace(enabled: Boolean, block: () -> Unit) {
    val previousCullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)
    try {
        if (enabled) {
            GL11.glEnable(GL11.GL_CULL_FACE)
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE)
        }
        block()
    } finally {
        if (previousCullEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE)
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE)
        }
    }
}
