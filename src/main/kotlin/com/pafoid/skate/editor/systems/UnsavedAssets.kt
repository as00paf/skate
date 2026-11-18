package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.ecs.Scene

/** Kinds of document the close prompt lists (EDT-82), each with its translated label `lbl.unsaved_assets.kind.<key>`. */
enum class UnsavedKind(val key: String) {
    SCENE("scene"),
    ANIMATION_CLIP("animation_clip"),
    ANIMATOR_CONTROLLER("animator_controller"),
}

/**
 * One document with unsaved changes. [save] writes it (whether it worked is read back from the source); [discard]
 * forgets the changes in the editor (the file on disk stays as it was). [id] is stable while the document is open.
 */
class UnsavedAsset(
    val kind: UnsavedKind,
    val id: String,
    val name: String,
    val save: () -> Unit,
    val discard: () -> Unit = {},
)

/**
 * Dirty-document registry (EDT-82): every back end that keeps documents open in the editor (scenes, animation clips,
 * animator controllers...) registers a source listing its unsaved ones. The close prompt asks it, never the windows.
 * Sources are asked each time, so the list is always current. Pure: no ImGui, no files.
 */
class UnsavedAssetRegistry {
    private val sources = ArrayList<() -> List<UnsavedAsset>>()

    /** Adds a source of unsaved documents; returns a handle that removes it. */
    fun register(source: () -> List<UnsavedAsset>): () -> Unit {
        sources += source
        return { sources.remove(source) }
    }

    /** Every unsaved document, grouped by kind (scenes first), then by name. A failing source lists nothing. */
    fun unsaved(): List<UnsavedAsset> =
        sources.flatMap { runCatching(it).getOrDefault(emptyList()) }
            .distinctBy { it.kind to it.id }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.name.lowercase() }))

    val hasUnsaved: Boolean get() = unsaved().isNotEmpty()

    /**
     * Saves every unsaved document, then returns what is still unsaved: failed or refused saves (a scene while
     * playing). Asking again also drops entries another save cleaned.
     */
    fun saveAll(): List<UnsavedAsset> {
        unsaved().forEach { runCatching(it.save) }
        return unsaved()
    }

    /** Forgets every unsaved change in the editor (files are not touched). */
    fun discardAll() = unsaved().forEach { runCatching(it.discard) }
}

/** What is being closed. */
enum class CloseTarget { PROJECT, EDITOR }

/**
 * The editor-wide close prompt's state (EDT-82). [request] closes at once when nothing is unsaved; otherwise the
 * prompt opens ([pending]) and the close runs only after Save all succeeds or Discard. Cancel keeps everything open.
 * Pure, so the flow is unit-tested; `UnsavedAssetsModal` is the view.
 */
class CloseGuard(private val registry: UnsavedAssetRegistry) {
    /** The close waiting for an answer, or null when the prompt is not up. */
    var pending: CloseTarget? = null; private set
    /** Documents a Save all could not write; the prompt shows them and stays up. */
    var failed: List<UnsavedAsset> = emptyList(); private set
    private var proceed: (() -> Unit)? = null

    /** The documents the prompt lists. */
    fun unsaved(): List<UnsavedAsset> = registry.unsaved()

    /** Closes [target] through [close] now if nothing is unsaved (returns true), else opens the prompt (false). */
    fun request(target: CloseTarget, close: () -> Unit): Boolean {
        if (!registry.hasUnsaved) { close(); return true }
        // A second request (Quit while a Close Project prompt is up) takes over the pending one.
        pending = target
        proceed = close
        failed = emptyList()
        return false
    }

    /** Saves everything; closes when all saved (true), else keeps the prompt with the [failed] list. */
    fun saveAll(): Boolean {
        if (pending == null) return false
        failed = registry.saveAll()
        if (failed.isNotEmpty()) return false
        finish()
        return true
    }

    /** Drops the unsaved changes and closes. */
    fun discard() {
        if (pending == null) return
        registry.discardAll()
        finish()
    }

    fun cancel() {
        pending = null
        proceed = null
        failed = emptyList()
    }

    private fun finish() {
        val close = proceed
        cancel()
        close?.invoke()
    }
}

/** The open scenes' entries of the registry (EDT-82). */
object UnsavedScenes {
    /**
     * Dirty scenes among [openScenes]. Save asks the scene handler through [save] (refused during Play, so the scene
     * stays listed). Discard clears the dirty flag: closing a project saves its dirty scenes
     * (`ProjectManager.closeProject`), so a discarded scene must not be dirty any more; it then closes unsaved and
     * its file stays as it was.
     */
    fun source(openScenes: () -> List<Scene>, save: (Scene) -> Unit): () -> List<UnsavedAsset> = {
        openScenes().filter { it.isDirty }.map { scene ->
            UnsavedAsset(UnsavedKind.SCENE, "scene:${scene.name}", scene.name, save = { save(scene) }, discard = { scene.isDirty = false })
        }
    }
}
