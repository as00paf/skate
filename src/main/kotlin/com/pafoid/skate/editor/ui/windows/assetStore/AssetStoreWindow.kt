package com.pafoid.skate.editor.ui.windows.assetStore

import com.pafoid.skate.editor.assetstore.AssetStoreRegistry
import com.pafoid.skate.editor.assetstore.AssetStoreService
import com.pafoid.skate.editor.assetstore.JdkStoreHttp
import com.pafoid.skate.editor.assetstore.LicenseBadge
import com.pafoid.skate.editor.assetstore.LicenseFilter
import com.pafoid.skate.editor.assetstore.StoreAssetDetails
import com.pafoid.skate.editor.assetstore.StoreAssetKind
import com.pafoid.skate.editor.assetstore.StoreAssetSummary
import com.pafoid.skate.editor.assetstore.StoreJobs
import com.pafoid.skate.editor.assetstore.providers.polyhaven.PolyHavenProvider
import com.pafoid.skate.editor.events.AssetStoreEvent
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.ImGuiIds
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiTableFlags
import imgui.type.ImString
import java.awt.Desktop
import java.net.URI
import kotlin.math.max

/**
 * Window > Asset Store (AST-16): search free asset libraries, preview, read the license, import into
 * `Assets/Store/<provider>/<slug>/`. Imports are published as events and run as undoable commands.
 */
class AssetStoreWindow(
    private val engine: Engine,
    private val projectManager: ProjectManager,
) : EditorWindow(NAME) {
    private val strings = engine.stringManager

    private val service: AssetStoreService by lazy {
        val http = JdkStoreHttp()
        val jobs = object : StoreJobs {
            override fun io(block: () -> Unit) {
                engine.jobSystem.runIO { block() }
            }

            override fun main(block: () -> Unit) {
                engine.jobSystem.runOnMain { block() }
            }
        }
        AssetStoreService(
            AssetStoreRegistry(listOf(PolyHavenProvider(http))),
            http, jobs, engine.eventSystem::publish,
            projectDir = { projectManager.getProjectDirectory()?.absoluteFile },
        ).also { it.refreshLedger() }
    }

    private val searchText = ImString(128)
    private var variantIndex = 0
    private var started = false

    init {
        engine.eventSystem.subscribe<AssetStoreEvent.ImportFinished> { event -> if (started) service.onImportFinished(event) }
    }

    override fun imgui() {
        ImGui.begin(title(strings))
        if (!projectManager.hasProject()) {
            EmptyState.render(strings, Icons.FOLDER, "assetstore.empty.no_project")
            ImGui.end()
            return
        }
        if (!started) {
            started = true
            service.search(service.query)
        }
        toolbar()
        status()
        if (ImGui.beginTabBar("##assetstore_tabs")) {
            if (ImGui.beginTabItem(strings.label("assetstore.tab.browse"))) {
                browse()
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem(strings.label("assetstore.tab.attribution"))) {
                attribution()
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }
        ImGui.end()
    }

    private fun toolbar() {
        val provider = service.provider
        ImGui.setNextItemWidth(160f)
        if (ImGui.beginCombo(strings.label("assetstore.provider"), strings.getString(provider.displayNameKey))) {
            service.registry.providers.forEach { p ->
                if (ImGui.selectable(strings.getString(p.displayNameKey), p.id == provider.id)) service.selectProvider(p.id)
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        ImGui.setNextItemWidth(140f)
        if (ImGui.beginCombo(strings.label("assetstore.kind"), strings.getString(kindKey(service.query.kind)))) {
            provider.kinds.forEach { kind ->
                if (ImGui.selectable(strings.getString(kindKey(kind)), kind == service.query.kind)) {
                    service.search(service.query.copy(kind = kind))
                }
            }
            ImGui.endCombo()
        }
        ImGui.sameLine()
        ImGui.setNextItemWidth(180f)
        if (ImGui.beginCombo(strings.label("assetstore.license"), strings.getString(service.query.licenseFilter.key))) {
            LicenseFilter.entries.forEach { filter ->
                if (ImGui.selectable(strings.getString(filter.key), filter == service.query.licenseFilter)) {
                    service.search(service.query.copy(licenseFilter = filter))
                }
            }
            ImGui.endCombo()
        }
        ImGui.setNextItemWidth(max(120f, ImGui.getContentRegionAvailX() - 110f))
        val enter = ImGui.inputTextWithHint("##assetstore_search", strings.getString("assetstore.search.hint"), searchText, ImGuiInputTextFlags.EnterReturnsTrue)
        ImGui.sameLine()
        if (strings.tipped(ImGui.button(strings.label("assetstore.search", "${Icons.SEARCH} ")), "assetstore.search") || enter) {
            service.search(service.query.copy(text = searchText.get()))
        }
        provider.poweredByKey?.let { ImGui.textDisabled(strings.getString(it)) }
    }

    private fun status() {
        service.download?.let { dl ->
            ImGui.progressBar(dl.fraction, max(100f, ImGui.getContentRegionAvailX() - 110f), 0f, strings.getString("assetstore.status.downloading", dl.title))
            ImGui.sameLine()
            if (strings.tipped(ImGui.button(strings.label("assetstore.cancel")), "assetstore.cancel")) service.cancelDownload()
        }
        service.downloadError?.let { ImGui.textColored(1f, 0.4f, 0.4f, 1f, strings.getString("assetstore.status.download_failed", it)) }
        service.lastImport?.let { last ->
            if (last.success) ImGui.textColored(0.4f, 0.9f, 0.4f, 1f, strings.getString("assetstore.status.imported", last.title))
            else ImGui.textColored(1f, 0.4f, 0.4f, 1f, strings.getString("assetstore.status.import_failed", last.title, last.reason ?: ""))
        }
    }

    private fun browse() {
        val detailsWidth = 320f
        val gridWidth = max(150f, ImGui.getContentRegionAvailX() - detailsWidth - ImGui.getStyle().itemSpacingX)
        ImGui.beginChild("##assetstore_grid", gridWidth, 0f, false)
        grid()
        ImGui.endChild()
        ImGui.sameLine()
        ImGui.beginChild("##assetstore_details", 0f, 0f, true)
        detailsPane()
        ImGui.endChild()
    }

    private fun grid() {
        service.searchError?.let { ImGui.textColored(1f, 0.4f, 0.4f, 1f, strings.getString("assetstore.status.search_failed", it)) }
        val items = service.results
        if (items.isEmpty()) {
            if (service.searching) ImGui.textDisabled(strings.getString("assetstore.status.searching"))
            else if (service.searchError == null) EmptyState.render(strings, Icons.SEARCH, "empty.search", "empty.search.hint")
            return
        }
        val columns = max(1, (ImGui.getContentRegionAvailX() / TILE).toInt())
        if (ImGui.beginTable("##assetstore_table", columns, ImGuiTableFlags.SizingFixedFit)) {
            items.forEach { item ->
                ImGui.tableNextColumn()
                tile(item)
            }
            ImGui.endTable()
        }
        if (service.nextCursor != null) {
            if (strings.tipped(ImGui.button(strings.label("assetstore.load_more")), "assetstore.load_more")) service.loadMore()
        }
        service.total?.let { ImGui.textDisabled(strings.getString("assetstore.status.results", items.size, it)) }
    }

    private fun tile(item: StoreAssetSummary) {
        val size = TILE - 16f
        ImGui.pushID(item.ref.sourceId)
        ImGui.beginGroup()
        val thumb = service.thumbnail(item.thumbnailUrl)
        // Data label (the item title) as tooltip; a placeholder icon until the thumbnail is cached.
        val clicked = if (thumb != null) {
            ImGui.imageButton("##thumb", engine.assetsManager.getTexture(thumb.path).texId.toLong(), size, size, 0f, 0f, 1f, 1f)
        } else {
            ImGui.button("##thumb", size, size)
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(item.title)
        if (clicked) {
            variantIndex = 0
            service.select(item)
        }
        badge(item.license?.badge)
        ImGui.sameLine()
        ImGui.textWrapped(item.title)
        ImGui.endGroup()
        ImGui.popID()
    }

    private fun detailsPane() {
        val selected = service.selected ?: run {
            ImGui.textDisabled(strings.getString("assetstore.details.none"))
            return
        }
        ImGui.text(selected.title)
        if (service.detailsLoading) {
            ImGui.textDisabled(strings.getString("assetstore.status.loading"))
            return
        }
        service.detailsError?.let {
            ImGui.textColored(1f, 0.4f, 0.4f, 1f, strings.getString("assetstore.status.details_failed", it))
            return
        }
        val details = service.details ?: return
        service.thumbnail(selected.thumbnailUrl)?.let { thumb ->
            val w = ImGui.getContentRegionAvailX()
            ImGui.image(engine.assetsManager.getTexture(thumb.path).texId.toLong(), w, w, 0f, 0f, 1f, 1f)
        }
        if (details.authors.isNotEmpty()) ImGui.textWrapped(strings.getString("assetstore.details.authors", details.authors.joinToString { it.name }))
        licenseBlock(details)
        details.description?.let {
            ImGui.separator()
            ImGui.textWrapped(it)
        }
        ImGui.separator()
        importBlock(details)
        if (strings.tipped(ImGui.button(strings.label("assetstore.open_website", "${Icons.EXTERNAL_LINK} ")), "assetstore.open_website")) {
            browse(details.sourceUrl)
        }
    }

    private fun licenseBlock(details: StoreAssetDetails) {
        val license = details.license
        badge(license.badge)
        ImGui.sameLine()
        ImGui.text(license.name)
        ImGui.textWrapped(strings.getString(license.badge.key + ".hint"))
        if (license.url.isNotEmpty()) {
            if (strings.tipped(ImGui.smallButton(strings.label("assetstore.license.open", "${Icons.LINK} ")), "assetstore.license.open")) browse(license.url)
        }
        if (!license.derivatives) ImGui.textColored(1f, 0.75f, 0.3f, 1f, strings.getString("assetstore.license.nd_warning"))
    }

    private fun importBlock(details: StoreAssetDetails) {
        val variants = details.variants
        if (variants.isEmpty()) {
            ImGui.textDisabled(strings.getString("assetstore.details.no_variants"))
            return
        }
        variantIndex = variantIndex.coerceIn(0, variants.lastIndex)
        val names = variants.map { v -> "${v.resolution ?: v.id} (${megabytes(v.sizeBytes)})" }
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.6f)
        if (ImGui.beginCombo(strings.label("assetstore.variant"), names[variantIndex])) {
            names.forEachIndexed { i, name -> if (ImGui.selectable(name, i == variantIndex)) variantIndex = i }
            ImGui.endCombo()
        }
        val busy = service.download != null || !details.license.isKnown
        if (busy) ImGui.beginDisabled()
        if (strings.tipped(ImGui.button(strings.label("assetstore.import", "${Icons.IMPORT_FILE} ")), "assetstore.import")) {
            service.import(details, variants[variantIndex])
        }
        if (busy) ImGui.endDisabled()
    }

    private fun attribution() {
        val entries = service.ledger.entries
        if (entries.isEmpty()) {
            EmptyState.render(strings, Icons.INFO, "assetstore.attribution.empty")
            return
        }
        if (ImGui.beginTable("##assetstore_ledger", 4, ImGuiTableFlags.Borders or ImGuiTableFlags.RowBg or ImGuiTableFlags.Resizable)) {
            ImGui.tableSetupColumn(ImGuiIds.stable(strings.getString("assetstore.attribution.title"), "assetstore.attribution.title"))
            ImGui.tableSetupColumn(ImGuiIds.stable(strings.getString("assetstore.attribution.provider"), "assetstore.attribution.provider"))
            ImGui.tableSetupColumn(ImGuiIds.stable(strings.getString("assetstore.attribution.license"), "assetstore.attribution.license"))
            ImGui.tableSetupColumn(ImGuiIds.stable(strings.getString("assetstore.attribution.authors"), "assetstore.attribution.authors"))
            ImGui.tableHeadersRow()
            entries.forEach { e ->
                ImGui.tableNextRow()
                ImGui.tableNextColumn(); ImGui.text(e.title)
                ImGui.tableNextColumn(); ImGui.text(e.provider)
                ImGui.tableNextColumn(); ImGui.text(e.license.spdx)
                ImGui.tableNextColumn(); ImGui.text(e.authors.joinToString { it.name })
            }
            ImGui.endTable()
        }
    }

    private fun badge(badge: LicenseBadge?) {
        val (r, g, b) = when (badge) {
            LicenseBadge.FREE -> Triple(0.35f, 0.8f, 0.4f)
            LicenseBadge.ATTRIBUTION -> Triple(0.4f, 0.65f, 1f)
            LicenseBadge.CONDITIONS -> Triple(1f, 0.75f, 0.3f)
            LicenseBadge.RESTRICTED, null -> Triple(1f, 0.4f, 0.4f)
        }
        ImGui.textColored(r, g, b, 1f, Icons.STAR)
        if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString((badge ?: LicenseBadge.RESTRICTED).key))
    }

    private fun browse(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
            .onFailure { engine.logger.logEditor("Cannot open browser for $url: ${it.message}") }
    }

    private fun kindKey(kind: StoreAssetKind) = "assetstore.kind." + kind.name.lowercase()

    private fun megabytes(bytes: Long?): String = bytes?.let { "%.1f MB".format(it / 1_048_576.0) } ?: "?"

    companion object {
        const val NAME = "window.asset_store"
        private const val TILE = 128f
    }
}
