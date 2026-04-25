package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.commands.CreateFileCommand
import com.pafoid.skate.editor.commands.DeleteFileCommand
import com.pafoid.skate.editor.commands.RenameFileCommand
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.project.ProjectManager
import com.pafoid.skate.editor.systems.FileSystemScanner
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.windows.project.FileSystemItem
import com.pafoid.skate.editor.ui.windows.project.FileType
import com.pafoid.skate.editor.ui.windows.project.FileTypeResolver
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.FileSystemChangedEvent
import com.pafoid.skate.engine.events.OpenSceneFileEvent
import imgui.flag.ImGuiSelectableFlags
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui.begin
import imgui.internal.ImGui.beginDragDropSource
import imgui.internal.ImGui.beginPopupContextWindow
import imgui.internal.ImGui.end
import imgui.internal.ImGui.endDragDropSource
import imgui.internal.ImGui.endPopup
import imgui.internal.ImGui.getIO
import imgui.internal.ImGui.inputText
import imgui.internal.ImGui.inputTextWithHint
import imgui.internal.ImGui.isItemClicked
import imgui.internal.ImGui.isKeyPressed
import imgui.internal.ImGui.menuItem
import imgui.internal.ImGui.popID
import imgui.internal.ImGui.popItemWidth
import imgui.internal.ImGui.pushID
import imgui.internal.ImGui.pushItemWidth
import imgui.internal.ImGui.sameLine
import imgui.internal.ImGui.selectable
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.setDragDropPayload
import imgui.internal.ImGui.text
import imgui.internal.ImGui.textColored
import imgui.internal.ImGui.treeNodeEx
import imgui.internal.ImGui.treePop
import imgui.type.ImBoolean
import imgui.type.ImString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.IOException

/**
 * Project file system browser window.
 *
 * Mimics Godot's FileSystem dock with:
 * - Recursive tree view of project directories
 * - File type icons
 * - Search/filter with real-time filtering
 * - Favorites system with persistence
 * - Context menu (create, rename, delete, show in explorer, copy path)
 * - Undo support for file operations
 * - Double-click to open scenes or external files
 */
class ProjectWindow : IWindow, KoinComponent {

    private val stringManager: StringManager by inject()
    private val logger: LoggerService by inject()
    private val projectManager: ProjectManager by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    private val sceneManager: SceneManager by inject()
    private val eventSystem: EventSystem by inject()
    private val fileSystemScanner: FileSystemScanner by inject()

    private val searchText = ImString("", 256)
    private var treeCache: List<FileSystemItem> = emptyList()
    private var needsRefresh = true

    // Cached status bar counts — only recalculate when treeCache changes
    private var statusFileCount = 0
    private var statusFolderCount = 0
    private var statusTotalSize = 0L
    private var statusTreeVersion = 0

    // Rename state
    private var renamingItemPath: String? = null
    private val renameInput = ImString("", 128)
    private var renameFinished = false

    // Expanded state: absolute path -> isExpanded
    private val expandedPaths = mutableSetOf<String>()

    // Context menu target
    private var contextTargetItem: FileSystemItem? = null

    // Inline create state
    private var creatingInPath: String? = null
    private var creatingIsDir = false
    private val createInput = ImString("", 128)

    override fun imgui(pOpen: ImBoolean?) {
        if (pOpen?.get() == false) return

        if (begin(stringManager.getString("window.project"), ImGuiWindowFlags.None)) {
            // Refresh tree if needed
            if (needsRefresh) {
                treeCache = fileSystemScanner.scanProject()
                needsRefresh = false
                statusTreeVersion++  // Invalidate status cache
            }

            // ── Search ──
            renderSearchBar()
            separator()

            // ── Tree ──
            renderTree()

            // ── Context Menu ──
            renderContextMenu()

            // ── Inline Create/Rename ──
            handleInlineInput()

            separator()

            // ── Status Bar (uses cached counts, only recalculates when tree changes) ──
            renderStatusBar()
        }
        end()
    }

    // ─────────────────────────────────────────────────────────
    // Search
    // ─────────────────────────────────────────────────────────
    private fun renderSearchBar() {
        pushItemWidth(-1f)
        val hint = "${Icons.SEARCH} ${stringManager.getString("lbl.filter")}..."
        inputTextWithHint("##project_search", hint, searchText)
        popItemWidth()
    }

    // ─────────────────────────────────────────────────────────
    // Tree rendering
    // ─────────────────────────────────────────────────────────
    private fun renderTree() {
        val filter = searchText.get().trim()
        for (item in treeCache) {
            renderTreeNode(item, filter)
        }
    }

    private fun renderTreeNode(item: FileSystemItem, filter: String) {
        // Apply filter: show item if it matches or a descendant matches
        if (filter.isNotEmpty() && !matchesFilter(item, filter)) return

        pushID(item.path)

        if (item.type == FileType.FOLDER) {
            renderFolderNode(item, filter)
        } else {
            renderFileNode(item)
        }

        popID()
    }

    private fun renderFolderNode(item: FileSystemItem, filter: String) {
        val isExpanded = expandedPaths.contains(item.path)

        // Auto-expand if filter matches a descendant
        if (filter.isNotEmpty() && item.children.any { matchesFilter(it, filter) }) {
            expandedPaths.add(item.path)
        }

        val starIcon = if (item.isFavorite) "${Icons.STAR} " else ""
        val headerLabel = "${starIcon}${Icons.FOLDER} ${item.name}"

        val opened = treeNodeEx(item.path, 0, headerLabel)

        // Right-click context menu on folder
        if (isItemClicked(1)) {
            contextTargetItem = item
        }

        // Double-click to expand/collapse
        if (isItemClicked() && getIO().mouseClickedCount[0] == 2) {
            if (isExpanded) expandedPaths.remove(item.path)
            else expandedPaths.add(item.path)
        }

        if (opened) {
            for (child in item.children) {
                renderTreeNode(child, filter)
            }
            treePop()
        }
    }

    private fun renderFileNode(item: FileSystemItem) {
        val icon = FileTypeResolver.getIcon(item.type)
        val starPrefix = if (item.isFavorite) "${Icons.STAR} " else ""
        val label = "$starPrefix$icon ${item.name}"

        val isSelected = false
        val selectableFlags = ImGuiSelectableFlags.SpanAllColumns

        selectable(label, isSelected, selectableFlags)

        // Right-click
        if (isItemClicked(1)) {
            contextTargetItem = item
        }

        // Double-click action
        if (isItemClicked() && getIO().mouseClickedCount[0] == 2) {
            handleDoubleClick(item)
        }

        // Drag-drop source for internal dragging
        if (beginDragDropSource()) {
            setDragDropPayload("FILE_PATH", item.path)
            text("$icon ${item.name}")
            endDragDropSource()
        }
    }

    private fun matchesFilter(item: FileSystemItem, filter: String): Boolean {
        if (item.name.contains(filter, ignoreCase = true)) return true
        if (item.type == FileType.FOLDER) {
            return item.children.any { matchesFilter(it, filter) }
        }
        return false
    }

    // ─────────────────────────────────────────────────────────
    // Double-click handler
    // ─────────────────────────────────────────────────────────
    private fun handleDoubleClick(item: FileSystemItem) {
        when (item.type) {
            FileType.SCENE -> {
                logger.logEditor("Opening scene: ${item.file.name}")
                eventSystem.publish(OpenSceneFileEvent(item.path))
            }
            FileType.SCRIPT_KOTLIN, FileType.SCRIPT_JAVA, FileType.JSON,
            FileType.CONFIG, FileType.TEXT, FileType.SHADER, FileType.UNKNOWN -> {
                openInExternalEditor(item.file)
            }
            else -> {
                openInExternalEditor(item.file)
            }
        }
    }

    private fun openInExternalEditor(file: File) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().edit(file)
            } catch (e: UnsupportedOperationException) {
                try {
                    Desktop.getDesktop().open(file.parentFile)
                } catch (e: IOException) {
                    logger.logEditor("Cannot open file: ${e.message}")
                }
            } catch (e: IOException) {
                logger.logEditor("Cannot open file: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Context Menu
    // ─────────────────────────────────────────────────────────
    private fun renderContextMenu() {
        if (beginPopupContextWindow("ProjectWindowContextMenu")) {
            val target = contextTargetItem

            if (target == null) {
                // Background context — new folder/file at project root
                if (menuItem("${Icons.FOLDER} ${stringManager.getString("context.project.new_folder")}")) {
                    showCreateDialog(isDirectory = true)
                }
                if (menuItem("${Icons.PLUS} ${stringManager.getString("context.project.new_file")}")) {
                    showCreateDialog(isDirectory = false)
                }
            } else {
                when (target.type) {
                    FileType.FOLDER -> {
                        if (menuItem("${Icons.FOLDER} ${stringManager.getString("context.project.new_folder")}")) {
                            showCreateDialog(isDirectory = true, target)
                        }
                        if (menuItem("${Icons.PLUS} ${stringManager.getString("context.project.new_file")}")) {
                            showCreateDialog(isDirectory = false, target)
                        }
                        separator()
                        if (menuItem("${Icons.STAR} ${if (target.isFavorite)
                            stringManager.getString("context.project.remove_favorite")
                            else stringManager.getString("context.project.add_favorite")}")) {
                            fileSystemScanner.toggleFavorite(target.path)
                            needsRefresh = true
                        }
                        separator()
                        if (menuItem("${Icons.EDIT} ${stringManager.getString("context.project.rename")}")) {
                            startRename(target)
                        }
                        if (menuItem("${Icons.TRASH} ${stringManager.getString("context.project.delete")}")) {
                            deleteItem(target)
                        }
                        separator()
                        if (menuItem("${Icons.EXTERNAL_LINK} ${stringManager.getString("context.project.show_in_explorer")}")) {
                            openInExplorer(target.file)
                        }
                        if (menuItem("${Icons.COPY} ${stringManager.getString("context.project.copy_path")}")) {
                            copyPathToClipboard(target.path)
                        }
                    }
                    else -> {
                        // File context menu
                        // Show GUID if asset has one
                        if (target.assetGuid != null) {
                            if (menuItem("${Icons.COPY} Copy GUID")) {
                                copyPathToClipboard(target.assetGuid)
                                logger.logEditor("Copied GUID: ${target.assetGuid}")
                            }
                            separator()
                        }
                        if (menuItem("${Icons.STAR} ${if (target.isFavorite)
                            stringManager.getString("context.project.remove_favorite")
                            else stringManager.getString("context.project.add_favorite")}")) {
                            fileSystemScanner.toggleFavorite(target.path)
                            needsRefresh = true
                        }
                        separator()
                        if (menuItem("${Icons.EDIT} ${stringManager.getString("context.project.rename")}")) {
                            startRename(target)
                        }
                        if (target.assetGuid != null) {
                            if (menuItem("${Icons.ARROW_ROTATE} Reimport")) {
                                reimportAsset(target)
                            }
                            separator()
                        }
                        if (menuItem("${Icons.TRASH} ${stringManager.getString("context.project.delete")}")) {
                            deleteItem(target)
                        }
                        separator()
                        if (menuItem("${Icons.EXTERNAL_LINK} ${stringManager.getString("context.project.open_external")}")) {
                            openInExternalEditor(target.file)
                        }
                        if (menuItem("${Icons.EXTERNAL_LINK} ${stringManager.getString("context.project.show_in_explorer")}")) {
                            openInExplorer(target.file.parentFile)
                        }
                        if (menuItem("${Icons.COPY} ${stringManager.getString("context.project.copy_path")}")) {
                            copyPathToClipboard(target.path)
                        }
                    }
                }
            }

            endPopup()
        }
    }

    // ─────────────────────────────────────────────────────────
    // Actions
    // ─────────────────────────────────────────────────────────
    private fun showCreateDialog(isDirectory: Boolean, parent: FileSystemItem? = null) {
        val targetDir = parent?.file ?: projectManager.getProjectDirectory() ?: return
        creatingInPath = targetDir.absolutePath
        creatingIsDir = isDirectory
        createInput.set(if (isDirectory) "NewFolder" else "NewFile.txt")
    }

    private fun startRename(item: FileSystemItem) {
        renamingItemPath = item.path
        renameInput.set(item.name)
        renameFinished = false
    }

    private fun deleteItem(item: FileSystemItem) {
        undoRedoManager.executeCommand(
            DeleteFileCommand(item.path, logger)
        )
        eventSystem.publish(FileSystemChangedEvent(item.path))
        needsRefresh = true
    }

    private fun reimportAsset(item: FileSystemItem) {
        val guid = item.assetGuid ?: return
        val root = projectManager.getProjectDirectory() ?: return
        val sourceFile = File(root, item.name)
        // For now, just log — actual reimport would go through ImportPipeline
        logger.logEditor("Reimport requested for: ${item.name} (GUID: ${guid.take(8)}...)")
        needsRefresh = true
    }

    private fun openInExplorer(file: File) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(file)
            } catch (e: Exception) {
                logger.logEditor("Cannot open explorer: ${e.message}")
            }
        }
    }

    private fun copyPathToClipboard(path: String) {
        val toolkit = Toolkit.getDefaultToolkit()
        val clipboard = toolkit.systemClipboard
        clipboard.setContents(StringSelection(path), null)
        logger.logEditor("Copied path to clipboard: $path")
    }

    // ─────────────────────────────────────────────────────────
    // Inline input handling (create/rename)
    // ─────────────────────────────────────────────────────────
    private fun handleInlineInput() {
        // Handle create input
        creatingInPath?.let { path ->
            pushID("inline_create")
            text(if (creatingIsDir) "New Folder:" else "New File:")
            sameLine()
            pushItemWidth(150f)
            val enterPressed = inputText("##create_input", createInput)
            if (isKeyPressed(GLFW.GLFW_KEY_ENTER) || enterPressed) {
                val name = createInput.get().trim()
                if (name.isNotEmpty()) {
                    val newFile = File(path, name)
                    undoRedoManager.executeCommand(
                        CreateFileCommand(newFile.absolutePath, creatingIsDir, logger)
                    )
                    eventSystem.publish(FileSystemChangedEvent(newFile.absolutePath))
                    needsRefresh = true
                }
                creatingInPath = null
            }
            if (isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
                creatingInPath = null
            }
            popItemWidth()
            popID()
        }

        // Handle rename input
        renamingItemPath?.let { path ->
            if (!renameFinished) {
                pushID("inline_rename")
                text("Rename:")
                sameLine()
                pushItemWidth(150f)
                val enterPressed = inputText("##rename_input", renameInput)
                if (isKeyPressed(GLFW.GLFW_KEY_ENTER) || enterPressed) {
                    val newName = renameInput.get().trim()
                    if (newName.isNotEmpty()) {
                        undoRedoManager.executeCommand(
                            RenameFileCommand(path, newName, logger)
                        )
                        eventSystem.publish(FileSystemChangedEvent(path))
                        needsRefresh = true
                    }
                    renamingItemPath = null
                    renameFinished = true
                }
                if (isKeyPressed(GLFW.GLFW_KEY_ESCAPE)) {
                    renamingItemPath = null
                    renameFinished = true
                }
                popItemWidth()
                popID()
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Status Bar
    // ─────────────────────────────────────────────────────────
    private fun renderStatusBar() {
        // Only recalculate when tree changes (avoids O(n) traversals every frame)
        val currentVersion = statusTreeVersion
        if (currentVersion != statusTreeCacheVersion) {
            statusFileCount = countFiles(treeCache)
            statusFolderCount = countFolders(treeCache)
            statusTotalSize = treeCache.sumOf { it.size }
            statusTreeCacheVersion = currentVersion
        }

        val sizeStr = formatFileSize(statusTotalSize)
        val filter = searchText.get().trim()
        val filterInfo = if (filter.isNotEmpty()) " (${stringManager.getString("lbl.filtered")})" else ""

        textColored(0.5f, 0.5f, 0.5f, 1.0f,
            "${Icons.FOLDER} $statusFolderCount  ${Icons.PLUS} $statusFileCount$filterInfo  │  $sizeStr"
        )
    }

    private var statusTreeCacheVersion = 0

    private fun countFiles(items: List<FileSystemItem>): Int =
        items.sumOf { if (it.type != FileType.FOLDER) 1 + countFiles(it.children) else countFiles(it.children) }

    private fun countFolders(items: List<FileSystemItem>): Int =
        items.sumOf { if (it.type == FileType.FOLDER) 1 + countFolders(it.children) else countFolders(it.children) }

    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }

    /**
     * Call this when external changes occur (e.g., asset import completes).
     */
    fun refresh() {
        needsRefresh = true
    }
}
