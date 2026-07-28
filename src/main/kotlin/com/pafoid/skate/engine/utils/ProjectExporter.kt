package com.pafoid.skate.engine.utils

import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.core.LoggerService
import java.io.File

class ProjectExporter(private val logger: LoggerService) {
    private val assetsPacker = AssetsPacker(logger)

    fun export(project: Project): Boolean {
        if (!packAssets(project)) return false
        if (!generateBatchFiles(project)) return false

        return true
    }

    private fun packAssets(project: Project): Boolean {
        return assetsPacker.pack(project)
    }

    private fun generateBatchFiles(project: Project): Boolean {
        val outputDir = File(project.projectPath).parentFile
        val winBatFile = File(outputDir, "builds\\run-game.bat")
        winBatFile.writeText(
            "@ECHO OFF\n" +
                    "start java -jar skate-game.jar"
        )
        return winBatFile.exists()
    }
}