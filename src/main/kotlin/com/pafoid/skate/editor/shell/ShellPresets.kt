package com.pafoid.skate.editor.shell

/**
 * Ready-made commands for the shell panel: engine Gradle tasks and the headless Skate CLI (TLG-01) on the open
 * project. They run from the editor's working directory (the engine checkout when started with `gradlew run`).
 * [labelKey] is the string key shown in the presets menu.
 */
data class ShellPreset(val labelKey: String, val command: String)

object ShellPresets {

    private fun gradlew(windows: Boolean) = if (windows) "gradlew.bat" else "./gradlew"

    /** Gradle's `--args` value with the project path single-quoted (Gradle splits it like a shell). */
    private fun cli(windows: Boolean, args: String) = "${gradlew(windows)} -q :tools:cli:run --args=\"$args\""

    private fun quoted(path: String) = "'" + path.replace("'", "\\'") + "'"

    fun all(windows: Boolean = ShellSession.isWindows(), projectFile: String? = null): List<ShellPreset> {
        val presets = mutableListOf(
            ShellPreset("shell.preset.build", "${gradlew(windows)} build"),
            ShellPreset("shell.preset.test", "${gradlew(windows)} test"),
            ShellPreset("shell.preset.cli_help", cli(windows, "help")),
        )
        if (projectFile != null) {
            val p = quoted(projectFile)
            presets += ShellPreset("shell.preset.cli_validate", cli(windows, "validate $p"))
            presets += ShellPreset("shell.preset.cli_run", cli(windows, "run $p --frames 120"))
            presets += ShellPreset("shell.preset.cli_pack", cli(windows, "pack $p"))
        }
        return presets
    }
}
