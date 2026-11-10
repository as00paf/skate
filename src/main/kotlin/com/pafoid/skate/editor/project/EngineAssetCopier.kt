package com.pafoid.skate.editor.project

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ProjectLayout
import java.io.File

/**
 * Copies the engine's bundled assets ([Assets.Bundled.bundledAssets]) into a new project. A source missing from
 * [engineAssetsRoot] makes the result a failure listing it (the other assets are still copied), so a broken entry
 * shows up as an error instead of a silently missing texture (BUG-020).
 */
class EngineAssetCopier(private val engineAssetsRoot: File = File(System.getProperty("user.dir"))) {

    /** Bundled-asset sources that do not exist under [engineAssetsRoot]. */
    fun missingSources(): List<String> =
        Assets.Bundled.bundledAssets.map { it.second }.filterNot { File(engineAssetsRoot, it).isFile }

    fun copyBundledAssets(projectDir: File): Result<Int> {
        return try {
            val assetsDir = ProjectLayout.assetsDir(projectDir).also { it.mkdirs() }

            var copiedCount = 0
            var skippedCount = 0

            for ((destRelative, sourceRelative) in Assets.Bundled.bundledAssets) {
                val sourceFile = File(engineAssetsRoot, sourceRelative)
                val destFile = File(assetsDir, destRelative)

                if (!sourceFile.isFile) continue // reported below

                if (destFile.exists()) {
                    skippedCount++
                    continue
                }

                destFile.parentFile?.mkdirs()
                sourceFile.copyTo(destFile, overwrite = false)
                copiedCount++
            }

            // Bundled shaders go to EngineDefaults/Shaders, like the Assets.Bundled shader paths (AST-14)
            val shadersDir = File(ProjectLayout.engineDefaultsDir(projectDir), "Shaders").also { it.mkdirs() }
            listOf(
                Assets.Shaders.SPLASH,
                Assets.Shaders.SHADER_3D_DEFAULT,
                Assets.Shaders.SHADER_3D_TEXTURE,
                Assets.Shaders.SHADER_2D_BATCH,
                Assets.Shaders.PICKING,
                Assets.Shaders.PICKING_3D,
                Assets.Shaders.SKYBOX,
                Assets.Shaders.DEBUG,
                Assets.Shaders.SKY_DOME,
                Assets.Shaders.SHADOW,
            ).forEach { path ->
                val outputFile = File(shadersDir, path.substringAfterLast('/'))
                EngineAssetCopier::class.java.getResourceAsStream(path)?.use { input ->
                    outputFile.outputStream().use { input.copyTo(it) }
                }
                copiedCount++
            }

            // Copy assets bundled in jar
            val outputFile = File(assetsDir, Assets.Bundled.PROJECT_APP_ICON)
            outputFile.parentFile?.mkdirs()
            EngineAssetCopier::class.java.getResourceAsStream(Assets.Bundled.APP_ICON)?.use { input ->
                outputFile.outputStream().use { input.copyTo(it) }
            }
            copiedCount++

            val missing = missingSources()
            if (missing.isNotEmpty()) {
                return Result.failure(IllegalStateException("Missing bundled assets (copied the other $copiedCount): $missing"))
            }
            Result.success(copiedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getEngineDefaultsRoot(projectDir: File): String {
        return ProjectLayout.engineDefaultsDir(projectDir).absolutePath
    }
}
