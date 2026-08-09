package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.assets.Assets
import java.io.File

class EngineAssetCopier {
    private val engineAssetsRoot: File = File(System.getProperty("user.dir"))

    fun copyBundledAssets(projectDir: File): Result<Int> {
        return try {
            val assetsDir = File(projectDir, "Assets")
            if (!assetsDir.exists()) {
                assetsDir.mkdirs()
            }

            var copiedCount = 0
            var skippedCount = 0

            for ((destRelative, sourceRelative) in Assets.Bundled.bundledAssets) {
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

            // Copy bundled shaders
            val shadersDir = File(assetsDir.absolutePath + "\\EngineDefaults")
            shadersDir.mkdirs()
            listOf(
                Assets.Shaders.SPLASH,
                Assets.Shaders.SHADER_3D_DEFAULT,
                Assets.Shaders.SHADER_2D_BATCH,
                Assets.Shaders.PICKING,
                Assets.Shaders.PICKING_3D,
                Assets.Shaders.SKYBOX,
                Assets.Shaders.DEBUG,
                Assets.Shaders.SKY_DOME,
                Assets.Shaders.SHADOW,
            ).forEach { path ->
                val outputFile = File(shadersDir, path)
                outputFile.parentFile?.mkdirs()
                EngineAssetCopier::class.java.getResourceAsStream(path)
                    ?.copyTo(outputFile.outputStream())
                copiedCount++
            }

            // Copy assets bundled in jar
            val outputFile = File(assetsDir, Assets.Textures.APP_ICON)
            outputFile.parentFile?.mkdirs()
            EngineAssetCopier::class.java.getResourceAsStream(Assets.Bundled.APP_ICON)
                ?.copyTo(outputFile.outputStream())
            copiedCount++

            Result.success(copiedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getEngineDefaultsRoot(projectDir: File): String {
        return File(projectDir, "Assets/EngineDefaults").absolutePath
    }
}
