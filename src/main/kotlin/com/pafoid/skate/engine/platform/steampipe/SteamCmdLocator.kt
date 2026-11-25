package com.pafoid.skate.engine.platform.steampipe

import java.io.File

enum class SteamCmdSource { SETTING, MANAGED, PATH }

data class SteamCmdInstall(val executable: File, val source: SteamCmdSource)

/**
 * Finds `steamcmd.exe` (docs/design/NET-07.md §4). Order: the editor setting (a file, or a folder holding it, such as
 * the Steamworks SDK's `tools\ContentBuilder\builder`), the managed install, then `PATH`. Only checks that the file
 * exists; the installer's first `+quit` run is what proves it works.
 */
class SteamCmdLocator(
    private val setting: () -> String? = { null },
    private val managedDir: File = defaultManagedDir(),
    private val pathDirs: () -> List<File> = {
        System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }.map(::File)
    },
) {
    fun locate(): SteamCmdInstall? {
        setting()?.trim()?.takeIf { it.isNotEmpty() }?.let { configured ->
            val file = File(configured)
            val exe = if (file.isDirectory) File(file, EXECUTABLE) else file
            if (exe.isFile) return SteamCmdInstall(exe, SteamCmdSource.SETTING)
        }
        managedExecutable(managedDir).takeIf { it.isFile }?.let { return SteamCmdInstall(it, SteamCmdSource.MANAGED) }
        pathDirs().map { File(it, EXECUTABLE) }.firstOrNull { it.isFile }?.let { return SteamCmdInstall(it, SteamCmdSource.PATH) }
        return null
    }

    companion object {
        const val EXECUTABLE = "steamcmd.exe"

        fun managedExecutable(managedDir: File): File = File(managedDir, EXECUTABLE)

        /** `%LOCALAPPDATA%\Skate\steamcmd`, or `~/.skate/steamcmd` when the variable is missing. */
        fun defaultManagedDir(env: (String) -> String? = System::getenv): File =
            env("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { File(it, "Skate${File.separator}steamcmd") }
                ?: File(System.getProperty("user.home"), ".skate${File.separator}steamcmd")
    }
}
