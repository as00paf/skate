package com.pafoid.skate.engine.scripting.completion

import com.pafoid.skate.engine.scripting.host.KotlinScriptHost
import com.pafoid.skate.engine.scripting.host.LuaScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.engine.scripting.host.TypeScriptHost
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Developer tool: `gradlew completeScript --args=<file>@<line>:<col>` prints completions at a 1-based caret position. */
fun main(args: Array<String>) {
    val spec = args.singleOrNull()?.let { Regex("(.+)@(\\d+):(\\d+)").matchEntire(it) }
    if (spec == null) {
        System.err.println("usage: completeScript --args=<file>@<line>:<col>   (1-based; col 1 = caret at line start)")
        exitProcess(2)
    }
    val path = spec.groupValues[1]
    val text = File(path).readText()
    val (line, col) = listOf(spec.groupValues[2].toInt(), spec.groupValues[3].toInt())
    val lines = text.split("\n")
    if (line !in 1..lines.size) {
        System.err.println("line $line is outside the file (${lines.size} lines)")
        exitProcess(2)
    }
    val offset = lines.take(line - 1).sumOf { it.length + 1 } + (col - 1).coerceIn(0, lines[line - 1].length)
    val hosts = ScriptHostRegistry().apply { register(KotlinScriptHost()); register(TypeScriptHost()); register(LuaScriptHost()) }
    CompletionService(hosts).use { service ->
        val items = service.request(CompletionRequest(path, text, offset)).result.get(30, TimeUnit.SECONDS)
        if (items.isEmpty()) println("(no completions)")
        items.forEach { println("%-8s %-18s %s%s".format(it.kind, it.label, it.signature ?: "", it.documentation?.let { d -> "  -- $d" } ?: "")) }
    }
    exitProcess(0)
}
