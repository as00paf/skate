package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * EDT-10: every scene or project mutation from the UI goes through an undoable command, or is listed here with the
 * reason it cannot be undone. The audit lists live in this file, so a new exception is a reviewed change.
 */
class UndoCoverageGuardTest {
    private val editor = File("src/main/kotlin/com/pafoid/skate/editor")
    private val uiRoots = listOf("ui/windows", "ui/menus", "imgui", "search/providers").map { File(editor, it) }

    private fun kotlinFiles(roots: List<File>) = roots.flatMap { r -> r.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun File.rel() = relativeTo(editor).invariantSeparatorsPath

    private fun codeLines(file: File) = file.readLines().withIndex()
        .filter { (_, l) -> l.trimStart().let { !it.startsWith("//") && !it.startsWith("*") } }

    /**
     * Execute-only commands (never in the undo history), each with its reason. Any other command must be undoable.
     */
    private val executeOnlyAllowed = mapOf(
        "animation/CreateAnimationClipCommand.kt" to "writes a new .skanim clip file (ANI-17); undo never deletes files",
        "animation/SaveAnimationClipCommand.kt" to "writes the edited .skanim file (ANI-14); undo never rewrites files, the edits stay undoable",
        "animation/CreateControllerCommand.kt" to "writes a new .controller.json (ANI-21); undo never deletes files",
        "animation/SaveControllerCommand.kt" to "writes the edited .controller.json (ANI-21); undo never rewrites files, the edits stay undoable",
        "editor/ClearLogsCommand.kt" to "editor-only state (console)",
        "editor/CopyToClipboardCommand.kt" to "editor-only state (clipboard)",
        "editor/TogglePhysicsDebugCommand.kt" to "editor-only view toggle",
        "editor/SetRenderPassEnabledCommand.kt" to "editor-only debug view of the renderer, not scene data (EDT-54)",
        "objects/MaterialCommands.kt" to "SaveMaterialAssetCommand writes a file; undo never deletes or rewrites files",
        "project/CloseOtherScenesCommand.kt" to "scene tabs (session state, not scene data)",
        "project/CloseAllScenesCommand.kt" to "scene tabs (session state, not scene data)",
        "project/CloseSceneCommand.kt" to "scene tabs (session state, not scene data)",
        "project/CloseProjectCommand.kt" to "project lifecycle",
        "project/CreateProjectCommand.kt" to "project lifecycle (creates files)",
        "project/OpenProjectFileCommand.kt" to "project lifecycle",
        "project/LoadLastProjectCommand.kt" to "project lifecycle",
        "project/DeleteSceneCommand.kt" to "deletes a file, after a confirmation",
        "project/SaveSceneCommand.kt" to "file write",
        "project/SaveSceneAsCommand.kt" to "file write",
        "project/SaveProjectCommand.kt" to "file write",
        "scene/SwitchSceneCommand.kt" to "scene tab navigation",
        "vcs/VcsCommands.kt" to "Git actions (EDT-60): history cannot be undone with Ctrl+Z; discard asks first",
    )

    @Test
    fun ExecuteOnlyCommands_AreAllListedWithAReason() {
        val commands = File(editor, "commands")
        val found = kotlinFiles(listOf(commands))
            .filter { it.name != "ExecuteOnlyCommand.kt" && Regex(""":\s*[^{]*\bExecuteOnlyCommand\b""").containsMatchIn(it.readText()) }
            .map { it.relativeTo(commands).invariantSeparatorsPath }
            .toSet()
        assertEquals(emptySet<String>(), found - executeOnlyAllowed.keys, "New execute-only commands: make them undoable, or add them here with a reason")
        assertEquals(emptySet<String>(), executeOnlyAllowed.keys - found, "Stale entries: these commands are undoable now; remove them from the list")
    }

    @Test
    fun UiCode_DoesNotAddRemoveOrResetComponentsAndObjectsDirectly() {
        val mutation = Regex("""\b(addComponent|removeComponent|attachComponent|addChild|removeChild)\s*\(|\b(component|cmp|it)\.reset\(\)""")
        val violations = kotlinFiles(uiRoots).flatMap { file ->
            codeLines(file).filter { (_, l) -> mutation.containsMatchIn(l) }.map { (i, l) -> "${file.rel()}:${i + 1}: ${l.trim()}" }
        }
        assertTrue(violations.isEmpty(), "Publish an event handled by a command instead:\n" + violations.joinToString("\n"))
    }

    /**
     * Component inspectors run through `Component.imgui` (imgui/components/Component.kt), which wraps them in
     * InspectorEditTracker. A call on a concrete component type would bind to that type's inspector and skip the
     * tracker, so every call outside the dispatcher is listed with its receiver.
     */
    private val inspectorCallsAllowed = mapOf(
        "imgui/components/GameObject.kt" to "receiver is a Component",
        "ui/windows/PropertiesWindow.kt" to "receiver is a Component",
        "ui/windows/AudioInspectorWindow.kt" to "AudioComponent inspector keeps a draft and commits through EditAudioComponentCommand",
    )

    @Test
    fun ComponentInspectors_AreOnlyCalledThroughTheTrackedDispatcher() {
        val call = Regex("""\.imgui\(engine\)""")
        val files = kotlinFiles(uiRoots)
            .filter { it.rel() != "imgui/components/Component.kt" }
            .filter { f -> codeLines(f).any { (_, l) -> call.containsMatchIn(l) } }
            .map { it.rel() }
            .toSet()
        assertEquals(emptySet<String>(), files - inspectorCallsAllowed.keys, "Call component inspectors through Component.imgui (a Component receiver)")
    }

    @Test
    fun Guard_DetectsADirectMutation() {
        val mutation = Regex("""\b(addComponent|removeComponent|attachComponent|addChild|removeChild)\s*\(|\b(component|cmp|it)\.reset\(\)""")
        assertTrue(mutation.containsMatchIn("go.addComponent(BoneOverride())"))
        assertTrue(mutation.containsMatchIn("component.reset()"))
        assertTrue(!mutation.containsMatchIn("wizard.reset()"))
    }
}
