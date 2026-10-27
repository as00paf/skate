package com.pafoid.skate.engine.assets.serialization

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Kinds of versioned data files. Files without a `schema` field are version 0. */
enum class FileKind(val currentSchema: Int) {
    SCENE(1),
    PREFAB(1),
    PROJECT(1)
}

class UnsupportedSchemaException(kind: FileKind, found: Int) :
    IllegalStateException("$kind file has schema $found, newer than supported ${kind.currentSchema}")

/** A step that upgrades a JSON document from schema `from` to `from + 1`. */
fun interface Migration {
    fun migrate(root: JsonObject): JsonObject
}

class SchemaMigrations(
    private val migrations: Map<FileKind, Map<Int, Migration>> = DEFAULT
) {
    fun schemaOf(root: JsonObject): Int = root[SCHEMA_KEY]?.jsonPrimitive?.int ?: 0

    fun stamp(kind: FileKind, root: JsonObject): JsonObject =
        JsonObject(mapOf(SCHEMA_KEY to JsonPrimitive(kind.currentSchema)) + root.filterKeys { it != SCHEMA_KEY })

    /** Runs every migration from the file's schema up to the current one. */
    fun upgrade(kind: FileKind, element: JsonElement, target: Int = kind.currentSchema): JsonObject {
        var root = element.jsonObject
        var version = schemaOf(root)
        if (version > target) throw UnsupportedSchemaException(kind, version)
        while (version < target) {
            val step = migrations[kind]?.get(version)
                ?: throw IllegalStateException("No $kind migration from schema $version")
            root = step.migrate(root)
            version++
            root = JsonObject(root + (SCHEMA_KEY to JsonPrimitive(version)))
        }
        return root
    }

    companion object {
        const val SCHEMA_KEY = "schema"

        // v0 -> v1 only introduces the schema field; the layout is unchanged.
        val DEFAULT: Map<FileKind, Map<Int, Migration>> = FileKind.values().associateWith {
            mapOf(0 to Migration { root -> root })
        }
    }
}
