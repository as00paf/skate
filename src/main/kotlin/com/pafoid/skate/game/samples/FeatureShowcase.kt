package com.pafoid.skate.game.samples

import com.pafoid.skate.engine.ecs.SceneSun
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.audio.BusId
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.animation.graph.AnimatorMaskDef
import com.pafoid.skate.engine.animation.graph.MaskPreset
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.CameraFocusComponent
import com.pafoid.skate.engine.ecs.components.CameraFocusTarget
import com.pafoid.skate.engine.ecs.components.CapsuleCollider3D
import com.pafoid.skate.engine.ecs.components.Collider3D
import com.pafoid.skate.engine.ecs.components.EmissionShape
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.FreeFlyCameraComponent
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.components.JointComponent
import com.pafoid.skate.engine.ecs.components.JointType
import com.pafoid.skate.engine.ecs.components.MeshCollider3D
import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.VehicleComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.render.material.MaterialAsset
import com.pafoid.skate.engine.ui.UIButtonComponent
import com.pafoid.skate.engine.ui.MapBounds
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIMapComponent
import com.pafoid.skate.engine.ui.UIMapMarkerComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UIImageComponent
import com.pafoid.skate.engine.ui.UIProgressBarComponent
import com.pafoid.skate.engine.ui.UISliderComponent
import com.pafoid.skate.engine.ui.UITextComponent
import com.pafoid.skate.engine.net.LoopbackPredictionComponent
import com.pafoid.skate.game.net.SkaterPredictionModel
import com.pafoid.skate.game.player.SkateGameplay
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.Skater
import com.pafoid.skate.game.prefabs.PrefabCatalog
import com.pafoid.skate.game.prefabs.PrefabType
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.File

/**
 * The Feature Showcase scene (TLG-05/TLG-08): one labelled demo area per engine feature on a shared floor, plus an
 * in-game UI overlay with a legend. Every new editor project gets it next to `MainScene` (see
 * [PrefabsGenerator.createDefaultScenes]) and the CLI writes it as the `FeatureShowcase` sample. New features add an
 * area here (GameEngine.md rule 8); see docs/samples.md for what to check in each.
 *
 * Objects are top-level (physics and animation only drive top-level objects) and named `[Area] Name`; each area also
 * has a coloured ground pad named `[Area] Area`.
 *
 * @param prefabs spawns the prefab library pieces and the animated character; its `projectAssetsDir` must point at the
 *   project's `Assets` folder (with the trailing separator) holding the bundled `EngineDefaults`.
 */
class FeatureShowcase(private val prefabs: PrefabsGenerator) {

    /** A demo area: [key] names its legend string (`showcase.area.<key>`), [center] is the middle of its ground pad. */
    class Area(val key: String, val label: String, val center: Vector3f, val size: Vector3f, val color: Vector4f)

    companion object {
        const val SCENE_NAME = "FeatureShowcase"

        /** The `.skmat` written next to the showcase (`Assets/Materials/`) and linked by `[Materials] Asset`. */
        const val ASSET_MATERIAL_DIR = "Materials"
        const val ASSET_MATERIAL_NAME = "ShowcaseJade"
        val ASSET_MATERIAL: MaterialAsset get() = MaterialAsset.from(
            Material(baseColor = Vector4f(0.1f, 0.75f, 0.55f, 1f), metallicFactor = 0.1f, roughnessFactor = 0.2f),
            ASSET_MATERIAL_NAME,
        )

        private const val BACK = -12f
        private const val MIDDLE = 2f
        private const val FRONT = 14.5f

        val AREAS = listOf(
            Area("lights", "Lights", Vector3f(-22f, 0f, BACK), Vector3f(6f, 0f, 5f), Vector4f(0.35f, 0.3f, 0.45f, 1f)),
            Area("shadows", "Shadows", Vector3f(-7f, 0f, BACK), Vector3f(6f, 0f, 5f), Vector4f(0.55f, 0.55f, 0.5f, 1f)),
            Area("materials", "Materials", Vector3f(8f, 0f, BACK), Vector3f(6f, 0f, 5f), Vector4f(0.3f, 0.4f, 0.35f, 1f)),
            Area("particles", "Particles", Vector3f(22f, 0f, BACK), Vector3f(6f, 0f, 5f), Vector4f(0.25f, 0.25f, 0.3f, 1f)),
            Area("physics", "Physics", Vector3f(-22f, 0f, MIDDLE), Vector3f(6f, 0f, 5f), Vector4f(0.45f, 0.35f, 0.3f, 1f)),
            Area("joints", "Joints", Vector3f(-7f, 0f, MIDDLE), Vector3f(6f, 0f, 5f), Vector4f(0.3f, 0.35f, 0.5f, 1f)),
            Area("vehicle", "Vehicle", Vector3f(8f, 0f, MIDDLE), Vector3f(6f, 0f, 5f), Vector4f(0.4f, 0.4f, 0.3f, 1f)),
            Area("audio", "Audio", Vector3f(22f, 0f, MIDDLE), Vector3f(6f, 0f, 5f), Vector4f(0.3f, 0.45f, 0.45f, 1f)),
            Area("prefabs", "Prefabs", Vector3f(-13f, 0f, FRONT), Vector3f(15f, 0f, 5f), Vector4f(0.45f, 0.45f, 0.45f, 1f)),
            Area("netcode", "Netcode", Vector3f(6f, 0f, FRONT), Vector3f(3.5f, 0f, 4f), Vector4f(0.25f, 0.3f, 0.4f, 1f)),
            Area("animation", "Animation", Vector3f(13.5f, 0f, FRONT), Vector3f(3.5f, 0f, 4f), Vector4f(0.4f, 0.3f, 0.4f, 1f)),
            Area("fog", "Fog", Vector3f(24f, 0f, FRONT), Vector3f(4f, 0f, 4f), Vector4f(0.5f, 0.55f, 0.6f, 1f)),
        )

        const val NETCODE_SERVER = "[Netcode] Server"
        const val NETCODE_PREDICTED = "[Netcode] Predicted"
        const val NETCODE_INTERPOLATED = "[Netcode] Interpolated"
        /** One-way latency of the Netcode demo; edit it on `[Netcode] Session` (round trip is twice this). */
        const val NETCODE_LATENCY_MS = 120
        /** The views are boxes standing on the pad; the session's origin sits at their centre height. */
        private const val VIEW_HALF_HEIGHT = 0.7f

        /**
         * ECS-14: the overlay label showing the random seed, and the script that fills it (copied to
         * `Assets/Scripts/` by [populate]) with the seed and five dice rolls from the `showcase.dice` stream.
         */
        const val SEED_LABEL = "SeedLabel"
        const val SEED_SCRIPT = "ShowcaseSeed.kts"
        private const val SEED_SCRIPT_RESOURCE = "/samples/showcase/$SEED_SCRIPT"

        /** Writes the showcase's scripts into [assetsDir]`/Scripts`. */
        fun writeScripts(assetsDir: File) {
            val dir = File(assetsDir, "Scripts").also { it.mkdirs() }
            val source = FeatureShowcase::class.java.getResourceAsStream(SEED_SCRIPT_RESOURCE) ?: error("Missing resource $SEED_SCRIPT_RESOURCE")
            source.use { input -> File(dir, SEED_SCRIPT).outputStream().use { input.copyTo(it) } }
        }

        /** GUI-13: the minimap (bottom left), the button that opens the full map, and the full map's canvas. */
        const val MINIMAP = "[UI] Minimap"
        const val MAP_BUTTON = "MapButton"
        const val MAP_CANVAS = "[UI] Map"
        const val MAP_CLOSE = "MapClose"
        /** The map follows the scene camera (the showcase has no player character; the camera is "you"). */
        const val MAP_TARGET = "Camera"
        /** The floor (60 x 40 m) with a 2 m margin: what the full map shows. */
        val MAP_WORLD = MapBounds(-32f, -22f, 32f, 22f)
        const val MINIMAP_VIEW = 36f
        const val MINIMAP_SIZE = 240f

        /** Overlay button that cycles the scene fog (off, linear, exp, exp², height) in Play; see [FogCycle]. */
        const val FOG_TOGGLE_BUTTON = "FogToggle"

        /** Prefab library pieces shown in the Prefabs area (all street/transition pieces except the large bowl and half pipe). */
        val PREFAB_PIECES = listOf(
            PrefabType.LEDGE, PrefabType.HUBBA, PrefabType.MANUAL_PAD, PrefabType.GRIND_BOX, PrefabType.FUNBOX,
            PrefabType.RAIL, PrefabType.HANDRAIL, PrefabType.KINKED_RAIL, PrefabType.STAIRS_3, PrefabType.STAIRS_5,
            PrefabType.KICKER, PrefabType.BANK, PrefabType.QUARTER_PIPE, PrefabType.CRATE, PrefabType.BARREL, PrefabType.BENCH,
        )

        /** String keys the overlay uses; they live in the bundled game and editor string tables. */
        val STRING_KEYS = listOf("showcase.title", "showcase.hint", "showcase.areas", "showcase.text", "showcase.button", "showcase.overview", "showcase.fog_toggle", "showcase.seed") +
            AREAS.map { "showcase.area.${it.key}" } +
            listOf("showcase.map.open", "showcase.map.title", "showcase.map.close", "showcase.map.hint") +
            AREAS.map { "showcase.map.${it.key}" }

        const val OVERVIEW = "overview"
        private val OVERVIEW_EYE = Vector3f(0f, 32f, 55f)
        private val OVERVIEW_LOOK_AT = Vector3f(0f, 0f, -2f)

        /** Focus views look down at this angle (degrees) from the +Z side, the same side as the overview. */
        const val FOCUS_PITCH = 35f

        /** Start view: the whole floor from high on the +Z side. */
        fun overviewTarget() = CameraFocusTarget.lookingAt(OVERVIEW, OVERVIEW_EYE, OVERVIEW_LOOK_AT, focusButtonName(OVERVIEW))

        /**
         * A view that frames [area]'s pad: looking at its centre (1 m up) from the +Z side at [FOCUS_PITCH], far enough
         * back that the pad (plus a margin) fits the default 45 degree vertical field of view at 16:9.
         */
        fun focusTarget(area: Area): CameraFocusTarget {
            val lookAt = Vector3f(area.center).add(0f, 1f, 0f)
            val distance = maxOf(area.size.x * 1.7f, area.size.z * 2.6f) + 3f
            val pitch = Math.toRadians(FOCUS_PITCH.toDouble())
            val eye = Vector3f(0f, kotlin.math.sin(pitch).toFloat(), kotlin.math.cos(pitch).toFloat()).mul(distance).add(lookAt)
            return CameraFocusTarget.lookingAt(area.key, eye, lookAt, focusButtonName(area.key))
        }

        /** Name of the overlay button that focuses the view called [key] (an area key or [OVERVIEW]). */
        fun focusButtonName(key: String) = "Focus_$key"
    }

    private val assets get() = prefabs.projectAssetsDir
    private val legendRow = 46f
    private lateinit var scene: Scene

    /** Adds the scene components and every demo area to [target] (normally an empty scene named [SCENE_NAME]). */
    fun populate(target: Scene): Scene {
        SkateGameplay.register()
        scene = target
        scene.addComponent(ScenePhysicsComponent())
            .addComponent(GridLines())
            .addComponent(EnvironmentComponent(skyTexture = Texture(filePath = assets + Assets.Bundled.SKY), fogColor = Vector3f(0.75f, 0.78f, 0.82f), renderFog = false, fogCycleButton = FOG_TOGGLE_BUTTON))
        // Ambient light in the environment, a "Sun" entity linked to the day/night cycle (REN-26)
        SceneSun.addDefaults(scene)
        val overview = overviewTarget()
        add(GameObject("Camera")
            .addComponent(CameraComponent(isDefault = true))
            .addComponent(Transform(Vector3f(overview.position), rotation = Vector3f(overview.rotation)))
            .addComponent(CameraFocusComponent(mutableListOf(overview).apply { AREAS.forEach { add(focusTarget(it)) } }))
            .addComponent(FreeFlyCameraComponent()))
        add(staticBox("Floor", Vector3f(0f, -0.5f, 0f), Vector3f(30f, 0.5f, 20f), Vector4f(0.5f, 0.5f, 0.5f, 1f)))
        AREAS.forEach { area ->
            add(mesh("[${area.label}] Area", Assets.Bundled.CUBE, Vector3f(area.center).add(0f, 0.01f, 0f),
                Vector3f(area.size.x, 0.01f, area.size.z), Material(baseColor = area.color), castShadow = false))
        }
        lights(); shadows(); materials(); particles()
        physics(); joints(); vehicle(); audio()
        prefabLibrary(); netcode(); animation(); fog(); ui()
        // Tests build the scene without a project folder; the script reference is still there.
        if (assets.isNotBlank() && File(assets).isDirectory) writeScripts(File(assets))
        add(GameObject("[UI] SeedController").addComponent(ScriptComponent(assets + "Scripts/" + SEED_SCRIPT)))
        return scene
    }

    private fun add(go: GameObject): GameObject = go.also { scene.addChild(it) }

    private fun at(area: String, x: Float, y: Float, z: Float): Vector3f =
        Vector3f(AREAS.first { it.key == area }.center).add(x, y, z)

    private fun mesh(
        name: String, model: String, pos: Vector3f, scale: Vector3f = Vector3f(1f),
        material: Material = Material(), castShadow: Boolean = true, rotation: Vector3f = Vector3f(),
    ) = GameObject(name)
        .addComponent(Transform(pos, scale, rotation))
        .addComponent(RenderComponent(`3dModel`(assets + model), material, castShadow = castShadow))

    // The unit cube spans -1..1 and Bullet scales box colliders with the transform, so the collider stays unit-sized.
    private fun staticBox(name: String, pos: Vector3f, half: Vector3f, color: Vector4f) =
        mesh(name, Assets.Bundled.CUBE, pos, half, Material(baseColor = color), castShadow = false)
            .addComponent(RigidBody3D(0f, BodyType.Static))
            .addComponent(BoxCollider3D(Vector3f(1f)))

    private fun node(name: String, pos: Vector3f) = GameObject(name).addComponent(Transform(pos))

    private fun lights() {
        add(mesh("[Lights] Sphere", Assets.Bundled.SPHERE, at("lights", 0f, 1.5f, 0f), Vector3f(1.2f)))
        add(node("[Lights] PointRed", at("lights", -4f, 2.5f, 0f))
            .addComponent(PointLightComponent(color = Vector3f(1f, 0.1f, 0.1f), intensity = 4f)))
        add(node("[Lights] PointBlue", at("lights", 4f, 2.5f, 0f))
            .addComponent(PointLightComponent(color = Vector3f(0.1f, 0.2f, 1f), intensity = 4f)))
        add(node("[Lights] SpotGreen", at("lights", 0f, 7f, 3f))
            .addComponent(SpotLightComponent(direction = Vector3f(0f, -1f, -0.4f), color = Vector3f(0.2f, 1f, 0.3f), intensity = 5f)))
    }

    private fun shadows() {
        // The sun (scene DirectionalLight) gives cascaded shadows; the spot and point lights each get a shadow map.
        add(mesh("[Shadows] Caster", Assets.Bundled.CUBE, at("shadows", -3f, 1f, 0f), Vector3f(0.6f, 1f, 0.6f)))
        add(mesh("[Shadows] TallPillar", Assets.Bundled.CUBE, at("shadows", 0f, 2.5f, -2f), Vector3f(0.3f, 2.5f, 0.3f)))
        add(mesh("[Shadows] NoCaster", Assets.Bundled.CUBE, at("shadows", 3.5f, 0.6f, 2f), Vector3f(0.6f), castShadow = false))
        add(mesh("[Shadows] SpotTarget", Assets.Bundled.SPHERE, at("shadows", 3f, 0.8f, -1f), Vector3f(0.8f)))
        add(node("[Shadows] Spot", at("shadows", 3f, 5f, -1f))
            .addComponent(SpotLightComponent(direction = Vector3f(0f, -1f, 0f), intensity = 6f, cutOff = 25f, outerCutOff = 32f)))
        add(node("[Shadows] Point", at("shadows", -1.5f, 1.5f, 2f))
            .addComponent(PointLightComponent(color = Vector3f(1f, 0.8f, 0.5f), intensity = 3f)))
        // REN-20: a mesh-less parent whose child and grandchild models cast (like the skateboard's visual child).
        // Moving the parent moves both shadows.
        val group = node("[Shadows] Group", at("shadows", -4.5f, 0f, 3f))
        val child = mesh("[Shadows] GroupChild", Assets.Bundled.CUBE, Vector3f(0f, 0.4f, 0f), Vector3f(0.4f))
        child.addChild(mesh("[Shadows] GroupGrandchild", Assets.Bundled.SPHERE, Vector3f(0f, 2.5f, 0f), Vector3f(1f)))
        group.addChild(child)
        add(group)
    }

    private fun materials() {
        (0..4).forEach { i ->
            val t = i / 4f
            add(mesh("[Materials] Metal_$i", Assets.Bundled.SPHERE, at("materials", -4f + i * 2f, 0.8f, 2f), Vector3f(0.8f),
                Material(baseColor = Vector4f(0.9f, 0.7f, 0.2f, 1f), metallicFactor = t, roughnessFactor = 1f - t)))
        }
        add(mesh("[Materials] Emissive", Assets.Bundled.CUBE, at("materials", -3f, 0.7f, -2f), Vector3f(0.7f),
            Material(emissiveFactor = Vector3f(2f, 0.5f, 0f))))
        add(mesh("[Materials] Textured", Assets.Bundled.CUBE, at("materials", 0f, 0.7f, -2f), Vector3f(0.7f),
            Material(baseColorTexture = Texture(filePath = assets + Assets.Bundled.ASPHALT))))
        add(mesh("[Materials] Transparent", Assets.Bundled.CUBE, at("materials", 3f, 0.7f, -2f), Vector3f(0.7f),
            Material(baseColor = Vector4f(0.2f, 0.6f, 1f, 0.4f), alphaMode = AlphaMode.BLEND)))
        // REN-27: a polished chrome sphere mirrors the sky and its surroundings (image-based lighting)
        add(mesh("[Materials] Mirror", Assets.Bundled.SPHERE, at("materials", -5f, 0.95f, -2f), Vector3f(1.35f),
            Material(baseColor = Vector4f(0.95f, 0.95f, 0.95f, 1f), metallicFactor = 1f, roughnessFactor = 0.02f)))
        // EDT-17: material saved as a .skmat asset and linked; loading the scene reads it back from the file
        val asset = ASSET_MATERIAL
        val file = File(File(assets, ASSET_MATERIAL_DIR), "$ASSET_MATERIAL_NAME.${MaterialAsset.EXTENSION}")
        if (File(assets).isDirectory) runCatching { file.parentFile.mkdirs(); asset.save(file) }
        add(mesh("[Materials] Asset", Assets.Bundled.SPHERE, at("materials", 5f, 0.8f, -2f), Vector3f(0.7f),
            asset.toMaterial { Texture(filePath = it) }).also { it.getComponent<RenderComponent>()!!.materialPath = file.path })
    }

    private fun particles() {
        add(node("[Particles] Fire", at("particles", -3.5f, 0.1f, 0f)).addComponent(ParticleEmitter(
            shape = EmissionShape.CONE, coneAngle = 15f, emissionRate = 60f, lifetimeMin = 0.6f, lifetimeMax = 1.2f,
            speedMin = 1f, speedMax = 2f, startSize = 0.5f, endSize = 0.1f, additive = true,
            startColor = Vector4f(1f, 0.6f, 0.1f, 1f), endColor = Vector4f(1f, 0.1f, 0f, 0f),
        )))
        add(node("[Particles] Smoke", at("particles", -1f, 0.1f, 0f)).addComponent(ParticleEmitter(
            shape = EmissionShape.CONE, coneAngle = 10f, emissionRate = 15f, lifetimeMin = 2.5f, lifetimeMax = 4f,
            speedMin = 0.5f, speedMax = 1f, endSpeedScale = 0.3f, startSize = 0.4f, endSize = 1.5f,
            startColor = Vector4f(0.5f, 0.5f, 0.5f, 0.6f), endColor = Vector4f(0.3f, 0.3f, 0.3f, 0f),
        )))
        add(node("[Particles] Sparks", at("particles", 1.5f, 1.5f, 0f)).addComponent(ParticleEmitter(
            shape = EmissionShape.SPHERE, shapeRadius = 0.1f, emissionRate = 0f, burstCount = 60, duration = 1.5f,
            lifetimeMin = 0.5f, lifetimeMax = 1f, speedMin = 3f, speedMax = 5f, gravity = Vector3f(0f, -9.81f, 0f),
            startSize = 0.12f, endSize = 0.02f, additive = true,
            startColor = Vector4f(1f, 0.95f, 0.5f, 1f), endColor = Vector4f(1f, 0.5f, 0.1f, 0f),
        )))
        add(node("[Particles] Fountain", at("particles", 4f, 0.1f, 0f)).addComponent(ParticleEmitter(
            shape = EmissionShape.CONE, coneAngle = 8f, emissionRate = 80f, maxParticles = 512, lifetimeMin = 1.2f,
            lifetimeMax = 1.6f, speedMin = 6f, speedMax = 7f, gravity = Vector3f(0f, -9.81f, 0f),
            startSize = 0.15f, endSize = 0.08f, startColor = Vector4f(0.4f, 0.7f, 1f, 0.9f), endColor = Vector4f(0.6f, 0.8f, 1f, 0f),
        )))
    }

    private fun physics() {
        (0 until 5).forEach { i ->
            add(mesh("[Physics] Box_$i", Assets.Bundled.CUBE, at("physics", -1.5f, 0.5f + i * 1.1f, 0f), Vector3f(0.5f),
                Material(baseColor = Vector4f(0.8f, 0.5f, 0.3f, 1f)))
                .addComponent(RigidBody3D(1f)).addComponent(BoxCollider3D(Vector3f(1f))))
        }
        // Capsules only take a uniform scale; sphere.obj has radius 0.7.
        add(mesh("[Physics] Ball", Assets.Bundled.SPHERE, at("physics", 2f, 6f, 0f), Vector3f(0.5f))
            .addComponent(RigidBody3D(2f)).addComponent(CapsuleCollider3D(0.7f, 0.01f)))
        add(prefabs.spawn(PrefabType.CRATE, at("physics", 2f, 0f, 2f)).labelled("Physics"))
        add(prefabs.spawn(PrefabType.BARREL, at("physics", -2f, 0f, -2f)).labelled("Physics"))
        colliderKinds()
    }

    /** PHY-13: the same bank with a compound-hull collider and with a static triangle-mesh one, a ball rolling down each. */
    private fun colliderKinds() {
        add(prefabs.spawn(PrefabType.BANK, at("physics", -4f, 0f, 3.5f)).apply { name = "[Physics] Compound hull bank" })
        val meshCollider = MeshCollider3D.fromTriangles(PrefabCatalog.shape(PrefabType.BANK)!!.mesh.triangles())
        add(prefabs.spawn(PrefabType.BANK, at("physics", 4f, 0f, 3.5f)).apply {
            name = "[Physics] Triangle mesh bank"
            components.removeAll { it is Collider3D }
        }.addComponent(meshCollider))
        listOf("hull" to -4f, "mesh" to 4f).forEach { (kind, x) ->
            add(mesh("[Physics] Ball on $kind bank", Assets.Bundled.SPHERE, at("physics", x, 2.5f, 3.2f), Vector3f(0.3f),
                Material(baseColor = Vector4f(0.3f, 0.6f, 0.9f, 1f)))
                .addComponent(RigidBody3D(1f)).addComponent(CapsuleCollider3D(0.7f, 0.01f)))
        }
    }

    private fun joints() {
        val red = Material(baseColor = Vector4f(0.9f, 0.3f, 0.2f, 1f))
        // Hinge anchored to the world, released 45 degrees off vertical so it swings.
        val hingePivot = at("joints", 3f, 4.5f, 0f)
        add(mesh("[Joints] HingePendulum", Assets.Bundled.CUBE, Vector3f(hingePivot).add(1.06f, -1.06f, 0f),
            Vector3f(0.15f, 1.5f, 0.15f), red, rotation = Vector3f(0f, 0f, 45f))
            .addComponent(RigidBody3D(2f)).addComponent(BoxCollider3D(Vector3f(1f)))
            .addComponent(JointComponent(
                jointType = JointType.HINGE, anchor = Vector3f(0f, 1.5f, 0f), connectedAnchor = hingePivot,
                axis = Vector3f(0f, 0f, 1f), connectedAxis = Vector3f(0f, 0f, 1f), limitMin = -1.2f, limitMax = 1.2f,
            )))
        // Cone-twist anchored to the world, released 40 degrees off vertical around X.
        val conePivot = at("joints", -3f, 4.5f, 0f)
        add(mesh("[Joints] ConeTwistLamp", Assets.Bundled.CUBE, Vector3f(conePivot).add(0f, -0.766f, -0.643f),
            Vector3f(0.25f, 1f, 0.25f), Material(baseColor = Vector4f(0.3f, 0.6f, 0.9f, 1f)), rotation = Vector3f(40f, 0f, 0f))
            .addComponent(RigidBody3D(1f)).addComponent(BoxCollider3D(Vector3f(1f)))
            .addComponent(JointComponent(
                jointType = JointType.CONE_TWIST, anchor = Vector3f(0f, 1f, 0f), connectedAnchor = conePivot,
                swingSpan1 = 0.9f, swingSpan2 = 0.9f, twistSpan = 0.3f,
            )))
        // Slider anchored to the world along Y: the block drops 2 m along its rail and stops at the lower limit.
        val sliderStart = at("joints", 0f, 3.5f, 2f)
        add(mesh("[Joints] SliderBlock", Assets.Bundled.CUBE, sliderStart, Vector3f(0.4f), red)
            .addComponent(RigidBody3D(3f)).addComponent(BoxCollider3D(Vector3f(1f)))
            .addComponent(JointComponent(
                jointType = JointType.SLIDER, anchor = Vector3f(), connectedAnchor = sliderStart,
                axis = Vector3f(0f, 1f, 0f), connectedAxis = Vector3f(0f, 1f, 0f), limitMin = -2f, limitMax = 0f,
            )))
    }

    private fun vehicle() {
        // Raycast vehicle (PHY-09) with the default 4-wheel layout: it drops and settles on its suspension rays.
        add(mesh("[Vehicle] Car", Assets.Bundled.CUBE, at("vehicle", 0f, 1.5f, 0f), Vector3f(0.9f, 0.3f, 1.8f),
            Material(baseColor = Vector4f(0.2f, 0.4f, 0.9f, 1f)))
            .addComponent(RigidBody3D(800f)).addComponent(BoxCollider3D(Vector3f(1f)))
            .addComponent(VehicleComponent()))
    }

    private fun audio() {
        // The bundled MP3 (AUD-10) as a positional source: streamed (music bus) and downmixed to mono so it pans; it gets
        // louder from maxDistance (25 m, where attenuation stops) down to minDistance (1 m) as the camera approaches.
        // No Doppler: music must not change pitch when the camera glides to an area (AUD-11).
        add(mesh("[Audio] Emitter3D", Assets.Bundled.SPHERE, at("audio", 0f, 1f, 0f), Vector3f(0.6f),
            Material(emissiveFactor = Vector3f(0.2f, 0.8f, 0.8f)))
            .addComponent(AudioComponent(
                assets + Assets.Bundled.MUSIC, is3D = true, loops = true, volume = 0.6f, bus = BusId.MUSIC,
                maxDistance = 25f, dopplerFactor = 0f, playOnAwake = true,
            )))
    }

    private fun prefabLibrary() {
        PREFAB_PIECES.forEachIndexed { i, type ->
            val row = i / 8
            val col = i % 8
            val material = if (type == PrefabType.RAIL || type == PrefabType.HANDRAIL || type == PrefabType.KINKED_RAIL) null
                else listOf(MaterialType.CONCRETE, MaterialType.WOOD_BROWN, MaterialType.WOOD_LIGHT)[i % 3]
            add(prefabs.spawn(type, at("prefabs", -12.25f + col * 3.5f, 0f, -2f + row * 4f), material).labelled("Prefabs"))
        }
    }

    private fun netcode() {
        // GPL-17 / NET-03: a server and two clients in-process with simulated latency, running the kinematic skater
        // step. Green ghost = server, blue = the owner's prediction (just ahead of the ghost: its newest inputs are still in
        // flight), orange = what other players see (interpolated, behind).
        val half = Vector3f(0.2f, VIEW_HALF_HEIGHT, 0.4f)
        add(mesh(NETCODE_SERVER, Assets.Bundled.CUBE, at("netcode", 0f, VIEW_HALF_HEIGHT, 0f), Vector3f(half).mul(1.25f),
            Material(baseColor = Vector4f(0.2f, 0.9f, 0.3f, 0.35f), alphaMode = AlphaMode.BLEND), castShadow = false))
        add(mesh(NETCODE_PREDICTED, Assets.Bundled.CUBE, at("netcode", 0f, VIEW_HALF_HEIGHT, 0f), half,
            Material(baseColor = Vector4f(0.2f, 0.45f, 1f, 1f))))
        add(mesh(NETCODE_INTERPOLATED, Assets.Bundled.CUBE, at("netcode", 0f, VIEW_HALF_HEIGHT, 0f), half,
            Material(baseColor = Vector4f(1f, 0.55f, 0.15f, 1f))))
        add(node("[Netcode] Session", at("netcode", 0f, VIEW_HALF_HEIGHT, 0f)).addComponent(LoopbackPredictionComponent(
            modelId = SkaterPredictionModel.ID, latencyMs = NETCODE_LATENCY_MS,
            serverView = NETCODE_SERVER, predictedView = NETCODE_PREDICTED, interpolatedView = NETCODE_INTERPOLATED,
        )))
    }

    private fun animation() {
        add(prefabs.spawnAnimatedCharacter("[Animation] Character", at("animation", 0f, 0f, 0f), Assets.Bundled.WALKING))
        // EDT-16: the walking clip drives the legs; the skater controller (default state: idle) only the upper body.
        add(prefabs.spawnAnimatedCharacter(
            "[Animation] Upper-body mask", at("animation", 1.5f, 0f, 0f), Assets.Bundled.WALKING, Skater.DEFAULT_ANIMATIONS,
        ).apply {
            getComponent<Animator>()?.apply {
                controllerPath = Skater.CONTROLLER_PATH
                mask = AnimatorMaskDef(MaskPreset.UPPER_BODY)
            }
        })
    }

    private fun fog() {
        // REN-09: posts receding along the right edge of the floor (about 4 m to 34 m from the Fog focus view), so each
        // fog curve (Cycle fog button in Play, or the scene's EnvironmentComponent) is easy to compare.
        (0 until 8).forEach { i ->
            add(mesh("[Fog] Post_$i", Assets.Bundled.CUBE, Vector3f(29f, 1.5f, 17f - i * 5f), Vector3f(0.3f, 1.5f, 0.3f),
                Material(baseColor = Vector4f(0.85f, 0.35f, 0.2f, 1f))))
        }
        add(mesh("[Fog] Tower", Assets.Bundled.CUBE, at("fog", 0f, 4f, 0f), Vector3f(0.5f, 4f, 0.5f)))
    }

    private fun GameObject.labelled(area: String) = apply { name = "[$area] $name" }

    private fun uiNode(name: String, anchorX: Float, anchorY: Float, x: Float, y: Float, w: Float, h: Float) =
        GameObject(name).addComponent(UIElementComponent().placed(anchorX, anchorY, x, y, w, h))

    private fun ui() {
        val canvas = GameObject("[UI] Canvas").addComponent(UICanvasComponent())
        canvas.addChild(uiNode("Title", 0.5f, 0f, 0f, 20f, 1000f, 60f)
            .addComponent(UITextComponent("showcase.title", fontSize = 52f, alignX = 0.5f)))
        canvas.addChild(uiNode("Hint", 0.5f, 0f, 0f, 84f, 1500f, 36f)
            .addComponent(UITextComponent("showcase.hint", fontSize = 28f, alignX = 0.5f)))

        // Legend: one button per area (flies the camera there in Play) plus Overview (back to the start view).
        val legend = listOf(OVERVIEW to "showcase.overview") + AREAS.map { it.key to "showcase.area.${it.key}" }
        canvas.addChild(uiNode("LegendPanel", 0f, 0f, 20f, 140f, 560f, 70f + legend.size * legendRow)
            .addComponent(UIImageComponent(tint = Vector4f(0.05f, 0.08f, 0.15f, 0.75f))))
        canvas.addChild(uiNode("LegendTitle", 0f, 0f, 40f, 150f, 520f, 44f)
            .addComponent(UITextComponent("showcase.areas", fontSize = 36f)))
        legend.forEachIndexed { i, (key, labelKey) ->
            canvas.addChild(uiNode(focusButtonName(key), 0f, 0f, 30f, 200f + i * legendRow, 540f, legendRow - 6f)
                .addComponent(UIButtonComponent(labelKey, fontSize = 24f)))
        }

        canvas.addChild(uiNode(FOG_TOGGLE_BUTTON, 1f, 0f, -20f, 20f, 320f, 64f)
            .addComponent(UIButtonComponent("showcase.fog_toggle", fontSize = 26f)))
        // Filled in Play by the seed script; "?" until then.
        canvas.addChild(uiNode(SEED_LABEL, 1f, 0f, -20f, 96f, 520f, 36f)
            .addComponent(UITextComponent("showcase.seed", listOf("?", "?"), Vector4f(0.8f, 0.9f, 1f, 1f), 26f, alignX = 1f)))

        canvas.addChild(uiNode("WidgetPanel", 1f, 1f, -20f, -20f, 480f, 330f)
            .addComponent(UIImageComponent(tint = Vector4f(0.1f, 0.2f, 0.4f, 0.8f))))
        canvas.addChild(uiNode("FormattedText", 1f, 1f, -40f, -280f, 440f, 44f)
            .addComponent(UITextComponent("showcase.text", listOf("42"), Vector4f(1f, 0.8f, 0.2f, 1f), 32f)))
        canvas.addChild(uiNode("Button", 1f, 1f, -110f, -180f, 300f, 76f)
            .addComponent(UIButtonComponent("showcase.button")))
        canvas.addChild(uiNode("Progress", 1f, 1f, -60f, -110f, 400f, 30f)
            .addComponent(UIProgressBarComponent(value = 0.6f)))
        canvas.addChild(uiNode("Slider", 1f, 1f, -60f, -50f, 400f, 30f)
            .addComponent(UISliderComponent(0f, 1f, 0.3f)))
        // GUI-13: minimap in the bottom-left corner (below the legend), following "you" (the camera).
        canvas.addChild(map(MINIMAP, follow = true, labels = false)
            .addComponent(UIElementComponent().placed(0f, 1f, 20f, -20f, MINIMAP_SIZE, MINIMAP_SIZE)))
        canvas.addChild(uiNode(MAP_BUTTON, 0f, 1f, MINIMAP_SIZE + 36f, -20f, 220f, 60f)
            .addComponent(UIButtonComponent("showcase.map.open", fontSize = 26f)))
        add(canvas)
        add(fullMap())
    }

    /** The full-screen map: a hidden modal canvas, opened by [MAP_BUTTON], closed by [MAP_CLOSE] or Esc. */
    private fun fullMap(): GameObject {
        val canvas = GameObject(MAP_CANVAS)
            .addComponent(UICanvasComponent(sortOrder = 50, visible = false, modal = true))
            .addComponent(UIElementComponent().stretched())
        canvas.addChild(GameObject("Dim").addComponent(UIElementComponent().stretched())
            .addComponent(UIImageComponent(tint = Vector4f(0f, 0f, 0f, 0.6f))))
        canvas.addChild(uiNode("MapTitle", 0.5f, 0f, 0f, 30f, 1200f, 60f)
            .addComponent(UITextComponent("showcase.map.title", fontSize = 48f, alignX = 0.5f)))
        canvas.addChild(uiNode("MapHint", 0.5f, 0f, 0f, 92f, 1400f, 34f)
            .addComponent(UITextComponent("showcase.map.hint", fontSize = 26f, alignX = 0.5f)))
        canvas.addChild(map("FullMap", follow = false, labels = true)
            .addComponent(UIElementComponent().placed(0.5f, 0.5f, 0f, 30f, 1500f, 820f)))
        canvas.addChild(uiNode(MAP_CLOSE, 1f, 0f, -30f, 30f, 220f, 64f)
            .addComponent(UIButtonComponent("showcase.map.close", fontSize = 28f)))
        return canvas
    }

    /** A map widget: background, the floor, one pad per area (with its name when [labels]), and "you". */
    private fun map(name: String, follow: Boolean, labels: Boolean): GameObject {
        val map = GameObject(name)
            .addComponent(UIImageComponent(tint = Vector4f(0.05f, 0.07f, 0.1f, 0.85f)))
            .addComponent(UIMapComponent(
                MAP_WORLD.minX, MAP_WORLD.minZ, MAP_WORLD.maxX, MAP_WORLD.maxZ,
                follow = follow, viewSize = MINIMAP_VIEW, target = MAP_TARGET,
                openButton = if (follow) "" else MAP_BUTTON, closeButton = if (follow) "" else MAP_CLOSE,
            ))
        map.addChild(marker("Floor", 0f, 0f, 60f, 40f, Vector4f(0.3f, 0.3f, 0.32f, 1f)))
        AREAS.forEach { area ->
            val pad = marker("Area_${area.key}", area.center.x, area.center.z, area.size.x * 2f, area.size.z * 2f,
                Vector4f(area.color).also { it.w = 1f }.mul(1.4f, 1.4f, 1.4f, 1f))
            if (labels) pad.addChild(GameObject("Label").addComponent(UIElementComponent().stretched())
                .addComponent(UITextComponent("showcase.map.${area.key}", fontSize = 26f, alignX = 0.5f)))
            map.addChild(pad)
        }
        val you = if (labels) 28f else 16f
        map.addChild(GameObject("You")
            .addComponent(UIElementComponent(width = you, height = you, zOrder = 1))
            .addComponent(UIImageComponent(tint = Vector4f(1f, 0.85f, 0.1f, 1f)))
            .addComponent(UIMapMarkerComponent(tracksTarget = true)))
        return map
    }

    private fun marker(name: String, x: Float, z: Float, width: Float, depth: Float, color: Vector4f) = GameObject(name)
        .addComponent(UIElementComponent())
        .addComponent(UIImageComponent(tint = color))
        .addComponent(UIMapMarkerComponent(x, z, width, depth))
}
