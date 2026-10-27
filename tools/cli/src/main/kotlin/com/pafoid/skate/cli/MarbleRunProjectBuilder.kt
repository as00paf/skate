package com.pafoid.skate.cli

import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.*
import com.pafoid.skate.engine.physics3d.BodyType
import org.joml.Vector2i
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.File

/**
 * Builds the "Marble Run" sample project (TLG-04): rolling bodies on zig-zag ramps with hinge and slider joints.
 * Triggers and camera follow are not available as public engine features yet; see docs/samples.md.
 */
class MarbleRunProjectBuilder(private val serializer: Serializer = Serializer()) {

    companion object {
        const val PROJECT_NAME = "MarbleRun"
        const val SCENE_NAME = "Run"
        const val MARBLE_COUNT = 8
        private const val RAMP_TILT = 10f
    }

    private lateinit var assetsPrefix: String

    fun build(parent: File): File {
        val projectDir = File(parent, PROJECT_NAME).also { it.mkdirs() }
        val assetsDir = File(projectDir, "Assets").also { it.mkdirs() }
        val scenesDir = File(projectDir, "Scenes").also { it.mkdirs() }
        File(projectDir, "Builds").mkdirs()
        EngineAssetCopier().copyBundledAssets(projectDir)
        assetsPrefix = assetsDir.absolutePath + File.separator

        val scenePath = File(scenesDir, "$SCENE_NAME.scene").also { it.writeText(serializer.encode(run())) }.path
        val projectFile = File(projectDir, "$PROJECT_NAME.skateproject")
        projectFile.writeText(serializer.encode(Project(
            name = PROJECT_NAME,
            projectPath = projectFile.path,
            description = "Physics-heavy sample: rolling bodies and joints",
            defaultScene = scenePath,
            scenesPath = listOf(scenePath),
            resolution = Vector2i(1920, 1080),
        )))
        return projectFile
    }

    private fun mesh(name: String, model: String, pos: Vector3f, scale: Vector3f, color: Vector4f, rotZ: Float = 0f) =
        GameObject(name)
            .addComponent(Transform(pos, scale, Vector3f(0f, 0f, rotZ)))
            .addComponent(RenderComponent(`3dModel`(assetsPrefix + model), Material(baseColor = color)))

    // The cube model is a unit cube scaled by the half extents used for its collider
    private fun staticBox(name: String, pos: Vector3f, half: Vector3f, color: Vector4f, rotZ: Float = 0f) =
        mesh(name, Assets.Bundled.CUBE, pos, half, color, rotZ)
            .addComponent(RigidBody3D(0f, BodyType.Static))
            .addComponent(BoxCollider3D(Vector3f(half)))

    private fun run(): Scene {
        val scene = Scene(SCENE_NAME)
        scene.addComponent(GridLines())
            .addComponent(AmbientLightComponent(intensity = 0.3f))
            .addComponent(DirectionalLightComponent())
            .addComponent(ScenePhysicsComponent())
        scene.children.add(
            GameObject("Camera").addComponent(CameraComponent(isDefault = true))
                .addComponent(Transform(Vector3f(0f, 8f, 28f), rotation = Vector3f(-5f, 0f, 0f)))
        )

        val grey = Vector4f(0.55f, 0.55f, 0.6f, 1f)
        scene.children.add(staticBox("Ground", Vector3f(0f, -0.5f, 0f), Vector3f(20f, 0.5f, 6f), grey))

        // Zig-zag ramps: each one drains onto the high end of the next
        listOf(12f to -RAMP_TILT, 8f to RAMP_TILT, 4f to -RAMP_TILT).forEachIndexed { i, (y, tilt) ->
            scene.children.add(staticBox("Ramp_$i", Vector3f(0f, y, 0f), Vector3f(6f, 0.15f, 2f), grey, tilt))
        }

        // Hinge joint anchored to the world: a pendulum bumper over the second ramp
        scene.children.add(
            mesh("Pendulum", Assets.Bundled.CUBE, Vector3f(0f, 10.2f, 0f), Vector3f(0.2f, 1.2f, 0.8f), Vector4f(0.9f, 0.3f, 0.2f, 1f))
                .addComponent(RigidBody3D(2f)).addComponent(BoxCollider3D(Vector3f(0.2f, 1.2f, 0.8f)))
                .addComponent(JointComponent(
                    jointType = JointType.HINGE, anchor = Vector3f(0f, 1.2f, 0f),
                    connectedAnchor = Vector3f(0f, 11.4f, 0f),
                    axis = Vector3f(0f, 0f, 1f), connectedAxis = Vector3f(0f, 0f, 1f),
                    limitMin = -1.0f, limitMax = 1.0f,
                ))
        )

        // Slider joint anchored to the world: a pusher block the marbles shove along the lowest ramp
        scene.children.add(
            mesh("Pusher", Assets.Bundled.CUBE, Vector3f(-4f, 1.2f, 0f), Vector3f(0.4f, 0.4f, 1.8f), Vector4f(0.2f, 0.5f, 0.9f, 1f))
                .addComponent(RigidBody3D(3f)).addComponent(BoxCollider3D(Vector3f(0.4f, 0.4f, 1.8f)))
                .addComponent(JointComponent(
                    jointType = JointType.SLIDER, anchor = Vector3f(), connectedAnchor = Vector3f(-4f, 1.2f, 0f),
                    axis = Vector3f(1f, 0f, 0f), connectedAxis = Vector3f(1f, 0f, 0f),
                    limitMin = -2f, limitMax = 2f,
                ))
        )

        // Goal bin
        scene.children.add(staticBox("BinLeft", Vector3f(-8f, 1f, 0f), Vector3f(0.2f, 1f, 2f), grey))
        scene.children.add(staticBox("BinRight", Vector3f(8f, 1f, 0f), Vector3f(0.2f, 1f, 2f), grey))

        // Marbles start in a row above the first ramp's high end
        repeat(MARBLE_COUNT) { i ->
            val hue = i / MARBLE_COUNT.toFloat()
            scene.children.add(
                mesh("Marble_$i", Assets.Bundled.SPHERE, Vector3f(-5f + (i % 2) * 0.3f, 14f + i * 1.2f, (i % 3 - 1) * 0.6f),
                    Vector3f(0.5f), Vector4f(1f - hue, hue, 0.5f, 1f))
                    .addComponent(RigidBody3D(1f, useCCD = true).also { it.friction = 0.3f; it.angularDamping = 0.05f })
                    .addComponent(CapsuleCollider3D(0.5f, 0.01f))
            )
        }
        return scene
    }
}
