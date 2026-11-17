package com.pafoid.skate.engine.render.water

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.environment.WaterWaves
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.data.VertexLayouts
import com.pafoid.skate.engine.render.lod.ScreenErrorLod
import com.pafoid.skate.engine.render.material.ShaderUniformSink
import com.pafoid.skate.engine.render.renderer.RenderTraversal
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiVertexLayout
import com.pafoid.skate.engine.render.state.PipelineCache
import com.pafoid.skate.engine.render.state.PipelineStates
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.FrustumIntersection
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.IdentityHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Everything the water pass and the picking pass share to draw [WaterComponent]s (REN-29 §6): surface meshes cached
 * per component, water materials cached per reference, the wave uniforms, chunk culling and LOD.
 *
 * @param readText reads a project `.skmat` (null when missing: the bundled presets are used)
 */
class WaterRenderer(
    private val readText: (String) -> String? = { null },
    private val resolveTexture: (String) -> Texture? = { null },
    private val device: () -> RhiDevice = RhiContext::require,
) {
    /** One visible water body this frame. */
    class Draw(val go: GameObject, val water: WaterComponent, val transform: Transform)

    private class Cached(val mesh: WaterSurfaceMesh, var lastFrame: Long)

    init {
        WaterSchema.register()
    }

    private val meshes = IdentityHashMap<WaterComponent, Cached>()
    private val materials = HashMap<String, WaterMaterial>()
    private val sinks = IdentityHashMap<Shader, ShaderUniformSink>()
    private var layout: RhiVertexLayout? = null
    private val pipelines = PipelineCache(PipelineStates.WATER, device)
    private var frame = 0L

    val waves = WaterWaves()
    private val model = Matrix4f()
    private val frustum = FrustumIntersection()
    private val viewProjection = Matrix4f()
    private val center = Vector3f()
    private val vec2 = Vector2f()
    private val vec4 = Vector4f()
    private val lodErrors = FloatArray(WaterMeshBuilder.LOD_STEPS.size)

    /** Chunks drawn and culled in the last [drawSurface] calls of this frame (stats, tests). */
    var chunksDrawn = 0
        private set
    var chunksCulled = 0
        private set

    /** Enabled, rendered water bodies in scene order. */
    fun collect(root: GameObject, out: MutableList<Draw>) {
        out.clear()
        RenderTraversal.forEachRendered(root) { go ->
            val water = go.getComponent<WaterComponent>()?.takeIf { it.enabled } ?: return@forEachRendered
            val transform = go.getComponent<Transform>()?.takeIf { it.enabled } ?: return@forEachRendered
            out += Draw(go, water, transform)
        }
    }

    /** Starts a frame: chunk counters reset, meshes of water not drawn last frame are released by [endFrame]. */
    fun beginFrame(camera: CameraComponent) {
        frame++
        chunksDrawn = 0
        chunksCulled = 0
        frustum.set(viewProjection.set(camera.projection).mul(camera.view))
    }

    fun endFrame() {
        val it = meshes.values.iterator()
        while (it.hasNext()) {
            val cached = it.next()
            if (cached.lastFrame < frame - 1) { cached.mesh.close(); it.remove() }
        }
    }

    /** The water's surface mesh, rebuilt when its size or quad size changed. */
    fun surface(water: WaterComponent): WaterSurfaceMesh {
        val cached = meshes[water]
        if (cached != null && cached.mesh.matches(water.sizeX, water.sizeZ, water.quadSize)) {
            cached.lastFrame = frame
            return cached.mesh
        }
        cached?.mesh?.close()
        val d = device()
        val vertexLayout = layout ?: d.createVertexLayout(VertexLayouts.WATER).also { layout = it }
        val mesh = WaterSurfaceMesh(WaterMeshBuilder.build(water.sizeX, water.sizeZ, water.quadSize), d, vertexLayout)
        meshes[water] = Cached(mesh, frame)
        return mesh
    }

    fun material(reference: String): WaterMaterial =
        materials.getOrPut(reference) { WaterMaterial.resolve(reference, readText, resolveTexture) }

    /** Forgets cached materials, e.g. after a `.skmat` changed on disk. */
    fun invalidateMaterials() = materials.clear()

    /** Loads the water's waves (wind-driven ones follow [env]) into [waves]. */
    fun configureWaves(water: WaterComponent, env: EnvironmentComponent?): WaterWaves =
        waves.configure(water.waves, water.windDrivenWaves, env?.windSpeed ?: 0f, env?.windDirection ?: 0f)

    /** Uploads [waves] and the per-water surface uniforms to [shader] (bound). */
    fun uploadSurface(shader: Shader, water: WaterComponent, transform: Transform, time: Float) {
        modelMatrix(transform, model)
        shader.uploadMat4f(Uniforms.WATER_MODEL, model)
        shader.uploadFloat(Uniforms.WATER_TIME, time)
        shader.uploadFloat(Uniforms.WATER_SKIRT_DEPTH, SKIRT_BASE + 2f * waves.totalAmplitude)
        uploadWaves(shader, waves, vec4)
        shader.uploadFloat(Uniforms.WATER_DEPTH, water.depth)
        shader.uploadInt(Uniforms.WATER_RECEIVE_SHADOWS, if (water.receiveShadows) 1 else 0)
        // Flow is authored on the water's axes; the shader scrolls in world XZ
        val yaw = yawOf(transform.worldMatrix)
        val c = cos(yaw); val s = sin(yaw)
        val fx = water.flowVelocity.x; val fz = water.flowVelocity.y
        shader.uploadVec2f(Uniforms.WATER_FLOW, vec2.set(fx * c + fz * s, -fx * s + fz * c))
    }

    /** Uploads [material]'s parameters to [shader] (bound) and binds its textures. */
    fun uploadMaterial(shader: Shader, material: WaterMaterial) {
        material.upload(sinks.getOrPut(shader) { ShaderUniformSink(shader) })
        val cmd = device().commandList()
        for (t in WaterSchema.SCHEMA.textures) material.textures[t.name]?.let { cmd.setTexture(t.slot, it.rhi) }
    }

    /**
     * Draws the visible chunks of [water] with [shader] (bound, uniforms uploaded): frustum-culled, LOD from the
     * screen error at the chunk's distance to [cameraPosition]; [fixedLod] forces one level (picking draws LOD 2).
     */
    fun drawSurface(shader: Shader, water: WaterComponent, transform: Transform, cameraPosition: Vector3f, projectionScale: Float, fixedLod: Int = -1) {
        val mesh = surface(water)
        val vertexLayout = layout ?: return
        val pipeline = pipelines.get(shader, vertexLayout) ?: return
        modelMatrix(transform, model)
        val amplitude = waves.totalAmplitude
        WaterLod.errors(mesh.grid.quadSize, amplitude, lodErrors)
        val cmd = device().commandList()
        cmd.setPipeline(pipeline)
        for (c in mesh.chunks) {
            val chunk = c.chunk
            model.transformPosition(center.set(chunk.centerX, 0f, chunk.centerZ))
            val hx = (chunk.maxX - chunk.minX) * 0.5f; val hz = (chunk.maxZ - chunk.minZ) * 0.5f
            val radius = sqrt(hx * hx + hz * hz) + amplitude * 2f + SKIRT_BASE
            if (!frustum.testSphere(center.x, center.y, center.z, radius)) { chunksCulled++; continue }
            val lod = if (fixedLod >= 0) fixedLod.coerceAtMost(c.inputs.size - 1) else {
                val distance = (center.distance(cameraPosition) - radius).coerceAtLeast(0.01f)
                WaterLod.select(lodErrors, distance, projectionScale)
            }
            cmd.setVertexInput(c.inputs[lod])
            cmd.drawIndexed(c.indexCount(lod))
            chunksDrawn++
        }
    }

    fun projectionScale(camera: CameraComponent): Float =
        ScreenErrorLod.projectionScale(camera.viewportHeight.coerceAtLeast(1), camera.fov)

    fun destroy() {
        meshes.values.forEach { it.mesh.close() }
        meshes.clear()
        materials.clear()
        layout?.close()
        layout = null
        pipelines.close()
    }

    companion object {
        /** Skirts hang this far below the surface, plus twice the wave amplitude. */
        const val SKIRT_BASE = 0.25f

        /** Yaw (radians) of [world]'s +Z axis on the XZ plane; pitch and roll are ignored like terrain. */
        fun yawOf(world: Matrix4f): Float = atan2(world.m20(), world.m22())

        /** The water's surface frame: world translation of [transform] plus its yaw only. */
        fun modelMatrix(transform: Transform, out: Matrix4f): Matrix4f {
            val m = transform.worldMatrix
            return out.translation(m.m30(), m.m31(), m.m32()).rotateY(yawOf(m))
        }

        /** Uploads the `water_waves.glsl` uniforms of [waves] to [shader] (bound). */
        fun uploadWaves(shader: Shader, waves: WaterWaves, scratch: Vector4f = Vector4f()) {
            shader.uploadInt(Uniforms.WAVE_COUNT, waves.count)
            for (i in 0 until WaterWaves.MAX_WAVES) {
                if (i < waves.count) {
                    shader.uploadVec4f(Uniforms.WAVE_A[i], scratch.set(waves.dirX[i], waves.dirZ[i], waves.k[i], waves.amplitude[i]))
                    shader.uploadVec4f(Uniforms.WAVE_B[i], scratch.set(waves.q[i], waves.omega[i], 0f, 0f))
                } else {
                    shader.uploadVec4f(Uniforms.WAVE_A[i], scratch.zero())
                    shader.uploadVec4f(Uniforms.WAVE_B[i], scratch.zero())
                }
            }
        }
    }
}
