package com.pafoid.skate.editor.tools.external

import java.io.File
import java.time.Duration

/** `major.minor.patch` of a Blender build, ordered. */
data class BlenderVersion(val major: Int, val minor: Int, val patch: Int = 0) : Comparable<BlenderVersion> {
    override fun compareTo(other: BlenderVersion): Int =
        compareValuesBy(this, other, BlenderVersion::major, BlenderVersion::minor, BlenderVersion::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        /** Oldest supported release (human decision on AST-19: 4.2 LTS). */
        val MINIMUM = BlenderVersion(4, 2)

        private val PATTERN = Regex("""Blender\s+(\d+)\.(\d+)(?:\.(\d+))?""")

        /** Parses the first line of `blender --version` (`Blender 5.2.3 LTS`); null when it is not Blender's. */
        fun parse(output: String): BlenderVersion? = PATTERN.find(output)?.destructured?.let { (a, b, c) ->
            BlenderVersion(a.toInt(), b.toInt(), c.toIntOrNull() ?: 0)
        }
    }
}

enum class BlenderSource { SETTING, PATH, REGISTRY, DEFAULT_DIR }

data class BlenderInstall(val executable: File, val version: BlenderVersion, val source: BlenderSource)

/**
 * Finds Blender (AST-19 §3; shared with AST-16e). Order: the editor setting, `PATH`, the Windows file association
 * (`HKCR\.blend` → ProgID → open command, which names `blender-launcher.exe` next to `blender.exe`), then
 * `Program Files\Blender Foundation\Blender x.y`. Each candidate is checked with `blender --version`; versions below
 * [BlenderVersion.MINIMUM] are dropped. Results are cached until [invalidate].
 */
class BlenderLocator(
    private val runner: ProcessRunner,
    private val setting: () -> String? = { null },
    private val pathDirs: () -> List<File> = { System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }.map(::File) },
    private val programDirs: () -> List<File> = { listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramW6432")).distinct().map(::File) },
    private val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows"),
    private val isFile: (File) -> Boolean = File::isFile,
) {
    @Volatile
    private var cached: List<BlenderInstall>? = null

    /** Every usable install, best first: the setting wins, then the newest version. Blocking (runs processes). */
    fun find(): List<BlenderInstall> = cached ?: probeAll().also { cached = it }

    fun best(): BlenderInstall? = find().firstOrNull()

    fun invalidate() {
        cached = null
    }

    private fun probeAll(): List<BlenderInstall> {
        val seen = HashSet<String>()
        val found = candidates().mapNotNull { (file, source) ->
            if (!isFile(file) || !seen.add(file.absoluteFile.normalize().path.lowercase())) return@mapNotNull null
            version(file)?.takeIf { it >= BlenderVersion.MINIMUM }?.let { BlenderInstall(file, it, source) }
        }
        val fromSetting = found.filter { it.source == BlenderSource.SETTING }
        return fromSetting + (found - fromSetting.toSet()).sortedByDescending { it.version }
    }

    private fun candidates(): List<Pair<File, BlenderSource>> = buildList {
        setting()?.takeIf { it.isNotBlank() }?.let { add(File(it) to BlenderSource.SETTING) }
        pathDirs().forEach { add(File(it, EXE) to BlenderSource.PATH) }
        if (isWindows) registryExecutable()?.let { add(it to BlenderSource.REGISTRY) }
        programDirs().forEach { root ->
            File(root, "Blender Foundation").listFiles { f -> f.isDirectory && f.name.startsWith("Blender") }
                ?.forEach { add(File(it, EXE) to BlenderSource.DEFAULT_DIR) }
        }
    }

    private fun version(exe: File): BlenderVersion? = runCatching {
        val result = runner.run(listOf(exe.path, "--version"), timeout = Duration.ofSeconds(30))
        if (result.exitCode == 0) BlenderVersion.parse(result.log.joinToString("\n")) else null
    }.getOrNull()

    private fun registryExecutable(): File? = runCatching {
        val progId = registryDefault("""HKCR\.blend""") ?: return null
        val command = registryDefault("""HKCR\$progId\shell\open\command""") ?: return null
        executableFromCommand(command)
    }.getOrNull()

    private fun registryDefault(key: String): String? {
        val result = runner.run(listOf("reg", "query", key, "/ve"), timeout = Duration.ofSeconds(10))
        if (result.exitCode != 0) return null
        return result.log.firstNotNullOfOrNull { REG_DEFAULT.find(it)?.groupValues?.get(1)?.trim() }
    }

    companion object {
        const val EXE = "blender.exe"
        private val REG_DEFAULT = Regex("""\(Default\)\s+REG_(?:EXPAND_)?SZ\s+(.+)$""")

        /** `"C:\...\blender-launcher.exe" "%1"` → `C:\...\blender.exe` (the launcher opens a console-less UI). */
        fun executableFromCommand(command: String): File? {
            val path = Regex("^\"([^\"]+)\"").find(command.trim())?.groupValues?.get(1)
                ?: command.trim().substringBefore(' ').takeIf { it.isNotBlank() }
                ?: return null
            val file = File(path)
            return if (file.name.equals("blender-launcher.exe", ignoreCase = true)) File(file.parentFile, EXE) else file
        }
    }
}

/** Whether a usable Blender is installed, as last checked (the inspector's "Needs Blender" badge, EDT-66). */
sealed interface BlenderAvailability {
    data object Unknown : BlenderAvailability
    data object Checking : BlenderAvailability
    data class Found(val install: BlenderInstall) : BlenderAvailability
    data object Missing : BlenderAvailability
}

/** What the Editor Settings "Test" button found for a typed [path] (blank: automatic search). */
sealed interface BlenderTestResult {
    /** [install] is the typed path, or the best automatic find when no path was typed. */
    data class Found(val install: BlenderInstall) : BlenderTestResult

    /** [path] is not Blender [BlenderVersion.MINIMUM]+; imports would use [install] instead. */
    data class PathUnusable(val path: String, val install: BlenderInstall) : BlenderTestResult

    data class NotFound(val path: String?) : BlenderTestResult

    companion object {
        /** [best] is what a [BlenderLocator] whose setting is [path] returns. */
        fun of(path: String?, best: BlenderInstall?): BlenderTestResult {
            val typed = path?.takeIf { it.isNotBlank() }
            return when {
                best == null -> NotFound(typed)
                typed != null && best.source != BlenderSource.SETTING -> PathUnusable(typed, best)
                else -> Found(best)
            }
        }
    }
}

/**
 * The Blender command line (AST-19 §3): background, factory settings (no user add-ons or preferences), Python errors
 * become a non-zero exit. Never `-y`/`--enable-autoexec`: a `.blend` can embed Python, and only our script may run.
 */
object BlenderCommand {
    val FORBIDDEN = setOf("-y", "--enable-autoexec")

    fun build(blender: File, blendFile: File, script: File, scriptArgs: List<String>): List<String> =
        listOf(blender.path, "-b", "--factory-startup", "--python-exit-code", "1", blendFile.path, "--python", script.path, "--") + scriptArgs
}
