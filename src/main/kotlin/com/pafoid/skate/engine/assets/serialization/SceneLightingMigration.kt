package com.pafoid.skate.engine.assets.serialization

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Scene schema 1 -> 2 (REN-26): ambient light becomes an environment setting and the sun becomes an entity.
 *
 * - The scene's `AmbientLightComponent` is removed; its `lightColor`/`intensity` become the
 *   `EnvironmentComponent`'s `ambientColor`/`ambientIntensity` (an environment is added when the scene had none).
 * - A `DirectionalLightComponent` on the scene itself moves to a new top-level "Sun" entity at (0, 10, 0), so it is no
 *   longer drawn at the origin.
 */
object SceneLightingMigration : Migration {
    private const val PKG = "com.pafoid.skate.engine.ecs.components."
    const val AMBIENT = PKG + "AmbientLightComponent"
    const val ENVIRONMENT = PKG + "EnvironmentComponent"
    const val DIRECTIONAL = PKG + "DirectionalLightComponent"
    const val TRANSFORM = PKG + "Transform"
    const val SUN_NAME = "Sun"

    override fun migrate(root: JsonObject): JsonObject {
        val components = root["components"]?.jsonArray ?: return root
        val ambient = components.firstOrNull { it.type() == AMBIENT }?.jsonObject
        val sun = components.firstOrNull { it.type() == DIRECTIONAL }?.jsonObject
        if (ambient == null && sun == null) return root

        var kept = components.filter { it.type() != AMBIENT && it.type() != DIRECTIONAL }
        if (ambient != null) {
            val ambientFields = buildMap<String, JsonElement> {
                ambient["lightColor"]?.let { put("ambientColor", it) }
                ambient["intensity"]?.let { put("ambientIntensity", it) }
                ambient["enabled"]?.jsonPrimitive?.content?.let { if (it == "false") put("ambientIntensity", JsonPrimitive(0f)) }
            }
            kept = if (kept.any { it.type() == ENVIRONMENT }) {
                kept.map { if (it.type() == ENVIRONMENT) JsonObject(it.jsonObject + ambientFields) else it }
            } else {
                kept + JsonObject(mapOf("type" to JsonPrimitive(ENVIRONMENT)) + ambientFields)
            }
        }

        var children = root["children"]?.jsonArray?.toList() ?: emptyList()
        if (sun != null) children = children + sunEntity(sun)
        return JsonObject(root + mapOf("components" to JsonArray(kept), "children" to JsonArray(children)))
    }

    private fun sunEntity(light: JsonObject): JsonObject = buildJsonObject {
        put("name", SUN_NAME)
        put("components", JsonArray(listOf(
            buildJsonObject {
                put("type", TRANSFORM)
                put("translation", buildJsonObject { put("x", 0f); put("y", 10f); put("z", 0f) })
            },
            JsonObject(light.filterKeys { it != "uId" }),
        )))
        put("children", JsonArray(emptyList()))
    }

    private fun JsonElement.type(): String? = (this as? JsonObject)?.get("type")?.jsonPrimitive?.content
}
