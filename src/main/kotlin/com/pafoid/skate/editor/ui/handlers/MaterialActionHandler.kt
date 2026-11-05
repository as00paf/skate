package com.pafoid.skate.editor.ui.handlers

import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.editor.commands.objects.AssignMaterialCommand
import com.pafoid.skate.editor.commands.objects.EditMaterialCommand
import com.pafoid.skate.editor.commands.objects.MaterialState
import com.pafoid.skate.editor.commands.objects.SaveMaterialAssetCommand
import com.pafoid.skate.editor.events.MaterialAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.LoggerService.LogLevel
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.render.material.MaterialAsset
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.File

/** Picks `.skmat` files; injectable so the handler can be tested without native dialogs. */
interface MaterialFileDialogs {
    /** Path to save to, or null when cancelled. [suggested] is the proposed file. */
    fun saveAs(suggested: File): String?

    /** Existing file to open, or null when cancelled. */
    fun open(startDir: File?): String?
}

/** Native dialogs (tinyfd), like Save Scene As; titles and filter name from [strings]. */
class TinyMaterialFileDialogs(private val strings: StringManager) : MaterialFileDialogs {
    private val FILTER = "*.${MaterialAsset.EXTENSION}"

    override fun saveAs(suggested: File): String? = withFilter { f ->
        TinyFileDialogs.tinyfd_saveFileDialog(strings.getString("dialog.material.save_title"), suggested.path, f, strings.getString("dialog.material.filter"))
    }?.let { if (it.endsWith(".${MaterialAsset.EXTENSION}", ignoreCase = true)) it else "$it.${MaterialAsset.EXTENSION}" }

    override fun open(startDir: File?): String? = withFilter { f ->
        TinyFileDialogs.tinyfd_openFileDialog(strings.getString("dialog.material.load_title"), startDir?.path?.plus(File.separator), f, strings.getString("dialog.material.filter"), false)
    }

    private fun withFilter(block: (org.lwjgl.PointerBuffer) -> String?): String? {
        val filter = MemoryUtil.memUTF8(FILTER)
        val filters = MemoryUtil.memAllocPointer(1)
        filters.put(0, filter)
        return try { block(filters) } finally {
            MemoryUtil.memFree(filter)
            MemoryUtil.memFree(filters)
        }
    }
}

/**
 * Runs the material inspector's actions (EDT-17) as commands. Value edits and (un)linking are undoable; writing the
 * `.skmat` file is execute-only (undo never deletes or rewrites files).
 *
 * @param projectDir Current project folder; new assets are suggested under `Assets/Materials/`
 * @param resolveTexture Loads the textures a `.skmat` references
 */
class MaterialActionHandler(
    eventSystem: EventSystem,
    private val undoRedoManager: UndoRedoManager,
    private val logger: LoggerService,
    private val projectDir: () -> File?,
    private val resolveTexture: (String) -> Texture?,
    private val dialogs: MaterialFileDialogs,
) {
    init {
        eventSystem.subscribe<MaterialAction.Edit> { e ->
            val command = EditMaterialCommand(e.material, e.before, e.after, e.targetName)
            if (!command.isNoOp) undoRedoManager.executeCommand(command)
        }
        eventSystem.subscribe<MaterialAction.CreateOverride> { e ->
            val source = e.component.model?.mesh?.firstOrNull()?.material ?: Material()
            assign(e.component, MaterialState.of(source).toMaterial(), null, AssignMaterialCommand.Kind.OVERRIDE)
        }
        eventSystem.subscribe<MaterialAction.RemoveOverride> { e -> assign(e.component, null, null, AssignMaterialCommand.Kind.REMOVE_OVERRIDE) }
        eventSystem.subscribe<MaterialAction.Unlink> { e -> assign(e.component, e.component.material, null, AssignMaterialCommand.Kind.UNLINK) }
        eventSystem.subscribe<MaterialAction.SaveAs> { e -> saveAs(e.component) }
        eventSystem.subscribe<MaterialAction.Save> { e ->
            val path = e.component.materialPath ?: return@subscribe saveAs(e.component)
            write(e.component, File(path))
        }
        eventSystem.subscribe<MaterialAction.Load> { e -> load(e.component) }
    }

    private fun assign(component: RenderComponent, material: Material?, path: String?, kind: AssignMaterialCommand.Kind) {
        val command = AssignMaterialCommand(component, material, path, kind)
        if (!command.isNoOp) undoRedoManager.executeCommand(command)
    }

    private fun materialsDir(): File? = projectDir()?.let { File(it, "Assets${File.separator}Materials") }

    private fun saveAs(component: RenderComponent) {
        val material = component.material ?: return
        val baseName = component.materialPath?.let { File(it).nameWithoutExtension }
            ?: component.gameObject?.name?.replace(Regex("""[^\w.-]+"""), "_")?.trim('_')?.ifEmpty { null }
            ?: "Material"
        val suggested = File(materialsDir() ?: File("."), "$baseName.${MaterialAsset.EXTENSION}")
        val path = dialogs.saveAs(suggested) ?: return
        if (write(component, File(path))) assign(component, material, File(path).absolutePath, AssignMaterialCommand.Kind.LINK)
    }

    private fun write(component: RenderComponent, file: File): Boolean {
        val material = component.material ?: return false
        return try {
            undoRedoManager.executeCommand(SaveMaterialAssetCommand(material, file, projectDir()))
            logger.logEditor("Material saved to ${file.path}")
            true
        } catch (e: Exception) {
            logger.logEditor("Failed to save material to ${file.path}: ${e.message}", LogLevel.ERROR)
            false
        }
    }

    private fun load(component: RenderComponent) {
        val start = component.materialPath?.let { File(it).parentFile } ?: materialsDir()?.takeIf { it.isDirectory } ?: projectDir()
        val path = dialogs.open(start) ?: return
        val material = try {
            MaterialAsset.load(File(path)).toMaterial(resolveTexture)
        } catch (e: Exception) {
            logger.logEditor("Failed to load material $path: ${e.message}", LogLevel.ERROR)
            return
        }
        assign(component, material, File(path).absolutePath, AssignMaterialCommand.Kind.LOAD)
    }
}
