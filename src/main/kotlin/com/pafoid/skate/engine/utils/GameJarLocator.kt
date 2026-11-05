package com.pafoid.skate.engine.utils

import java.io.File

/**
 * Result of a game jar search: the [jar] found (or null), every place [searched] in order, and whether the jar is
 * [stale] (older than the editor build, so the exported game would not match the editor).
 */
data class GameJarSearch(val jar: File?, val searched: List<File>, val stale: Boolean = false)

/**
 * Finds the standalone game jar (`gradlew gameJar`, `*-game.jar`) for exports (AST-11), independently of the working
 * directory. Places, in order:
 * 1. Next to the editor: the folder of the editor's jar ([editorCodeSource] is a file), or, in a dev run where it is a
 *    classes folder such as `build/classes/kotlin/main`, that build's `build/libs`.
 * 2. [configuredPath] (editor settings), a jar file or a folder.
 * 3. The [SYSTEM_PROPERTY] system property (`-Dskate.gameJar=...`), a jar file or a folder.
 *
 * In a folder, `skate-game.jar` wins, else the newest `*-game.jar`. [editorBuildTime] is when the editor was built
 * (its jar, or its class files in a dev run); a jar older than that is [GameJarSearch.stale].
 */
class GameJarLocator(
    private val editorCodeSource: File? = codeSourceOf(GameJarLocator::class.java),
    private val configuredPath: () -> String? = { null },
    private val systemProperty: () -> String? = { System.getProperty(SYSTEM_PROPERTY) },
    private val editorBuildTime: () -> Long? = { buildTimeOf(GameJarLocator::class.java) },
) {
    fun locate(): GameJarSearch {
        val candidates = buildList {
            editorCodeSource?.let { add(besideEditor(it)) }
            configuredPath()?.trim()?.takeIf { it.isNotEmpty() }?.let { add(File(it)) }
            systemProperty()?.trim()?.takeIf { it.isNotEmpty() }?.let { add(File(it)) }
        }.map { it.absoluteFile.normalize() }.distinct()

        val jar = candidates.firstNotNullOfOrNull(::jarIn)
        val built = editorBuildTime()
        val stale = jar != null && built != null && jar.lastModified() + TOLERANCE_MS < built
        return GameJarSearch(jar, candidates, stale)
    }

    companion object {
        const val SYSTEM_PROPERTY = "skate.gameJar"
        const val PREFERRED_NAME = "skate-game.jar"
        private const val SUFFIX = "-game.jar"
        /** File systems and copies blur timestamps a little; only clearly older jars are stale. */
        private const val TOLERANCE_MS = 2_000L

        /** The folder that holds the game jar for an editor running from [codeSource]. */
        fun besideEditor(codeSource: File): File {
            if (codeSource.isFile) return codeSource.absoluteFile.parentFile
            // Dev run: <repo>/build/classes/kotlin/main -> <repo>/build/libs
            val build = generateSequence(codeSource.absoluteFile) { it.parentFile }.firstOrNull { it.name == "build" }
            return File(build ?: codeSource.absoluteFile, "libs")
        }

        /** [candidate] itself when it is a game jar file, else the best game jar inside the folder [candidate]. */
        fun jarIn(candidate: File): File? {
            if (candidate.isFile) return candidate.takeIf { it.extension.equals("jar", ignoreCase = true) }
            val jars = candidate.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) }.orEmpty()
            return jars.firstOrNull { it.name == PREFERRED_NAME } ?: jars.maxByOrNull { it.lastModified() }
        }

        /** The jar or classes folder [type] was loaded from, or null. */
        fun codeSourceOf(type: Class<*>): File? =
            runCatching { File(type.protectionDomain.codeSource.location.toURI()) }.getOrNull()

        /** When [type]'s build was made: its jar's time, or its newest class file's time in a dev run. */
        fun buildTimeOf(type: Class<*>): Long? {
            val source = codeSourceOf(type) ?: return null
            if (source.isFile) return source.lastModified()
            // Dev run: the newest compiled class (an incremental build only rewrites the classes that changed).
            return source.walkTopDown().filter { it.isFile && it.extension == "class" }.maxOfOrNull { it.lastModified() }
        }
    }
}
