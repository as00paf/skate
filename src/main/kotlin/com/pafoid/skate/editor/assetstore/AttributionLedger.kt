package com.pafoid.skate.editor.assetstore

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

@Serializable
data class LedgerAuthor(val name: String, val url: String? = null)

@Serializable
data class LedgerLicense(val spdx: String, val url: String = "")

/** One imported store item and the GUIDs of every file it put in the project (AST-16 §6.2). */
@Serializable
data class LedgerEntry(
    val assetIds: List<String>,
    val provider: String,
    val sourceId: String,
    val revision: String? = null,
    val variant: String? = null,
    val title: String,
    val sourceUrl: String,
    val authors: List<LedgerAuthor> = emptyList(),
    val license: LedgerLicense,
    val modifications: List<String> = emptyList(),
    val retrievedAt: String,
    /** Import folder, project-relative with `/`; lets undo and re-import find the files without the registry. */
    val folder: String = "",
)

/** `<project>/.skate/attribution.json`: committed with the project, never scanned into the `AssetRegistry`. */
@Serializable
data class AttributionLedger(val schema: Int = CURRENT_SCHEMA, val entries: List<LedgerEntry> = emptyList()) {

    fun find(provider: String, sourceId: String): LedgerEntry? = entries.firstOrNull { it.provider == provider && it.sourceId == sourceId }

    /** Replaces the entry for the same (provider, sourceId), or appends. */
    fun upsert(entry: LedgerEntry): AttributionLedger =
        copy(entries = entries.filterNot { it.provider == entry.provider && it.sourceId == entry.sourceId } + entry)

    fun remove(provider: String, sourceId: String): AttributionLedger =
        copy(entries = entries.filterNot { it.provider == provider && it.sourceId == sourceId })

    /** Drops [ids] from every entry and removes entries left without files (asset deleted in the editor). */
    fun removeAssetIds(ids: Set<String>): AttributionLedger =
        copy(entries = entries.map { it.copy(assetIds = it.assetIds - ids) }.filter { it.assetIds.isNotEmpty() })

    /** Entries with at least one file in [ids] (credits for a packed export, AST-16 §6.2). */
    fun entriesFor(ids: Set<String>): List<LedgerEntry> = entries.filter { e -> e.assetIds.any { it in ids } }

    companion object {
        const val CURRENT_SCHEMA = 1
        const val FILE_NAME = "attribution.json"

        private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

        fun file(projectDir: File): File = File(projectDir, "${StorePaths.PROJECT_HIDDEN_DIR}/$FILE_NAME")

        fun encode(ledger: AttributionLedger): String = json.encodeToString(serializer(), ledger)

        fun decode(text: String): AttributionLedger {
            val element = json.parseToJsonElement(text)
            val schema = (element as? JsonObject)?.get("schema")?.jsonPrimitive?.int ?: CURRENT_SCHEMA
            return json.decodeFromJsonElement(serializer(), migrate(schema, element))
        }

        /** Upgrade hook mirroring `SchemaMigrations`; empty until there is a schema 2 (rule 8: no upgrade code yet). */
        fun migrate(schema: Int, element: JsonElement): JsonElement {
            require(schema <= CURRENT_SCHEMA) { "Attribution ledger schema $schema is newer than supported $CURRENT_SCHEMA" }
            return element
        }

        fun load(projectDir: File): AttributionLedger {
            val f = file(projectDir)
            return if (f.isFile) decode(f.readText()) else AttributionLedger()
        }

        fun save(projectDir: File, ledger: AttributionLedger) {
            StorePaths.ensureHiddenDir(projectDir)
            file(projectDir).writeText(encode(ledger))
        }
    }
}
