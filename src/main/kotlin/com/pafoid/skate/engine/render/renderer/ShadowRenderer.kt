package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.lighting.ModelBounds
import com.pafoid.skate.engine.render.shadow.CascadeFit
import com.pafoid.skate.engine.render.shadow.ShadowCulling
import com.pafoid.skate.engine.render.utils.GLStateTracker
import com.pafoid.skate.engine.render.utils.bindTexture
import com.pafoid.skate.engine.render.utils.bindVAO
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import com.pafoid.skate.engine.utils.TextureSlots
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL11.glDepthMask
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glPolygonOffset
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL32.GL_DEPTH_CLAMP

/** A shadow-casting object with its world bounding sphere, gathered once per frame. */
class ShadowCaster {
    lateinit var renderComponent: RenderComponent
    lateinit var transform: Transform
    var skeleton: SkeletonComponent? = null
    val center = Vector3f()
    var radius = 0f
}

/**
 * Draws shadow casters into the currently bound shadow-map layer with the depth-only shadow shader.
 *
 * Per frame: [collect] the casters once, then [begin], one draw call per layer ([drawCascade] / [drawLocal], each
 * culling casters by bounding sphere), then [end]. Back-face culling is off so open meshes (planes, decks) still cast;
 * acne is handled by slope-scaled polygon offset here plus the receiver normal offset in the main shader.
 */
class ShadowRenderer(
    private val shadowShader: Shader,
) {
    private val pool = ArrayList<ShadowCaster>()
    private val casters = ArrayList<ShadowCaster>()
    private var previousCull = false

    /** Gathers visible, enabled objects with `castShadow` and computes their bounds. */
    fun collect(gameObjects: List<GameObject>): List<ShadowCaster> {
        casters.clear()
        for (go in gameObjects) {
            if (!go.isVisible) continue
            val rc = go.getComponent<RenderComponent>()?.takeIf { it.enabled && it.castShadow } ?: continue
            val model = rc.model ?: continue
            val transform = go.getComponent<Transform>() ?: continue
            val caster = if (casters.size < pool.size) pool[casters.size] else ShadowCaster().also { pool.add(it) }
            caster.renderComponent = rc
            caster.transform = transform
            caster.skeleton = go.getComponent<SkeletonComponent>()
            caster.radius = ModelBounds.of(model).toWorld(transform.worldMatrix, caster.center) *
                (if (caster.skeleton != null) SKINNED_BOUNDS_SCALE else 1f)
            casters.add(caster)
        }
        return casters
    }

    fun begin() {
        shadowShader.start()
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        previousCull = GLStateTracker.isCullFaceEnabled()
        GLStateTracker.setCullFaceEnabled(false)
        glEnable(GL_POLYGON_OFFSET_FILL)
        glPolygonOffset(POLYGON_OFFSET_FACTOR, POLYGON_OFFSET_UNITS)
        // Casters between the sun and the cascade's near plane are clamped onto it instead of clipped away.
        glEnable(GL_DEPTH_CLAMP)
    }

    fun end() {
        glDisable(GL_DEPTH_CLAMP)
        glPolygonOffset(0f, 0f)
        glDisable(GL_POLYGON_OFFSET_FILL)
        GLStateTracker.setCullFaceEnabled(previousCull)
        shadowShader.stop()
    }

    /** Draws casters overlapping cascade [fit]; returns how many were drawn. */
    fun drawCascade(fit: CascadeFit): Int {
        shadowShader.uploadMat4f(Uniforms.LIGHT_SPACE_MATRIX, fit.matrix)
        var drawn = 0
        for (c in casters) if (ShadowCulling.intersectsCascade(fit, c.center, c.radius)) { draw(c); drawn++ }
        return drawn
    }

    /** Draws casters inside the light's sphere of influence with [matrix]; returns how many were drawn. */
    fun drawLocal(lightPosition: Vector3f, range: Float, matrix: Matrix4f): Int {
        shadowShader.uploadMat4f(Uniforms.LIGHT_SPACE_MATRIX, matrix)
        var drawn = 0
        for (c in casters) if (ShadowCulling.intersectsLight(lightPosition, range, c.center, c.radius)) { draw(c); drawn++ }
        return drawn
    }

    private fun draw(caster: ShadowCaster) {
        val model = caster.renderComponent.model ?: return
        val skeleton = caster.skeleton
        shadowShader.uploadMat4f(Uniforms.MODEL_MATRIX, caster.transform.worldMatrix)
        if (skeleton != null) SkinningUbo.bind(shadowShader, skeleton)
        shadowShader.uploadBoolean(Uniforms.HAS_SKIN, skeleton != null)

        for (part in model.mesh) {
            if (part.vaoId <= 0) continue
            val material = part.material
            part.vaoId.bindVAO(part.enabledAttributes)
            val alphaModeInt = when (material.alphaMode) {
                AlphaMode.OPAQUE -> 0
                AlphaMode.MASK -> 1
                AlphaMode.BLEND -> 2
            }
            shadowShader.uploadInt(Uniforms.ALPHA_MODE, alphaModeInt)
            shadowShader.uploadFloat(Uniforms.ALPHA_CUTOFF, material.alphaCutoff)
            shadowShader.uploadBoolean(Uniforms.HAS_BASE_COLOR_TEXTURE, material.baseColorTexture != null)
            if (material.alphaMode == AlphaMode.MASK && material.baseColorTexture != null) {
                material.baseColorTexture?.texId?.let { bindTexture(TextureSlots.BASE_COLOR, it) }
                shadowShader.uploadInt(Uniforms.BASE_COLOR_TEXTURE, TextureSlots.BASE_COLOR)
            }
            glDrawElements(GL_TRIANGLES, part.vertexCount, GL_UNSIGNED_INT, 0)
            EngineStats.drawCalls.incrementAndGet()
            glBindVertexArray(0)
        }
    }

    fun destroy() {
        shadowShader.destroy()
    }

    companion object {
        /** Slope-scaled caster offset: pushes steep caster depths back, the main fix for acne on sloped receivers. */
        const val POLYGON_OFFSET_FACTOR = 1.5f
        const val POLYGON_OFFSET_UNITS = 2f

        /** Bind-pose bounds understate animated meshes; inflate so limbs do not pop out of the map. */
        const val SKINNED_BOUNDS_SCALE = 1.5f
    }
}
