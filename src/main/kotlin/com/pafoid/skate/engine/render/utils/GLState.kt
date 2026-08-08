package com.pafoid.skate.engine.render.utils

import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.glBindTexture
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
 */
fun bindTexture(
    slot: Int,
    textureId: Int
) {
    GL13.glActiveTexture(GL13.GL_TEXTURE0 + slot)
    glBindTexture(GL_TEXTURE_2D, textureId)
}

/**
 * Extension function for scoping depth function changes.
 * Temporarily changes the depth comparison function and restores it after the block executes.
 * 
 * @param func The depth function to use during the block (e.g., GL_LESS, GL_LEQUAL)
 * @param block The code block to execute with the modified depth function
 */
inline fun withDepthFunc(func: Int, block: () -> Unit) {
    val previousDepthFunc = GLStateTracker.getDepthFunc()
    try {
        GLStateTracker.setDepthFunc(func)
        block()
    } finally {
        GLStateTracker.setDepthFunc(previousDepthFunc)
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
    val previousBlendEnabled = GLStateTracker.isBlendEnabled()
    try {
        GLStateTracker.setBlendEnabled(enabled)
        block()
    } finally {
        GLStateTracker.setBlendEnabled(previousBlendEnabled)
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
    val previousMask = GLStateTracker.isDepthMaskEnabled()
    try {
        GLStateTracker.setDepthMask(mask)
        block()
    } finally {
        GLStateTracker.setDepthMask(previousMask)
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
    val previousCullEnabled = GLStateTracker.isCullFaceEnabled()
    try {
        GLStateTracker.setCullFaceEnabled(enabled)
        block()
    } finally {
        GLStateTracker.setCullFaceEnabled(previousCullEnabled)
    }
}
