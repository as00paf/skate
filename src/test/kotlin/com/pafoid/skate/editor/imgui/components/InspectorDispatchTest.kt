package com.pafoid.skate.editor.imgui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * EDT-40: a custom inspector (`fun X.imgui(engine: Engine)`) only shows if `Component.imgui` dispatches to it; the
 * EDT-16 Animator inspector was written but never wired, so the Properties window fell back to reflected fields.
 */
class InspectorDispatchTest {
    private val dir = File("src/main/kotlin/com/pafoid/skate/editor/imgui/components")

    /** Older inspectors left unwired on purpose (direct mutation / TODOs); wire them through commands before removing. */
    private val legacyUnwired = setOf("DayNightCycleComponent", "SkeletonComponent")

    /** Not components, or the dispatcher itself. */
    private val notComponents = setOf("Component", "GameObject")

    @Test
    fun EveryComponentInspector_IsDispatched() {
        val inspectors = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { Regex("""(?m)^fun (\w+)\.imgui\(engine: Engine""").findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toSet() - notComponents
        val dispatcher = File(dir, "Component.kt").readText()
        val dispatched = Regex("""is (\w+) -> this\.imgui\(engine\)""").findAll(dispatcher).map { it.groupValues[1] }.toSet()

        assertTrue("Animator" in inspectors)
        assertEquals(emptySet<String>(), inspectors - dispatched - legacyUnwired, "inspectors missing from Component.imgui")
        assertEquals(emptySet<String>(), legacyUnwired intersect dispatched, "now dispatched: remove from legacyUnwired")
    }
}
