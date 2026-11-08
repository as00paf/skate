package com.pafoid.skate.engine.assets

import com.pafoid.skate.engine.ecs.SceneSun
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.procedural.PropShapes
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.BoxCollider3D
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.FollowCameraComponent
import com.pafoid.skate.engine.ecs.components.CompoundHullCollider3D
import com.pafoid.skate.engine.ecs.components.CylinderCollider3D
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.GridLines
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScenePhysicsComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.ui.UIButtonComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UITextComponent
import com.pafoid.skate.game.prefabs.Floor
import com.pafoid.skate.game.samples.FeatureShowcase
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.PrefabCatalog
import com.pafoid.skate.game.prefabs.PrefabType
import com.pafoid.skate.game.prefabs.Skateboard
import com.pafoid.skate.game.prefabs.Skater
import org.joml.Vector3f
import java.io.File

/**
 * Spawns prefabs and writes the default project scenes.
 *
 * @param loadAssets false keeps models and textures as path references only (no GL/assimp; for headless tools such
 *   as the CLI). Scenes saved that way are complete: the editor reloads every model and texture by path on open.
 */
class PrefabsGenerator(
    engine: Engine,
    private val loadAssets: Boolean = true,
) {
    private val logger = engine.logger
    private val assetsManager = engine.assetsManager
    private val sceneManager = engine.sceneManager

    var projectAssetsDir: String = ""

    fun spawnSkateboard(): GameObject {
        val path = projectAssetsDir + Assets.Bundled.SKATEBOARD
        return Skateboard(assetsManager.loadModel(path))
    }

    fun spawnSkater(skate: GameObject? = null): Skater {
        val path = projectAssetsDir + Assets.Bundled.JAMES
        val model = assetsManager.loadModel(path)
        val animations = Skater.DEFAULT_ANIMATIONS.map { path ->
            assetsManager.loadAnimationSync(projectAssetsDir + path)
        }
        val skater = Skater("Skater", model, skate, animations = animations)
        skater.animator.resolveController(logger)
        return skater
    }

    fun spawnFloor(): GameObject {
        val texturePath = projectAssetsDir + Assets.Bundled.ASPHALT
        val modelPath = projectAssetsDir + Assets.Bundled.CUBE
        val texture = assetsManager.getTexture(texturePath)
        val material = Material(texture)
        val baseModel = assetsManager.loadModel(modelPath)
        val model = `3dModel`(path = modelPath, mesh = baseModel.mesh)
        return Floor("Floor", model, material)
    }

    /**
     * A plain animated character (bundled James model, no controller or physics) looping [clipPath] (a bundled
     * animation path such as [Assets.Bundled.WALKING]).
     */
    fun spawnAnimatedCharacter(
        name: String,
        position: Vector3f,
        clipPath: String,
        /** More bundled clips for the Animator (e.g. the clips a controller needs); [clipPath] stays the one playing. */
        extraClips: List<String> = emptyList(),
    ): GameObject {
        val modelPath = projectAssetsDir + Assets.Bundled.JAMES
        val model = if (loadAssets) assetsManager.loadModel(modelPath) else `3dModel`(modelPath)
        fun load(path: String) = if (loadAssets) assetsManager.loadAnimationSync(projectAssetsDir + path)
            else Animation(File(path).nameWithoutExtension, emptyList(), 0f, projectAssetsDir + path)
        val clip = load(clipPath)
        val clips = (listOf(clip) + (extraClips - clipPath).map(::load)).toMutableList()
        val go = GameObject(name)
            .addComponent(Transform(position))
            .addComponent(RenderComponent(model))
            .addComponent(Animator(animations = clips, isPlaying = true).also { it.currentAnimation = clip })
        model.skeleton?.let { go.addComponent(SkeletonComponent(SkeletonPose(it))) }
        return go
    }

    /**
     * Spawns any catalog prefab (see [PrefabCatalog]) standing on the ground point [position] (null: the origin),
     * raised by its default height (lights hang 3 m up), with the surface [material] when the prefab offers it.
     * The object is returned detached; callers add it to a scene.
     */
    fun spawn(type: PrefabType, position: Vector3f? = null, material: MaterialType? = null): GameObject {
        val pos = PrefabCatalog.defaultPosition(type).add(position ?: Vector3f())
        val surface = PrefabCatalog.materialFor(type, material)
        return when (type) {
            PrefabType.SKATEBOARD -> spawnSkateboard().placedAt(position)
            PrefabType.SKATER -> spawnSkater().placedAt(position)
            PrefabType.LEDGE -> spawnLedge(pos, surface)
            PrefabType.MANUAL_PAD -> spawnManualPad(pos, surface)
            PrefabType.RAIL -> spawnRail(pos, surface)
            PrefabType.POINT_LIGHT -> GameObject("PointLight").addComponent(Transform(pos)).addComponent(PointLightComponent())
            PrefabType.SPOT_LIGHT -> GameObject("SpotLight").addComponent(Transform(pos)).addComponent(SpotLightComponent())
            PrefabType.CRATE -> spawnCrate(pos, surface)
            PrefabType.BARREL -> spawnBarrel(pos, surface)
            PrefabType.BENCH -> spawnStatic("Bench", type, pos, surface, PropShapes.benchHulls(), friction = 0.6f)
            PrefabType.TRIGGER_VOLUME -> spawnTrigger(pos)
            PrefabType.SPAWN_POINT -> GameObject("SpawnPoint").addComponent(Transform(pos)).also { it.tag = SPAWN_POINT_TAG }
            else -> {
                val shape = requireNotNull(PrefabCatalog.shape(type)) { "no generated shape for $type" }
                spawnStatic(pascalName(type), type, pos, surface, shape.hulls, friction = if (type.isRail()) 0.05f else 0.5f)
                    .also { if (type.isGrindable()) it.tag = GRINDABLE_TAG }
            }
        }
    }

    private fun GameObject.placedAt(position: Vector3f?): GameObject {
        if (position != null) getComponent<Transform>()?.translation?.set(position)
        return this
    }

    /** Textured model of a catalog prefab, loaded from the project's bundled copy. */
    private fun surfaceModel(type: PrefabType, material: MaterialType?): Pair<`3dModel`, Material?> {
        val modelPath = projectAssetsDir + PrefabCatalog.entry(type).bundledModelPath
        if (!loadAssets) {
            return `3dModel`(modelPath) to material?.let { Material(Texture(filePath = projectAssetsDir + it.texturePath)) }
        }
        val baseModel = assetsManager.loadModel(modelPath)
        val texture = material?.let { assetsManager.getTexture(projectAssetsDir + it.texturePath) }
        val model = `3dModel`(
            path = modelPath,
            mesh = if (texture == null) baseModel.mesh else baseModel.mesh.map { it.copy(material = Material(texture)) },
        )
        return model to texture?.let { Material(it) }
    }

    /** Static, shadowed obstacle whose collision is the given convex [hulls], all in one compound-hull collider. */
    private fun spawnStatic(
        name: String, type: PrefabType, position: Vector3f, material: MaterialType?, hulls: List<List<Vector3f>>,
        friction: Float,
    ): GameObject {
        val (model, mat) = surfaceModel(type, material)
        return GameObject(name)
            .addComponent(Transform(position))
            .addComponent(RenderComponent(model, mat, castShadow = true, receiveShadow = true))
            .addComponent(RigidBody3D(0f, bodyType = BodyType.Static).apply { this.friction = friction })
            .addComponent(CompoundHullCollider3D(hulls))
    }

    fun spawnRail(position: Vector3f = Vector3f(), material: MaterialType?): GameObject {
        val (model, mat) = surfaceModel(PrefabType.RAIL, material ?: MaterialType.METAL)
        return GameObject("Rail")
            .addComponent(Transform(position))
            .addComponent(RenderComponent(model, mat))
            .addComponent(RigidBody3D(0f, bodyType = BodyType.Static).apply { friction = 0.05f })
            // rail.obj is a 2 m bar along Z, 0.5-0.6 m above its origin.
            .addComponent(CylinderCollider3D(radius = 0.05f, height = 2.0f, axis = 2, offset = Vector3f(0f, 0.55f, 0f)))
            .also { it.tag = GRINDABLE_TAG }
    }

    fun spawnLedge(position: Vector3f = Vector3f(), material: MaterialType?): GameObject {
        val mat = material ?: MaterialType.CONCRETE
        val (model, _) = surfaceModel(PrefabType.LEDGE, mat)
        return GameObject("${mat.displayName}_Ledge")
            .addComponent(Transform(position))
            .addComponent(RenderComponent(model = model))
            .addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
            // ledge.obj is 1 x 0.5 x 1 m standing on its origin.
            .addComponent(BoxCollider3D(Vector3f(0.5f, 0.25f, 0.5f), offset = Vector3f(0f, 0.25f, 0f)))
            .also { it.tag = GRINDABLE_TAG }
    }

    fun spawnManualPad(position: Vector3f = Vector3f(), material: MaterialType?): GameObject {
        val (model, mat) = surfaceModel(PrefabType.MANUAL_PAD, material ?: MaterialType.CONCRETE)
        return GameObject("ManualPad")
            .addComponent(Transform(position))
            .addComponent(RenderComponent(model, mat))
            .addComponent(RigidBody3D(0f).apply { friction = 0.6f; bodyType = BodyType.Static })
            // manual_pad.obj is 2 x 0.2 x 2 m standing on its origin.
            .addComponent(BoxCollider3D(Vector3f(1f, 0.1f, 1f), offset = Vector3f(0f, 0.1f, 0f)))
    }

    /** 0.8 m wooden crate that can be pushed around. */
    private fun spawnCrate(position: Vector3f, material: MaterialType?): GameObject {
        val (model, mat) = surfaceModel(PrefabType.CRATE, material)
        return GameObject("Crate")
            .addComponent(Transform(position, Vector3f(0.4f)))
            .addComponent(RenderComponent(model, mat))
            .addComponent(RigidBody3D(20f))
            // The unit cube spans -1..1; Bullet scales box colliders with the transform.
            .addComponent(BoxCollider3D(Vector3f(1f)))
    }

    /** Upright 0.6 m wide metal barrel. */
    private fun spawnBarrel(position: Vector3f, material: MaterialType?): GameObject {
        val (model, mat) = surfaceModel(PrefabType.BARREL, material)
        return GameObject("Barrel")
            .addComponent(Transform(position, Vector3f(0.3f)))
            .addComponent(RenderComponent(model, mat))
            .addComponent(RigidBody3D(15f))
            // In model units like the mesh (cylinder.obj is r 1, h 2.85, centred): Bullet applies the 0.3 transform
            // scale to every collider shape, so pre-scaled sizes ended up 0.3x too small and sank into the ground.
            .addComponent(CylinderCollider3D(radius = 1f, height = 2.85f, axis = 1))
    }

    /** 2 m invisible box that reports overlaps (trigger events) and never blocks. */
    private fun spawnTrigger(position: Vector3f): GameObject = GameObject("TriggerVolume")
        .addComponent(Transform(position))
        .addComponent(RigidBody3D(0f, bodyType = BodyType.Static).apply { isTrigger = true })
        .addComponent(BoxCollider3D(Vector3f(1f)))

    private fun PrefabType.isRail() = this == PrefabType.HANDRAIL || this == PrefabType.KINKED_RAIL

    private fun PrefabType.isGrindable() = isRail() || this == PrefabType.HUBBA || this == PrefabType.GRIND_BOX

    private fun pascalName(type: PrefabType) =
        type.name.split('_').joinToString("") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }

    /**
     * The scenes of a new project: `MainScene` (sky and ambient light, a "Sun" entity driven by the day/night cycle, the skater and board on an asphalt floor,
     * a concrete ledge to grind and a HUD canvas) and the [FeatureShowcase] scene (one labelled area per engine
     * feature). See docs/samples.md.
     */
    fun createDefaultScenes(sceneDir: File): List<Scene> {
        val scene = sceneManager.createNewScene(DEFAULT_SCENE, sceneDir.path)
        val skyTexture = assetsManager.getTexture(projectAssetsDir + Assets.Bundled.SKY)
        scene.addComponent(ScenePhysicsComponent())
            .addComponent(GridLines())
            .addComponent(EnvironmentComponent(skyTexture = skyTexture))
            .addComponent(DayNightCycleComponent())
        SceneSun.addDefaults(scene) // the "Sun" entity, driven by the day/night cycle (REN-26)

        spawnDefaultsSync().forEach { scene.addChild(it) }
        sceneManager.saveScene(scene, sceneDir.path)

        val showcase = FeatureShowcase(this).populate(sceneManager.createNewScene(FeatureShowcase.SCENE_NAME, sceneDir.path))
        sceneManager.saveScene(showcase, sceneDir.path)

        // Not opened here: ProjectManager.loadDefaultScenes reopens the saved files (opening twice duplicated bodies).
        return listOf(scene, showcase)
    }

    /**
     * The default skate scene: a follow camera (GPL-05) on the skater, the board, the floor and a few obstacles. The
     * quarter pipe behind the spawn and the funbox give the camera's spring arm something to collide with.
     */
    fun spawnDefaultsSync(): List<GameObject> {
        val camera = GameObject("Camera")
            .addComponent(CameraComponent(isDefault = true))
            .addComponent(Transform(Vector3f(0f, 2.2f, -3.9f)))
            .addComponent(FollowCameraComponent(targetName = "Skater"))
        val skater = spawnSkater()
        val skate = spawnSkateboard()
        val floor = spawnFloor()
        val ledge = spawnLedge(Vector3f(3f, 0f, -3f), MaterialType.CONCRETE)
        val quarterPipe = spawn(PrefabType.QUARTER_PIPE, Vector3f(0f, 0f, -6f), MaterialType.WOOD_TAN)
        val funbox = spawn(PrefabType.FUNBOX, Vector3f(-4f, 0f, 4f), MaterialType.CONCRETE)
        return listOf(camera, skate, skater, floor, ledge, quarterPipe, funbox, spawnHud())
    }

    /** In-game (non-ImGui) HUD: a score line top-left and a restart button top-right. */
    fun spawnHud(): GameObject {
        val canvas = GameObject("HUD").addComponent(UICanvasComponent())
        canvas.addChild(
            GameObject("ScoreText")
                .addComponent(UIElementComponent().placed(0f, 0f, 40f, 30f, 500f, 60f))
                .addComponent(UITextComponent("hud.score", listOf("0"), fontSize = 44f))
        )
        canvas.addChild(
            GameObject("RestartButton")
                .addComponent(UIElementComponent().placed(1f, 0f, -40f, 30f, 260f, 70f))
                .addComponent(UIButtonComponent("pause.restart"))
        )
        return canvas
    }

    companion object {
        const val DEFAULT_SCENE = "MainScene"

        /** [GameObject.tag] of rails, ledges and boxes that trucks can lock onto (docs/obstacles.md). */
        const val GRINDABLE_TAG = "GRINDABLE"

        /** [GameObject.tag] of spawn points; their transform gives the spawn position and +Z heading. */
        const val SPAWN_POINT_TAG = "SpawnPoint"
    }
}
