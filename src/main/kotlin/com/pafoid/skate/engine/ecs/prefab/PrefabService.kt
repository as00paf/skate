package com.pafoid.skate.engine.ecs.prefab

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.EcsChanges
import com.pafoid.skate.engine.ecs.GameObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Immutable prefab template: the serialized [GameObject] tree, without [PrefabInstance] markers. */
class PrefabAsset(val id: String, val template: JsonObject)

/**
 * Override addressing: `nodePath|component|property`.
 * - nodePath: `""` for the root, otherwise `name#ordinal` segments joined by `/` (ordinal among same-named siblings).
 * - component: `TypeName#ordinal` (ordinal among components of that type), or [OBJECT] for the GameObject's own fields.
 * - property: top-level serialized property name.
 */
object PrefabOverrideKey {
    const val OBJECT = "@object"
    fun of(nodePath: String, component: String, property: String) = "$nodePath|$component|$property"
    fun parse(key: String): Triple<String, String, String> {
        val p = key.split('|')
        require(p.size == 3) { "Invalid override key '$key'" }
        return Triple(p[0], p[1], p[2])
    }
}

/** Result of [PrefabService.update]; [droppedOverrides] targeted nodes/components removed from the prefab. */
class PrefabUpdateResult(val droppedOverrides: List<String>)

class PrefabService(private val serializer: Serializer) {

    private companion object {
        val OBJECT_IGNORED = setOf("uId", "isDead", "children", "components")
        val COMPONENT_IGNORED = setOf("uId")
        const val TYPE_KEY = "type"
    }

    fun createAsset(id: String, source: GameObject): PrefabAsset =
        PrefabAsset(id, stripMarkers(encode(source)))

    /** Creates a new instance of [asset] (fresh ids) with the given [overrides] applied on top of the template. */
    fun instantiate(asset: PrefabAsset, overrides: Map<String, JsonElement> = emptyMap()): GameObject {
        val (json, _) = merge(asset.template, overrides)
        val go = decode(json)
        initTree(go)
        val marker = PrefabInstance(asset.id, overrides.toMutableMap())
        go.components.add(marker)
        marker.init(go)
        return go
    }

    /** Recomputes the instance's overrides by diffing its current state against [asset]. */
    fun captureOverrides(instance: GameObject, asset: PrefabAsset): Map<String, JsonElement> {
        val out = LinkedHashMap<String, JsonElement>()
        diff(asset.template, stripMarkers(encode(instance)), "", out)
        instance.marker().overrides.apply { clear(); putAll(out) }
        return out
    }

    fun setOverride(instance: GameObject, key: String, value: JsonElement) {
        instance.marker().overrides[key] = value
    }

    /** Removes the override so the property follows the prefab again (applied on the next [update]). */
    fun revertOverride(instance: GameObject, key: String) {
        instance.marker().overrides.remove(key)
    }

    /**
     * Rebuilds [instance] in place from the (possibly edited) [asset] with its overrides re-applied.
     * The instance keeps its identity, uId, parent and sibling position.
     */
    fun update(instance: GameObject, asset: PrefabAsset): PrefabUpdateResult {
        val marker = instance.marker()
        val (json, dropped) = merge(asset.template, marker.overrides)
        dropped.forEach { marker.overrides.remove(it) }
        marker.prefabId = asset.id

        val fresh = decode(json)
        initTree(fresh)

        instance.children.toList().forEach { it.destroy(); instance.removeChild(it) }
        instance.components.filter { it !== marker }.forEach { it.destroy() }
        instance.components.removeAll { it !== marker }

        instance.name = fresh.name
        instance.isEnabled = fresh.isEnabled
        instance.isVisible = fresh.isVisible
        instance.isLocked = fresh.isLocked
        fresh.components.toList().forEach { instance.components.add(it); it.init(instance) }
        fresh.children.toList().forEach { instance.addChild(it) }
        EcsChanges.touch()
        return PrefabUpdateResult(dropped)
    }

    private fun GameObject.marker(): PrefabInstance =
        components.filterIsInstance<PrefabInstance>().firstOrNull()
            ?: error("GameObject '$name' is not a prefab instance")

    private fun encode(go: GameObject): JsonObject =
        serializer.json.encodeToJsonElement(GameObject.serializer(), go) as JsonObject

    private fun decode(json: JsonObject): GameObject =
        serializer.json.decodeFromJsonElement(GameObject.serializer(), json)

    private fun initTree(go: GameObject) {
        go.generateUid()
        go.components.forEach { it.generateId(); it.init(go) }
        go.children.forEach { it.parent = go; initTree(it) }
    }

    private fun typeName(component: JsonObject) =
        component[TYPE_KEY]?.jsonPrimitive?.content?.substringAfterLast('.') ?: "Component"

    private fun components(node: JsonObject): List<JsonObject> =
        (node["components"] as? JsonArray)?.map { it.jsonObject } ?: emptyList()

    private fun children(node: JsonObject): List<JsonObject> =
        (node["children"] as? JsonArray)?.map { it.jsonObject } ?: emptyList()

    /** `name#ordinal` keys for [items], ordinal counted per [keyOf] value. */
    private fun keyed(items: List<JsonObject>, keyOf: (JsonObject) -> String): List<Pair<String, JsonObject>> {
        val counts = HashMap<String, Int>()
        return items.map {
            val k = keyOf(it)
            val n = counts.merge(k, 1, Int::plus)!! - 1
            "$k#$n" to it
        }
    }

    private fun nodeName(n: JsonObject) = n["name"]?.jsonPrimitive?.content ?: ""

    private fun stripMarkers(node: JsonObject): JsonObject {
        val comps = components(node).filter { typeName(it) != "PrefabInstance" }
        val kids = children(node).map { stripMarkers(it) }
        return JsonObject(node + mapOf("components" to JsonArray(comps), "children" to JsonArray(kids)))
    }

    private fun diff(base: JsonObject, current: JsonObject, path: String, out: MutableMap<String, JsonElement>) {
        for ((k, v) in current) {
            if (k !in OBJECT_IGNORED && base[k] != v) out[PrefabOverrideKey.of(path, PrefabOverrideKey.OBJECT, k)] = v
        }
        val baseComps = keyed(components(base), ::typeName).toMap()
        for ((ck, comp) in keyed(components(current), ::typeName)) {
            val b = baseComps[ck] ?: continue
            for ((k, v) in comp) {
                if (k !in COMPONENT_IGNORED && b[k] != v) out[PrefabOverrideKey.of(path, ck, k)] = v
            }
        }
        val baseKids = keyed(children(base), ::nodeName).toMap()
        for ((nk, kid) in keyed(children(current), ::nodeName)) {
            val b = baseKids[nk] ?: continue
            diff(b, kid, if (path.isEmpty()) nk else "$path/$nk", out)
        }
    }

    /** Applies [overrides] onto [template]; returns the merged tree and the keys that matched nothing. */
    private fun merge(template: JsonObject, overrides: Map<String, JsonElement>): Pair<JsonObject, List<String>> {
        val unused = overrides.keys.toMutableSet()
        val parsed = overrides.entries.groupBy({ PrefabOverrideKey.parse(it.key).first }) { it }

        fun walk(node: JsonObject, path: String): JsonObject {
            val here = parsed[path].orEmpty()
            val props = LinkedHashMap<String, JsonElement>(node)
            here.filter { PrefabOverrideKey.parse(it.key).second == PrefabOverrideKey.OBJECT }.forEach {
                props[PrefabOverrideKey.parse(it.key).third] = it.value
                unused.remove(it.key)
            }
            val comps = keyed(components(node), ::typeName).map { (ck, comp) ->
                val cp = LinkedHashMap<String, JsonElement>(comp)
                here.filter { PrefabOverrideKey.parse(it.key).second == ck }.forEach {
                    cp[PrefabOverrideKey.parse(it.key).third] = it.value
                    unused.remove(it.key)
                }
                JsonObject(cp)
            }
            val kids = keyed(children(node), ::nodeName).map { (nk, kid) ->
                walk(kid, if (path.isEmpty()) nk else "$path/$nk")
            }
            props["components"] = JsonArray(comps)
            props["children"] = JsonArray(kids)
            return JsonObject(props)
        }

        return walk(template, "") to unused.toList()
    }
}
