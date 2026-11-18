package com.pafoid.skate.engine.platform.steampipe

import java.io.File

/** What the build values are filled from: see [SteamPublishTarget.describe]. */
data class BuildDescriptionValues(val project: String, val version: String, val date: String)

enum class SteamUploadIssue { TARGET_INVALID, USERNAME_INVALID, CONTENT_MISSING, CONTENT_EMPTY }

/** A staged build script, or why staging was refused. */
data class StagedBuild(val script: File?, val issues: List<SteamUploadIssue> = emptyList(), val targetIssues: List<SteamPublishIssue> = emptyList()) {
    val isReady: Boolean get() = script != null
}

/** How [SteamCmdManager.upload] ended. Deciding success from the output (BuildID) is NET-07d's parser. */
data class SteamUploadRun(val staged: StagedBuild, val run: SteamCmdRun?)

/**
 * Prepares and runs SteamPipe uploads (docs/design/NET-07.md §2, §5, §8):
 * - [stage] writes `app_build_<appid>.vdf` into `<stagingDir>/scripts`, with `<stagingDir>/output` as steamcmd's
 *   scratch folder. No `SetLive` (Q3). `preview` writes a dry-run script that scans without uploading.
 * - [uploadCommand] is `steamcmd +login <user> +run_app_build <vdf> +quit`. It never takes a password: uploads
 *   reuse steamcmd's cached session (Q1); the first sign-in over stdin is NET-07d.
 * - [upload] stages, then runs the command, passing every output line through [SteamLogFilter].
 */
class SteamCmdManager(private val runner: SteamCmdRunner) {

    fun stage(
        target: SteamPublishTarget,
        contentRoot: File,
        stagingDir: File,
        values: BuildDescriptionValues,
        preview: Boolean,
    ): StagedBuild {
        val targetIssues = target.issues()
        val issues = buildList {
            if (targetIssues.isNotEmpty()) add(SteamUploadIssue.TARGET_INVALID)
            if (!contentRoot.isDirectory) add(SteamUploadIssue.CONTENT_MISSING)
            else if (contentRoot.listFiles().isNullOrEmpty()) add(SteamUploadIssue.CONTENT_EMPTY)
        }
        if (issues.isNotEmpty()) return StagedBuild(null, issues, targetIssues)

        val output = File(stagingDir, "output").apply { mkdirs() }
        val scripts = File(stagingDir, "scripts").apply { mkdirs() }
        val script = AppBuildScript(
            appId = target.appId,
            description = target.describe(values.project, values.version, values.date),
            contentRoot = contentRoot.absolutePath,
            buildOutput = output.absolutePath,
            depots = listOf(DepotMapping(target.depotId, target.exclusions)),
            preview = preview,
        )
        val file = File(scripts, script.fileName)
        file.writeText(script.toVdf(), Charsets.UTF_8)
        return StagedBuild(file)
    }

    fun upload(
        install: SteamCmdInstall,
        username: String,
        target: SteamPublishTarget,
        contentRoot: File,
        stagingDir: File,
        values: BuildDescriptionValues,
        preview: Boolean,
        cancel: SteamCmdCancel = SteamCmdCancel(),
        onLine: (String) -> Unit = {},
    ): SteamUploadRun {
        if (!isValidUsername(username)) return SteamUploadRun(StagedBuild(null, listOf(SteamUploadIssue.USERNAME_INVALID)), null)
        val staged = stage(target, contentRoot, stagingDir, values, preview)
        val script = staged.script ?: return SteamUploadRun(staged, null)
        val filter = SteamLogFilter(username)
        val workingDir = install.executable.absoluteFile.parentFile
        val run = runner.run(uploadCommand(install.executable, username, script), workingDir, cancel) { onLine(filter.filter(it)) }
        return SteamUploadRun(staged, run)
    }

    companion object {
        /** The upload command. Throws for an invalid [username] (it would otherwise be parsed as more arguments). */
        fun uploadCommand(executable: File, username: String, script: File): List<String> {
            require(isValidUsername(username)) { "Invalid Steam account name" }
            return listOf(executable.absolutePath, "+login", username, "+run_app_build", script.absolutePath, "+quit")
        }

        /** Steam account names: 3 to 64 letters, digits or underscores. */
        fun isValidUsername(name: String): Boolean = name.length in 3..64 && name.all { it.isLetterOrDigit() && it.code < 128 || it == '_' }
    }
}
