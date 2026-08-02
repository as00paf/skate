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

            Result.success(copiedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getEngineDefaultsRoot(projectDir: File): String {
        return File(projectDir, "Assets/EngineDefaults").absolutePath
    }
}
