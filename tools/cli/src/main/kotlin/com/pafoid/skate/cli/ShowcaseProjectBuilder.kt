package com.pafoid.skate.cli

import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.*
import org.joml.Vector2i
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Builds the "Feature showcase" sample project (TLG-05) from public engine components only.
 * One scene per engine feature; see docs/samples.md for what to look for in each.
 */
class ShowcaseProjectBuilder(private val serializer: Serializer = Serializer()) {

    companion object {
        const val PROJECT_NAME = "FeatureShowcase"
        val SCENE_NAMES = listOf("Lights", "Shadows", "Materials", "Physics", "Audio", "Animation")
        const val TONE_FILE = "tone.wav"
    }

    private lateinit var assetsPrefix: String

    fun build(parent: File): File {
        val projectDir = File(parent, PROJECT_NAME).also { it.mkdirs() }
        val assetsDir = File(projectDir, "Assets").also { it.mkdirs() }
        val scenesDir = File(projectDir, "Scenes").also { it.mkdirs() }
        File(projectDir, "Builds").mkdirs()
        EngineAssetCopier().copyBundledAssets(projectDir)
        writeTone(File(assetsDir, TONE_FILE))
        assetsPrefix = assetsDir.absolutePath + File.separator

        val scenePaths = SCENE_NAMES.map { name ->
            val scene = when (name) {
                "Lights" -> lights()
                "Shadows" -> shadows()
                "Materials" -> materials()
                "Physics" -> physics()
                "Audio" -> audio()
                else -> animation()
            }
            File(scenesDir, "$name.scene").also { it.writeText(serializer.encode(scene)) }.path
        }
        val projectFile = File(projectDir, "$PROJECT_NAME.skateproject")
        val project = Project(
            name = PROJECT_NAME,
            projectPath = projectFile.path,
            description = "One scene per engine feature; manual regression suite",
            defaultScene = scenePaths.first(),
            scenesPath = scenePaths,
            resolution = Vector2i(1920, 1080),
        )
        projectFile.writeText(serializer.encode(project))
        return projectFile
    }

    private fun baseScene(name: String, ambient: Float = 0.25f, withSun: Boolean = false): Scene {
        val scene = Scene(name)
        scene.addComponent(GridLines()).addComponent(AmbientLightComponent(intensity = ambient))
        if (withSun) scene.addComponent(DirectionalLightComponent())
        scene.children.add(
            GameObject("Camera")
                .addComponent(CameraComponent(isDefault = true))
                .addComponent(Transform(Vector3f(0f, 6f, 16f), rotation = Vector3f(-20f, 0f, 0f)))
        )
        return scene
    }

    private fun mesh(
        name: String, model: String, pos: Vector3f, scale: Vector3f = Vector3f(1f),
        material: Material = Material(), castShadow: Boolean = true,
    ) = GameObject(name)
        .addComponent(Transform(pos, scale))
        .addComponent(RenderComponent(`3dModel`(assetsPrefix + model), material, castShadow = castShadow))

    private fun floor() = mesh("Floor", Assets.Bundled.CUBE, Vector3f(0f, -0.5f, 0f), Vector3f(20f, 0.5f, 20f),
        Material(baseColor = Vector4f(0.5f, 0.5f, 0.5f, 1f)))

    private fun lights() = baseScene("Lights", ambient = 0.05f).also { s ->
        s.children.add(floor())
        s.children.add(mesh("Sphere", Assets.Bundled.SPHERE, Vector3f(0f, 1.5f, 0f), Vector3f(1.5f)))
        s.children.add(GameObject("Sun").addComponent(DirectionalLightComponent(intensity = 0.3f, castShadows = false)))
        s.children.add(GameObject("PointRed").addComponent(Transform(Vector3f(-5f, 3f, 0f)))
            .addComponent(PointLightComponent(color = Vector3f(1f, 0.1f, 0.1f), intensity = 3f)))
        s.children.add(GameObject("PointBlue").addComponent(Transform(Vector3f(5f, 3f, 0f)))
            .addComponent(PointLightComponent(color = Vector3f(0.1f, 0.2f, 1f), intensity = 3f)))
        s.children.add(GameObject("Spot").addComponent(Transform(Vector3f(0f, 8f, 4f)))
            .addComponent(SpotLightComponent(direction = Vector3f(0f, -1f, -0.4f), intensity = 4f)))
    }

    private fun shadows() = baseScene("Shadows", withSun = true).also { s ->
        s.children.add(floor())
        s.children.add(mesh("Caster", Assets.Bundled.CUBE, Vector3f(0f, 2f, 0f), Vector3f(1f, 2f, 1f)))
        s.children.add(mesh("TallCaster", Assets.Bundled.CUBE, Vector3f(-6f, 4f, -4f), Vector3f(0.5f, 4f, 0.5f)))
        s.children.add(mesh("Sphere", Assets.Bundled.SPHERE, Vector3f(5f, 1.2f, 2f), Vector3f(1.2f)))
        s.children.add(mesh("NoCaster", Assets.Bundled.CUBE, Vector3f(8f, 1f, -3f), castShadow = false))
    }

    private fun materials() = baseScene("Materials", withSun = true).also { s ->
        s.children.add(floor())
        (0..4).forEach { i ->
            val t = i / 4f
            s.children.add(mesh("Metal_$i", Assets.Bundled.SPHERE, Vector3f(-8f + i * 4f, 1.2f, 3f), Vector3f(1.2f),
                Material(baseColor = Vector4f(0.9f, 0.7f, 0.2f, 1f), metallicFactor = t, roughnessFactor = 1f - t)))
        }
        s.children.add(mesh("Emissive", Assets.Bundled.CUBE, Vector3f(-4f, 1f, -3f),
            material = Material(emissiveFactor = Vector3f(2f, 0.5f, 0f))))
        s.children.add(mesh("Textured", Assets.Bundled.CUBE, Vector3f(0f, 1f, -3f),
            material = Material(baseColorTexture = Texture(filePath = assetsPrefix + Assets.Bundled.ASPHALT))))
        s.children.add(mesh("Transparent", Assets.Bundled.CUBE, Vector3f(4f, 1f, -3f),
            material = Material(baseColor = Vector4f(0.2f, 0.6f, 1f, 0.4f), alphaMode = AlphaMode.BLEND)))
    }

    private fun physics() = baseScene("Physics", withSun = true).also { s ->
        s.addComponent(ScenePhysicsComponent(debugEnabled = true))
        s.children.add(floor().addComponent(RigidBody3D(0f, BodyType.Static))
            .addComponent(BoxCollider3D(Vector3f(20f, 0.5f, 20f))))
        (0 until 5).forEach { i ->
            s.children.add(mesh("Box_$i", Assets.Bundled.CUBE, Vector3f(0f, 1f + i * 2.2f, 0f))
                .addComponent(RigidBody3D(1f)).addComponent(BoxCollider3D(Vector3f(1f))))
        }
        s.children.add(mesh("Ball", Assets.Bundled.SPHERE, Vector3f(4f, 10f, 0f))
            .addComponent(RigidBody3D(2f)).addComponent(CapsuleCollider3D(0.5f, 0.1f)))
    }

    private fun audio() = baseScene("Audio", withSun = true).also { s ->
        s.children.add(floor())
        s.children.add(mesh("Emitter", Assets.Bundled.SPHERE, Vector3f(-8f, 1f, 0f))
            .addComponent(AudioComponent(assetsPrefix + TONE_FILE, is3D = true, loops = true, volume = 1f)))
        s.children.add(GameObject("Ambient2D")
            .addComponent(AudioComponent(assetsPrefix + TONE_FILE, is3D = false, loops = true, volume = 0.2f)))
    }

    private fun animation() = baseScene("Animation", withSun = true).also { s ->
        s.children.add(floor())
        s.children.add(GameObject("Character")
            .addComponent(Transform(Vector3f(0f, 0f, 0f)))
            .addComponent(RenderComponent(`3dModel`(assetsPrefix + Assets.Bundled.JAMES)))
            .addComponent(Animator(isPlaying = true)))
    }

    private fun writeTone(file: File) {
        val rate = 22050
        val samples = rate
        val data = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(samples) { data.putShort((sin(2 * PI * 440 * it / rate) * 8000).toInt().toShort()) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.capacity()).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(data.capacity())
        file.writeBytes(header.array() + data.array())
    }
}
