package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneSun
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.lighting.EnvironmentProbe
import com.pafoid.skate.engine.render.lighting.LightBuffer
import com.pafoid.skate.engine.render.lighting.LightCulling
import com.pafoid.skate.engine.render.lighting.LightData
import com.pafoid.skate.engine.render.lighting.ModelBounds
import com.pafoid.skate.engine.render.lighting.SceneLights
import com.pafoid.skate.engine.render.material.FramePrograms
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.shadow.ShadowFrame
import com.pafoid.skate.engine.render.state.DrawQueue
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector3f

/**
 * Lit-scene state shared by every pass that shades meshes in the scene colour: `GeometryPass` (opaque), `TransparentPass`
 * (blended) and `WaterPass` (REN-29 task 1). The geometry pass [begin]s the frame: it collects and uploads the lights,
 * updates the environment probe and consumes this frame's shadow maps once; later passes only [uploadFrame] the result.
 * It also carries the blended draws the geometry pass hands to the transparent pass, in scene order.
 */
class SceneFrame(
    private val lightingUniformsLoader: LightingUniformsLoader,
    private val shadowFrame: ShadowFrame? = null,
    val environmentProbe: EnvironmentProbe? = null,
) {
    /** One mesh draw of the colour passes. */
    data class DrawItem(val go: GameObject, val renderComponent: RenderComponent, val transform: Transform, val program: Shader)

    var camera: CameraComponent? = null
        private set
    val cameraPosition = Vector3f()
    var sun: DirectionalLightComponent? = null
        private set
    var environment: EnvironmentComponent? = null
        private set
    var shadows: ShadowFrame? = null
        private set

    /** True between [begin] and the next frame: later passes can draw lit meshes. */
    var began = false
        private set

    /** Blended draws collected by the geometry pass, drawn in submission (scene) order by the transparent pass. */
    val transparent = DrawQueue<DrawItem>()

    private val lightBuffer = LightBuffer()
    private val sceneLights = ArrayList<LightData>()
    private val objectLights = IntArray(LightCulling.MAX_LIGHTS_PER_OBJECT)
    private val lightScores = FloatArray(LightCulling.MAX_LIGHTS_PER_OBJECT)
    private val boundsCenter = Vector3f()
    private val programs = FramePrograms<Shader>()

    /**
     * Starts the frame: environment probe (it may bake into its own target, so call this before any render pass begins),
     * camera, sun, lights and this frame's shadows.
     */
    fun begin(scene: Scene, camera: CameraComponent, cameraPosition: Vector3f, environment: EnvironmentComponent?) {
        environmentProbe?.update(environment, scene.getComponent<DayNightCycleComponent>())
        this.camera = camera
        this.cameraPosition.set(cameraPosition)
        this.sun = SceneSun.of(scene)
        this.environment = environment
        this.shadows = GeometryPass.shadowsThisFrame(shadowFrame) // consumed once per frame
        SceneLights.collect(scene, sceneLights)
        lightBuffer.upload(sceneLights)
        programs.beginFrame()
        transparent.clear()
        began = true
    }

    /** Id of a material's custom program reference, for sort keys (0: the default program). */
    fun programId(reference: String?): Int = programs.idOf(reference)

    /** Uploads the frame uniforms to [program] (bound) the first time it is used this frame. */
    fun prepare(program: Shader) {
        if (programs.firstUse(program)) uploadFrame(program)
    }

    /** Uploads the frame's camera, lighting, shadow and IBL uniforms to [program] (bound). */
    fun uploadFrame(program: Shader) {
        val camera = camera ?: return
        program.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.projection)
        program.uploadMat4f(Uniforms.VIEW_MATRIX, camera.view)
        lightingUniformsLoader.loadCameraPosition(program, cameraPosition)
        lightBuffer.bind(program)
        lightingUniformsLoader.loadLightingUniforms(program, sun, environment, shadows)
        environmentProbe?.bind(program) ?: program.uploadInt(Uniforms.ENV_ENABLED, 0)
    }

    /** Picks the local lights shading a draw of [model] at [transform] and uploads them to [program]. */
    fun selectObjectLights(program: Shader, model: `3dModel`?, transform: Transform) {
        val count = if (model == null || sceneLights.isEmpty()) 0 else {
            val radius = ModelBounds.of(model).toWorld(transform.worldMatrix, boundsCenter)
            LightCulling.select(sceneLights, boundsCenter, radius, objectLights, lightScores)
        }
        lightBuffer.setObjectLights(program, objectLights, count)
    }

    /** Selects lights for a draw centred on [center] with [radius] (water surfaces, no model). */
    fun selectObjectLights(program: Shader, center: Vector3f, radius: Float) {
        val count = if (sceneLights.isEmpty()) 0 else LightCulling.select(sceneLights, center, radius, objectLights, lightScores)
        lightBuffer.setObjectLights(program, objectLights, count)
    }

    fun destroy() {
        transparent.clear()
        lightBuffer.destroy()
        environmentProbe?.destroy()
    }
}
