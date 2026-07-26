package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.assets.Assets
import java.io.File

class EngineAssetCopier {
    private val engineAssetsRoot: File = File(System.getProperty("user.dir"))
    private val bundledAssets = listOf(
        // Characters
        "EngineDefaults/Characters/james.glb" to Assets.Models.JAMES,
        // Character animations
        "EngineDefaults/Characters/animations/idle_0.fbx" to Assets.Animations.IDLE_0,
        "EngineDefaults/Characters/animations/idle_1.fbx" to Assets.Animations.IDLE_1,
        "EngineDefaults/Characters/animations/idle phone.fbx" to Assets.Animations.IDLE_PHONE,
        "EngineDefaults/Characters/animations/jump.fbx" to Assets.Animations.JUMP,
        "EngineDefaults/Characters/animations/falling to roll.fbx" to Assets.Animations.FALLING,
        "EngineDefaults/Characters/animations/falling idle.fbx" to Assets.Animations.FALLING_IDLE,
        "EngineDefaults/Characters/animations/hard landing.fbx" to Assets.Animations.LANDING,
        "EngineDefaults/Characters/animations/walking.fbx" to Assets.Animations.WALKING,
        "EngineDefaults/Characters/animations/running.fbx" to Assets.Animations.RUNNING,
        "EngineDefaults/Characters/animations/left turn.fbx" to Assets.Animations.LEFT_TURN,
        "EngineDefaults/Characters/animations/left turn 90.fbx" to Assets.Animations.LEFT_TURN_90,
        "EngineDefaults/Characters/animations/left strafe.fbx" to Assets.Animations.LEFT_STRAFE,
        "EngineDefaults/Characters/animations/left strafe walking.fbx" to Assets.Animations.LEFT_STRAFE_WALKING,
        "EngineDefaults/Characters/animations/right turn.fbx" to Assets.Animations.RIGHT_TURN,
        "EngineDefaults/Characters/animations/right turn 90.fbx" to Assets.Animations.RIGHT_TURN_90,
        "EngineDefaults/Characters/animations/right strafe.fbx" to Assets.Animations.RIGHT_STRAFE,
        "EngineDefaults/Characters/animations/right strafe walking.fbx" to Assets.Animations.RIGHT_STRAFE_WALKING,
        // Models
        "EngineDefaults/Models/skateboard_free_model.glb" to Assets.Models.SKATEBOARD_GLB,
        "EngineDefaults/Models/cube.obj" to Assets.Models.CUBE,
        "EngineDefaults/Models/rail.obj" to Assets.Models.RAIL,
        "EngineDefaults/Models/ledge.obj" to Assets.Models.LEDGE,
        "EngineDefaults/Models/kicker.obj" to Assets.Models.KICKER,
        "EngineDefaults/Models/manual_pad.obj" to Assets.Models.MANUAL_PAD,
        "EngineDefaults/Models/bank.obj" to Assets.Models.BANK,
        "EngineDefaults/Models/quarter_pipe.obj" to Assets.Models.QUARTER_PIPE,
        // Textures
        "EngineDefaults/Textures/asphalt.png" to Assets.Textures.ASPHALT,
        "EngineDefaults/Textures/concrete_simple.png" to Assets.Textures.CONCRETE_SIMPLE,
        "EngineDefaults/Textures/metal.png" to Assets.Textures.METAL,
        "EngineDefaults/Textures/wood_brown.png" to Assets.Textures.WOOD_BROWN,
        "EngineDefaults/Textures/wood_light.png" to Assets.Textures.WOOD_LIGHT,
        "EngineDefaults/Textures/wood_tan.png" to Assets.Textures.WOOD_TAN,
        "EngineDefaults/Textures/wood_dark.png" to Assets.Textures.WOOD_DARK,
    )

    fun copyBundledAssets(projectDir: File): Result<Int> {
        return try {
            val assetsDir = File(projectDir, "Assets")
            if (!assetsDir.exists()) {
                assetsDir.mkdirs()
            }

            var copiedCount = 0
            var skippedCount = 0

            for ((destRelative, sourceRelative) in bundledAssets) {
                val sourceFile = File(engineAssetsRoot, sourceRelative)
                val destFile = File(assetsDir, destRelative)

                if (!sourceFile.exists()) {
                    continue
                }

                if (destFile.exists()) {
                    skippedCount++
                    continue
                }

                destFile.parentFile?.mkdirs()
                sourceFile.copyTo(destFile, overwrite = false)
                copiedCount++
            }

            Result.success(copiedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getEngineDefaultsRoot(projectDir: File): String {
        return File(projectDir, "Assets/EngineDefaults").absolutePath
    }
}
