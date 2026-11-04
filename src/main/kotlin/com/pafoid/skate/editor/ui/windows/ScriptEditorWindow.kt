package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.ScriptEditorAction
import com.pafoid.skate.editor.scripting.DiagnosticSource
import com.pafoid.skate.editor.scripting.ScriptDiagnostic
import com.pafoid.skate.editor.scripting.ScriptDocument
import com.pafoid.skate.editor.scripting.ScriptSyntax
import com.pafoid.skate.editor.scripting.ScriptTemplates
import com.pafoid.skate.editor.scripting.TokenKind
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.editor.scripting.BracketAutoClose
import com.pafoid.skate.editor.scripting.CompletionSession
import com.pafoid.skate.editor.scripting.ParameterHints
import com.pafoid.skate.editor.scripting.TextEdit
import com.pafoid.skate.engine.scripting.completion.CompletionHandle
import com.pafoid.skate.engine.scripting.completion.CompletionKind
import com.pafoid.skate.engine.scripting.completion.CompletionRequest
import com.pafoid.skate.engine.scripting.completion.CompletionService
import com.pafoid.skate.engine.scripting.host.ScriptHostRegistry
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.scripting.host.KotlinScriptHost
import com.pafoid.skate.engine.scripting.host.ScriptLoadResult
import com.pafoid.skate.engine.scripting.host.ScriptSource
import com.pafoid.skate.engine.scripting.runtime.ScriptErrorEvent
import imgui.ImGui
import imgui.ImGuiInputTextCallbackData
import imgui.callback.ImGuiInputTextCallback
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiKey
import imgui.flag.ImGuiMouseButton
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImString
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * Text editor for `.kts` scripts: line numbers, syntax highlighting, Ctrl+S (which hot-reloads a running script),
 * compile diagnostics (off the UI thread) and runtime errors, shown inline and in a list that jumps to the line.
 */
class ScriptEditorWindow(
    private val engine: Engine,
    private val projectManager: ProjectManager,
    private val settingsManager: EditorSettingsManager,
) : EditorWindow("window.script_editor") {
    private val strings = engine.stringManager
    private val events = engine.eventSystem

    private val documents = ArrayList<ScriptDocument>()
    private var active: ScriptDocument? = null
    private var activeIndexRequest: ScriptDocument? = null
    private val buffer = ImString(BUFFER_SIZE)
    private var bufferOwner: ScriptDocument? = null
    private val newName = ImString(64)
    private var cursorPos = 0
    private var jumpToLine = -1

    private val pendingOpen = ConcurrentLinkedQueue<File>()
    private val pendingRuntime = ConcurrentLinkedQueue<ScriptEditorUpdate>()
    private val compiler = Executors.newSingleThreadExecutor { r -> Thread(r, "skate-script-diagnostics").apply { isDaemon = true } }
    private val host by lazy { KotlinScriptHost() }

    private class ScriptEditorUpdate(val path: String, val source: DiagnosticSource, val errors: List<ScriptDiagnostic>)

    private val completionService by lazy {
        CompletionService(ScriptHostRegistry().also { it.register(host) })
    }
    private var session: CompletionSession? = null
    private var pendingRequest: CompletionHandle? = null
    private var requestAt = 0.0
    private var autoRequestAt = -1.0
    private var explicitRequest = false
    private var lastTyped = 0.toChar()
    private var acceptRequested = false
    private var moveRequested = 0
    private var lastCaret = 0
    private var lastText = ""
    private var pendingCaret = -1
    private var refocus = false
    private val signatures = HashMap<String, String>()
    private var caretScreenX = 0f
    private var caretScreenY = 0f

    private fun byteOffset(text: String, chars: Int) = text.substring(0, chars.coerceIn(0, text.length)).toByteArray().size

    private fun charOffset(text: String, bytes: Int): Int {
        val raw = text.toByteArray()
        return String(raw, 0, bytes.coerceIn(0, raw.size)).length
    }

    private fun applyEdit(data: ImGuiInputTextCallbackData, text: String, edit: TextEdit) {
        val start = byteOffset(text, edit.pos)
        val count = byteOffset(text, edit.pos + edit.deleteCount) - start
        if (count > 0) data.deleteChars(start, count)
        if (edit.insert.isNotEmpty()) data.insertChars(start, edit.insert)
        val caret = byteOffset(edit.apply(text), edit.caretAfter)
        data.cursorPos = caret
        data.selectionStart = caret
        data.selectionEnd = caret
    }

    private val callback = object : ImGuiInputTextCallback() {
        override fun accept(data: ImGuiInputTextCallbackData) {
            if (data.eventFlag == ImGuiInputTextFlags.CallbackCharFilter) {
                val c = data.eventChar.toChar()
                if (session != null && (c == '\n' || c == '\t' || c == '\r')) {
                    data.eventChar = 0
                    acceptRequested = true
                } else {
                    lastTyped = c
                }
                return
            }
            val text = data.buf
            var caret = charOffset(text, data.cursorPos)
            if (pendingCaret >= 0) {
                caret = pendingCaret.coerceAtMost(text.length)
                data.cursorPos = byteOffset(text, caret)
                data.selectionStart = data.cursorPos
                data.selectionEnd = data.cursorPos
                pendingCaret = -1
            }
            val active = session
            if (acceptRequested) {
                acceptRequested = false
                active?.accept(caret)?.let { applyEdit(data, text, it) }
                session = null
            } else if (active != null && moveRequested != 0) {
                active.move(moveRequested)
                data.cursorPos = byteOffset(text, lastCaret)
                data.selectionStart = data.cursorPos
                data.selectionEnd = data.cursorPos
            } else if (lastTyped != 0.toChar() && text != lastText) {
                val typed = lastTyped
                BracketAutoClose.afterTyped(text, caret, typed)?.let { applyEdit(data, text, it) }
                val now = data.buf
                val nowCaret = charOffset(now, data.cursorPos)
                if (settingsManager.editorSettings.completionAutoPopup && session == null &&
                    CompletionSession.shouldAutoPopup(now, nowCaret, typed)
                ) {
                    autoRequestAt = ImGui.getTime() + settingsManager.editorSettings.completionDelayMs / 1000.0
                }
            }
            moveRequested = 0
            lastTyped = 0.toChar()
            val finalText = data.buf
            val finalCaret = charOffset(finalText, data.cursorPos)
            session?.let { if (!it.update(finalText, finalCaret)) session = null }
            cursorPos = finalCaret
            lastCaret = finalCaret
            lastText = finalText
        }
    }

    init {
        events.subscribe<ScriptEditorAction.Open> { pendingOpen.add(it.file) }
        events.subscribe<ScriptErrorEvent> {
            pendingRuntime.add(
                ScriptEditorUpdate(it.error.path, DiagnosticSource.RUNTIME, listOf(ScriptDiagnostic.from(it.error, DiagnosticSource.RUNTIME)))
            )
        }
    }

    override fun imgui() {
        drainPending()
        if (!isOpen.get()) return
        ImGui.setNextWindowSize(760f, 520f, imgui.flag.ImGuiCond.FirstUseEver)
        if (ImGui.begin(strings.getString(name), isOpen, ImGuiWindowFlags.MenuBar)) {
            toolbar()
            tabs()
            val doc = active
            if (doc == null) {
                ImGui.textDisabled(strings.getString("lbl.script_editor.empty"))
            } else {
                editor(doc)
                diagnosticsList(doc)
            }
        }
        ImGui.end()
    }

    private fun drainPending() {
        while (true) {
            val file = pendingOpen.poll() ?: break
            open(file)
        }
        while (true) {
            val update = pendingRuntime.poll() ?: break
            documents.filter { it.matchesPath(update.path) }.forEach {
                it.setDiagnostics(update.source, update.errors)
            }
        }
    }

    private fun open(requested: File) {
        val file = if (requested.isFile) requested else ScriptDocument.resolve(requested.path, projectManager.getProjectDirectory())
        if (!file.isFile) return
        val existing = documents.find { it.file.absoluteFile == file.absoluteFile }
        val doc = existing ?: ScriptDocument.load(file).also { documents.add(it) }
        active = doc
        activeIndexRequest = doc
        isOpen.set(true)
    }

    private fun toolbar() {
        if (ImGui.button(strings.getString("btn.script_editor.save"))) save()
        ImGui.sameLine()
        if (ImGui.button(strings.getString("btn.script_editor.check"))) check(active)
        ImGui.sameLine()
        ImGui.setNextItemWidth(160f)
        ImGui.inputTextWithHint("##new_script", strings.getString("hint.script_editor.new_name"), newName)
        ImGui.sameLine()
        val valid = ScriptTemplates.fileName(newName.get()) != null && scriptsDirectory() != null
        if (!valid) ImGui.beginDisabled()
        if (ImGui.button(strings.getString("btn.script_editor.new"))) {
            scriptsDirectory()?.let { events.publish(ScriptEditorAction.Create(it, newName.get())) }
            newName.set("")
        }
        if (!valid) ImGui.endDisabled()
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip(strings.getString("tooltip.script_editor.new"))
        }
    }

    private fun scriptsDirectory(): File? =
        projectManager.getProjectDirectory()?.let { File(it, ScriptTemplates.SCRIPTS_DIRECTORY) }

    private fun tabs() {
        if (documents.isEmpty()) return
        if (ImGui.beginTabBar("##script_tabs")) {
            val closed = ArrayList<ScriptDocument>()
            for (doc in documents.toList()) {
                val flags = if (doc === activeIndexRequest) imgui.flag.ImGuiTabItemFlags.SetSelected else 0
                val keepOpen = imgui.type.ImBoolean(true)
                val label = (if (doc.isDirty) "* " else "") + doc.name + "###" + doc.file.absolutePath
                if (ImGui.beginTabItem(label, keepOpen, flags)) {
                    if (active !== doc) active = doc
                    ImGui.endTabItem()
                }
                if (!keepOpen.get()) closed.add(doc)
            }
            activeIndexRequest = null
            ImGui.endTabBar()
            closed.forEach { documents.remove(it); if (active === it) active = documents.lastOrNull() }
        }
    }

    private fun save() {
        val doc = active ?: return
        syncBuffer(doc)
        events.publish(ScriptEditorAction.Save(doc.file, doc.text))
        doc.markSaved()
        check(doc)
    }

    private fun syncBuffer(doc: ScriptDocument) {
        if (bufferOwner === doc && buffer.get() != doc.text) doc.edit(buffer.get())
    }

    /** Compiles off the UI thread; results arrive through [pendingRuntime]. */
    private fun check(doc: ScriptDocument?) {
        doc ?: return
        val source = ScriptSource(doc.file.path, doc.text)
        compiler.execute {
            val errors = when (val result = host.reload(source)) {
                is ScriptLoadResult.Success -> emptyList()
                is ScriptLoadResult.Failure -> result.errors.map { ScriptDiagnostic.from(it, DiagnosticSource.COMPILE) }
            }
            pendingRuntime.add(ScriptEditorUpdate(doc.file.path, DiagnosticSource.COMPILE, errors))
        }
    }

    private fun editor(doc: ScriptDocument) {
        if (bufferOwner !== doc) {
            buffer.set(doc.text)
            bufferOwner = doc
            cursorPos = 0
        }
        val footer = ImGui.getTextLineHeightWithSpacing() * 6f
        val avail = ImGui.getContentRegionAvailY() - footer
        ImGui.beginChild("##script_scroll", 0f, avail.coerceAtLeast(80f), false, ImGuiWindowFlags.HorizontalScrollbar)

        val lineHeight = ImGui.getTextLineHeight()
        val pad = ImGui.getStyle().framePaddingX
        val padY = ImGui.getStyle().framePaddingY
        val lineCount = doc.lineCount
        val gutter = ImGui.calcTextSize(lineCount.toString().padStart(3, '0')).x + 16f
        val totalHeight = lineCount * lineHeight + padY * 2 + lineHeight
        val width = maxOf(ImGui.getContentRegionAvailX() - gutter, 200f)

        val origin = ImGui.getCursorScreenPos()
        val dl = ImGui.getWindowDrawList()
        ImGui.setCursorScreenPos(origin.x + gutter, origin.y)

        if (refocus) { ImGui.setKeyboardFocusHere(); refocus = false }
        if (session != null && focusedLast) {
            if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) moveRequested = -1
            else if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) moveRequested = 1
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) session = null
        }
        ImGui.pushStyleColor(ImGuiCol.Text, 0)
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0)
        ImGui.inputTextMultiline(
            "##script_text", buffer, width, totalHeight,
            ImGuiInputTextFlags.CallbackAlways or ImGuiInputTextFlags.CallbackCharFilter or ImGuiInputTextFlags.AllowTabInput,
            callback,
        )
        ImGui.popStyleColor(2)
        val focused = ImGui.isItemActive()
        focusedLast = focused
        completionFrame(doc, focused)
        if (buffer.get() != doc.text) doc.edit(buffer.get())

        val textX = origin.x + gutter + pad
        val textY = origin.y + padY
        val scrollY = ImGui.getScrollY()
        val viewH = ImGui.getWindowHeight()
        val first = ((scrollY - padY) / lineHeight).toInt().coerceAtLeast(0)
        val last = minOf(lineCount, first + (viewH / lineHeight).toInt() + 2)
        val lines = doc.text.split('\n')
        val tokens = ScriptSyntax.tokenize(doc.text)
        val colors = SyntaxColors
        for (i in first until last) {
            val y = textY + i * lineHeight
            val diagnostics = doc.diagnosticsAt(i + 1)
            if (diagnostics.isNotEmpty()) {
                dl.addRectFilled(origin.x, y, origin.x + gutter + width, y + lineHeight, colors.errorLine)
            }
            val number = (i + 1).toString()
            val nx = origin.x + gutter - 8f - ImGui.calcTextSize(number).x
            dl.addText(nx, y, if (diagnostics.isNotEmpty()) colors.errorText else colors.lineNumber, number)
            val line = lines.getOrElse(i) { "" }.removeSuffix("\r")
            var pos = 0
            var x = textX
            for (t in tokens.getOrElse(i) { emptyList() }) {
                if (t.start > pos) x = drawText(dl, x, y, line.substring(pos, t.start), colors.plain)
                x = drawText(dl, x, y, line.substring(t.start, t.end), colors.of(t.kind))
                pos = t.end
            }
            if (pos < line.length) x = drawText(dl, x, y, line.substring(pos), colors.plain)
            if (diagnostics.isNotEmpty()) {
                dl.addText(x + 24f, y, colors.errorText, diagnostics.first().message.lineSequence().first())
            }
        }

        if (focused || ImGui.isItemFocused()) {
            val caretLine = doc.lineOfOffset(cursorPos) - 1
            val lineStart = doc.offsetOfLine(caretLine + 1)
            val beforeCaret = lines.getOrElse(caretLine) { "" }.take((cursorPos - lineStart).coerceAtLeast(0))
            val cx = textX + ImGui.calcTextSize(beforeCaret).x
            val cy = textY + caretLine * lineHeight
            caretScreenX = cx - ImGui.getScrollX()
            caretScreenY = cy - scrollY + lineHeight
            parameterHint(doc, dl, cx, cy)
            if ((ImGui.getTime() * 2).toLong() % 2L == 0L) dl.addLine(cx, cy, cx, cy + lineHeight, colors.caret, 1.5f)
        }

        if (jumpToLine > 0) {
            ImGui.setScrollY(((jumpToLine - 3) * lineHeight).coerceAtLeast(0f))
            jumpToLine = -1
        }
        ImGui.endChild()

        if (ImGui.isWindowFocused(imgui.flag.ImGuiFocusedFlags.RootAndChildWindows) &&
            ImGui.getIO().keyCtrl && ImGui.isKeyPressed(ImGuiKey.S, false)
        ) save()
    }

    private var focusedLast = false

    /** Starts/polls completion requests (off the UI thread) and shows the popup. Never blocks the frame. */
    private fun completionFrame(doc: ScriptDocument, focused: Boolean) {
        if (!focused) { autoRequestAt = -1.0; if (pendingRequest == null) return }
        val ctrlSpace = focused && ImGui.getIO().keyCtrl && ImGui.isKeyPressed(ImGuiKey.Space, false)
        val auto = autoRequestAt in 0.0..ImGui.getTime()
        if (ctrlSpace || auto) {
            autoRequestAt = -1.0
            pendingRequest?.cancel()
            explicitRequest = ctrlSpace
            pendingRequest = completionService.request(
                CompletionRequest(doc.file.path, doc.text, cursorPos.coerceIn(0, doc.text.length), explicit = ctrlSpace)
            )
        }
        val request = pendingRequest
        if (request != null && request.result.isDone) {
            pendingRequest = null
            val items = if (request.result.isCancelled) emptyList() else runCatching { request.result.get() }.getOrDefault(emptyList())
            items.forEach { c -> c.signature?.let { signatures[c.label] = it } }
            val caret = cursorPos.coerceIn(0, doc.text.length)
            val created = if (items.isEmpty()) null else CompletionSession(items, CompletionSession.wordStart(doc.text, caret), doc.text, caret)
            session = created?.takeIf { it.filtered.isNotEmpty() }
        }
        session?.let { popup(doc, it) }
    }

    private fun popup(doc: ScriptDocument, active: CompletionSession) {
        ImGui.setNextWindowPos(caretScreenX + 40f, caretScreenY)
        val flags = ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoSavedSettings or
            ImGuiWindowFlags.NoFocusOnAppearing or ImGuiWindowFlags.NoNav or ImGuiWindowFlags.NoMove
        if (ImGui.begin("##script_completion", flags)) {
            val shown = active.filtered.take(MAX_ROWS)
            val first = (active.selected - MAX_ROWS + 1).coerceAtLeast(0)
            val rows = active.filtered.drop(first).take(MAX_ROWS)
            rows.forEachIndexed { row, c ->
                val index = first + row
                val label = "${icon(c.kind)} ${c.label}${c.type?.let { "  : $it" }.orEmpty()}##c$index"
                if (ImGui.selectable(label, index == active.selected)) {
                    applyByClick(doc, active, index)
                }
            }
            active.current()?.let { c ->
                if (c.signature != null || c.documentation != null) {
                    ImGui.separator()
                    c.signature?.let { ImGui.textColored(0.6f, 0.8f, 1f, 1f, it) }
                    c.documentation?.let { ImGui.textWrapped(it) }
                }
            }
            if (shown.isEmpty()) ImGui.textDisabled(strings.getString("lbl.script_editor.no_completions"))
        }
        ImGui.end()
    }

    /** Mouse selection: edits the buffer directly and puts the caret back through the next input callback. */
    private fun applyByClick(doc: ScriptDocument, active: CompletionSession, index: Int) {
        while (active.selected != index) active.move(1)
        val edit = active.accept(cursorPos) ?: return
        val updated = edit.apply(doc.text)
        buffer.set(updated)
        doc.edit(updated)
        pendingCaret = edit.caretAfter
        refocus = true
        session = null
    }

    private fun icon(kind: CompletionKind) = when (kind) {
        CompletionKind.PROPERTY -> "[P]"
        CompletionKind.METHOD -> "[M]"
        CompletionKind.VARIABLE -> "[V]"
        CompletionKind.KEYWORD -> "[K]"
        CompletionKind.SNIPPET -> "[S]"
    }

    private fun parameterHint(doc: ScriptDocument, dl: imgui.ImDrawList, x: Float, y: Float) {
        if (session != null) return
        val hint = ParameterHints.at(doc.text, cursorPos, signatures) ?: return
        val text = hint.functionName + "(" + hint.parameters.mapIndexed { i, p -> if (i == hint.activeIndex) "[$p]" else p }.joinToString(", ") + ")"
        val w = ImGui.calcTextSize(text).x
        val h = ImGui.getTextLineHeight()
        dl.addRectFilled(x - 2f, y - h - 4f, x + w + 4f, y - 2f, ImGui.colorConvertFloat4ToU32(0.12f, 0.12f, 0.16f, 0.95f))
        dl.addText(x, y - h - 3f, SyntaxColors.plain, text)
    }

    private fun drawText(dl: imgui.ImDrawList, x: Float, y: Float, text: String, color: Int): Float {
        if (text.isEmpty()) return x
        dl.addText(x, y, color, text)
        return x + ImGui.calcTextSize(text).x
    }

    private fun diagnosticsList(doc: ScriptDocument) {
        ImGui.separator()
        if (doc.diagnostics.isEmpty()) {
            ImGui.textDisabled(strings.getString("lbl.script_editor.no_errors"))
            return
        }
        ImGui.text(strings.getString("lbl.script_editor.errors", doc.diagnostics.size))
        ImGui.beginChild("##script_errors", 0f, 0f, true)
        doc.diagnostics.forEachIndexed { index, d ->
            val where = d.line?.let { ":$it" + (d.column?.let { c -> ":$c" } ?: "") }.orEmpty()
            val tag = strings.getString(if (d.source == DiagnosticSource.COMPILE) "lbl.script_editor.compile" else "lbl.script_editor.runtime")
            if (ImGui.selectable("[$tag] ${doc.name}$where  ${d.message.lineSequence().first()}##err$index", false) && d.line != null) {
                jumpToLine = d.line
            }
        }
        ImGui.endChild()
    }

    private object SyntaxColors {
        val plain = ImGui.colorConvertFloat4ToU32(0.86f, 0.86f, 0.86f, 1f)
        val keyword = ImGui.colorConvertFloat4ToU32(0.80f, 0.47f, 0.76f, 1f)
        val string = ImGui.colorConvertFloat4ToU32(0.60f, 0.78f, 0.45f, 1f)
        val comment = ImGui.colorConvertFloat4ToU32(0.45f, 0.52f, 0.45f, 1f)
        val number = ImGui.colorConvertFloat4ToU32(0.82f, 0.68f, 0.40f, 1f)
        val annotation = ImGui.colorConvertFloat4ToU32(0.90f, 0.80f, 0.40f, 1f)
        val lineNumber = ImGui.colorConvertFloat4ToU32(0.5f, 0.5f, 0.5f, 1f)
        val errorText = ImGui.colorConvertFloat4ToU32(1f, 0.35f, 0.35f, 1f)
        val errorLine = ImGui.colorConvertFloat4ToU32(0.8f, 0.1f, 0.1f, 0.18f)
        val caret = ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f)

        fun of(kind: TokenKind) = when (kind) {
            TokenKind.KEYWORD -> keyword
            TokenKind.STRING -> string
            TokenKind.COMMENT -> comment
            TokenKind.NUMBER -> number
            TokenKind.ANNOTATION -> annotation
            TokenKind.PLAIN -> plain
        }
    }

    companion object {
        private const val BUFFER_SIZE = 1 shl 20
        private const val MAX_ROWS = 10
    }
}
