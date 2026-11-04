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
import com.pafoid.skate.engine.render.shadow.ShadowSignature
import com.pafoid.skate.engine.render.utils.GLStateTracker
import com.pafoid.skate.engine.render.utils.bindTexture
import com.pafoid.skate.engine.render.utils.bindVAO
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import com.pafoid.skate.engine.utils.TextureSlots
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.GL_POLYGON_OFFSET_FILL
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
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
    private val alphaTestShader: Shader = shadowShader,
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
        GLStateTracker.setDepthTestEnabled(true)
        GLStateTracker.setDepthMask(true)
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

    /** Content signature of cascade [fit]: its matrix plus every caster [drawCascade] would draw. */
    fun cascadeSignature(fit: CascadeFit): Long {
        var h = ShadowSignature.mix(ShadowSignature.SEED, fit.matrix)
        for (c in casters) if (ShadowCulling.intersectsCascade(fit, c.center, c.radius)) h = casterSignature(h, c)
        return h
    }

    /** Content signature of a local light layer: its matrix plus every caster [drawLocal] would draw. */
    fun localSignature(lightPosition: Vector3f, range: Float, matrix: Matrix4f): Long {
        var h = ShadowSignature.mix(ShadowSignature.SEED, matrix)
        for (c in casters) if (ShadowCulling.intersectsLight(lightPosition, range, c.center, c.radius)) h = casterSignature(h, c)
        return h
    }

    private fun casterSignature(seed: Long, c: ShadowCaster): Long {
        var h = ShadowSignature.mix(seed, System.identityHashCode(c.renderComponent))
        val model = c.renderComponent.model
        h = ShadowSignature.mix(h, System.identityHashCode(model))
        h = ShadowSignature.mix(h, c.transform.worldMatrix)
        val skeleton = c.skeleton
        if (skeleton != null) {
            h = ShadowSignature.mix(h, System.identityHashCode(skeleton))
            h = ShadowSignature.mix(h, skeleton.paletteVersion)
        }
        if (model != null) for (part in model.mesh) {
            val m = part.material
            h = ShadowSignature.mix(h, m.alphaMode.ordinal)
            if (m.alphaMode != AlphaMode.MASK) continue
            h = ShadowSignature.mix(h, m.alphaCutoff)
            h = ShadowSignature.mix(h, m.baseColorTexture?.texId ?: 0)
        }
        return h
    }

    /** Draws casters overlapping cascade [fit]; returns how many were drawn. */
    fun drawCascade(fit: CascadeFit): Int {
        selected.clear()
        for (c in casters) if (ShadowCulling.intersectsCascade(fit, c.center, c.radius)) selected.add(c)
        drawSelected(fit.matrix)
        return selected.size
    }

    /** Draws casters inside the light's sphere of influence with [matrix]; returns how many were drawn. */
    fun drawLocal(lightPosition: Vector3f, range: Float, matrix: Matrix4f): Int {
        selected.clear()
        for (c in casters) if (ShadowCulling.intersectsLight(lightPosition, range, c.center, c.radius)) selected.add(c)
        drawSelected(matrix)
        return selected.size
    }

    private val selected = ArrayList<ShadowCaster>()

    /**
     * Opaque and blended parts first with the discard-free shader (fast depth-only path), then alpha-masked parts with
     * the ALPHA_TEST variant, so the program switches at most twice per layer.
     */
    private fun drawSelected(lightMatrix: Matrix4f) {
        shadowShader.uploadMat4f(Uniforms.LIGHT_SPACE_MATRIX, lightMatrix)
        var anyMasked = false
        for (c in selected) anyMasked = draw(shadowShader, c, masked = false) or anyMasked
        if (!anyMasked) return
        alphaTestShader.start()
        alphaTestShader.uploadMat4f(Uniforms.LIGHT_SPACE_MATRIX, lightMatrix)
        for (c in selected) draw(alphaTestShader, c, masked = true)
        shadowShader.start()
    }

    /** Draws [caster]'s parts whose alpha mode matches [masked]; returns true if it has alpha-masked parts. */
    private fun draw(shader: Shader, caster: ShadowCaster, masked: Boolean): Boolean {
        val model = caster.renderComponent.model ?: return false
        val skeleton = caster.skeleton
        var hasMasked = false
        var prepared = false
        for (part in model.mesh) {
            if (part.vaoId <= 0) continue
            val material = part.material
            val isMasked = material.alphaMode == AlphaMode.MASK && material.baseColorTexture != null
            hasMasked = hasMasked || isMasked
            if (isMasked != masked) continue
            if (!prepared) {
                shader.uploadMat4f(Uniforms.MODEL_MATRIX, caster.transform.worldMatrix)
                if (skeleton != null) SkinningUbo.bind(shader, skeleton)
                shader.uploadBoolean(Uniforms.HAS_SKIN, skeleton != null)
                prepared = true
            }
            part.vaoId.bindVAO(part.enabledAttributes)
            if (isMasked) {
                shader.uploadInt(Uniforms.ALPHA_MODE, 1)
                shader.uploadFloat(Uniforms.ALPHA_CUTOFF, material.alphaCutoff)
                shader.uploadBoolean(Uniforms.HAS_BASE_COLOR_TEXTURE, true)
                material.baseColorTexture?.texId?.let { bindTexture(TextureSlots.BASE_COLOR, it) }
                shader.uploadInt(Uniforms.BASE_COLOR_TEXTURE, TextureSlots.BASE_COLOR)
            }
            glDrawElements(GL_TRIANGLES, part.vertexCount, GL_UNSIGNED_INT, 0)
            EngineStats.drawCalls.incrementAndGet()
        }
        glBindVertexArray(0)
        return hasMasked
    }

    fun destroy() {
        shadowShader.destroy()
        // alphaTestShader is a variant owned by the AssetsManager's variant cache
    }

    companion object {
        /** Slope-scaled caster offset: pushes steep caster depths back, the main fix for acne on sloped receivers. */
        const val POLYGON_OFFSET_FACTOR = 1.5f
        const val POLYGON_OFFSET_UNITS = 2f

        /** Bind-pose bounds understate animated meshes; inflate so limbs do not pop out of the map. */
        const val SKINNED_BOUNDS_SCALE = 1.5f
    }
}
