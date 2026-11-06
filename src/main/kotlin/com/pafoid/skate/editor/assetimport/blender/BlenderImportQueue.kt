package com.pafoid.skate.editor.assetimport.blender

import java.io.File

/** How one queued import ended (main thread). */
sealed interface BlenderImportOutcome {
    val blend: File

    data class Imported(val result: BlenderImportResult) : BlenderImportOutcome {
        override val blend: File get() = result.blend
    }

    data class UpToDate(override val blend: File) : BlenderImportOutcome

    data class Failed(override val blend: File, val reason: String, val log: String = "") : BlenderImportOutcome
}

/**
 * Runs `.blend` imports (AST-19 §3, §5): [convert] on an IO worker, [apply] on the main thread. One job per `.blend`
 * at a time; saving again while it runs queues exactly one follow-up (the newest save wins); at most [maxJobs] Blender
 * processes run at once, the rest wait in order. Call [request] from the main thread.
 */
class BlenderImportQueue(
    private val io: (() -> Unit) -> Unit,
    private val main: (() -> Unit) -> Unit,
    private val convert: (File) -> BlenderConversion,
    private val apply: (BlenderConversion.Converted) -> BlenderImportResult,
    private val onOutcome: (BlenderImportOutcome) -> Unit,
    private val maxJobs: Int = 2,
) {
    private val running = LinkedHashSet<File>()
    private val followUps = LinkedHashSet<File>()
    private val waiting = ArrayDeque<File>()

    val busy: Boolean get() = running.isNotEmpty() || waiting.isNotEmpty()

    fun isImporting(blend: File): Boolean = key(blend) in running

    fun request(blend: File) {
        val key = key(blend)
        when {
            key in running -> followUps += key
            key in waiting -> Unit
            running.size >= maxJobs -> waiting.addLast(key)
            else -> start(key)
        }
    }

    private fun start(blend: File) {
        running += blend
        io {
            val conversion = try {
                convert(blend)
            } catch (e: Exception) {
                BlenderConversion.Failed(blend, e.message ?: e.javaClass.simpleName)
            }
            main { finish(blend, conversion) }
        }
    }

    private fun finish(blend: File, conversion: BlenderConversion) {
        val outcome = when (conversion) {
            is BlenderConversion.UpToDate -> BlenderImportOutcome.UpToDate(blend)
            is BlenderConversion.Failed -> BlenderImportOutcome.Failed(blend, conversion.reason, conversion.log)
            is BlenderConversion.Converted -> try {
                BlenderImportOutcome.Imported(apply(conversion))
            } catch (e: Exception) {
                BlenderImportOutcome.Failed(blend, "Could not copy the import into the project: ${e.message}")
            }
        }
        running -= blend
        onOutcome(outcome)
        if (followUps.remove(blend)) request(blend)
        while (running.size < maxJobs && waiting.isNotEmpty()) start(waiting.removeFirst())
    }

    private fun key(blend: File): File = blend.absoluteFile.normalize()
}

/**
 * Detects new and saved `.blend` files from periodic folder scans (AST-19 §5). A file is reported once its size and
 * modified time have been the same for two scans in a row, so a save in progress is never read. Pure; main thread.
 */
class BlendWatch {
    data class Stamp(val modified: Long, val length: Long)

    private val accepted = HashMap<File, Stamp>()
    private val pending = HashMap<File, Stamp>()

    /** Files new or changed since they were last reported, now stable. Files gone from [current] are forgotten. */
    fun observe(current: Map<File, Stamp>): List<File> {
        accepted.keys.retainAll(current.keys)
        pending.keys.retainAll(current.keys)
        val ready = ArrayList<File>()
        for ((file, stamp) in current) {
            when {
                accepted[file] == stamp -> pending.remove(file)
                pending[file] == stamp -> {
                    accepted[file] = stamp
                    pending.remove(file)
                    ready += file
                }
                else -> pending[file] = stamp
            }
        }
        return ready.sortedBy { it.path }
    }

    /** Treats every file in [current] as already imported (import on open is off). */
    fun seed(current: Map<File, Stamp>) {
        accepted.clear()
        pending.clear()
        accepted.putAll(current)
    }

    fun clear() {
        accepted.clear()
        pending.clear()
    }

    companion object {
        /** `.blend` files under [root] (hidden folders and Blender's side files skipped). Blocking: IO worker. */
        fun scan(root: File): Map<File, Stamp> {
            if (!root.isDirectory) return emptyMap()
            return root.walkTopDown()
                .onEnter { it == root || !it.name.startsWith(".") }
                .filter { it.isFile && BlenderOutputs.isBlend(it) && !BlenderOutputs.isBlenderSideFile(it) }
                .associate { it.absoluteFile.normalize() to Stamp(it.lastModified(), it.length()) }
        }
    }
}
