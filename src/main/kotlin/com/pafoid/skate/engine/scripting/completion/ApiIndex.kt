package com.pafoid.skate.engine.scripting.completion

import com.pafoid.skate.engine.scripting.api.ScriptContext
import com.pafoid.skate.engine.scripting.api.ScriptEntity
import com.pafoid.skate.engine.scripting.api.ScriptEvents
import com.pafoid.skate.engine.scripting.api.ScriptInput
import com.pafoid.skate.engine.scripting.api.ScriptLog
import com.pafoid.skate.engine.scripting.api.ScriptPhysics
import com.pafoid.skate.engine.scripting.api.ScriptRigidBody
import com.pafoid.skate.engine.scripting.api.ScriptScene
import com.pafoid.skate.engine.scripting.api.ScriptTime
import com.pafoid.skate.engine.scripting.api.ScriptTransform
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KType

data class ApiMember(
    val name: String,
    val kind: CompletionKind,
    /** Name of the [ApiType] a property/method result has, or null when unknown or primitive. */
    val type: String?,
    val signature: String,
    val doc: String?,
)

data class ApiType(val name: String, val members: List<ApiMember>) {
    fun member(name: String): ApiMember? = members.firstOrNull { it.name == name }
}

/**
 * Index of the public script API. Built by reflection over the script API interfaces so it cannot drift from the
 * API; curated docs are attached by `Type.member` key. Types are looked up by simple name.
 */
class ApiIndex(types: Collection<ApiType>, val roots: Map<String, String> = mapOf("ctx" to "ScriptContext")) {
    private val byName = types.associateBy { it.name }

    val types: Collection<ApiType> get() = byName.values

    fun type(name: String?): ApiType? = name?.let { byName[it] }

    fun withType(type: ApiType): ApiIndex = ApiIndex(byName.values.filter { it.name != type.name } + type, roots)

    fun withoutMembers(typeName: String, vararg names: String): ApiIndex {
        val t = byName[typeName] ?: return this
        return withType(t.copy(members = t.members.filter { it.name !in names }))
    }

    companion object {
        private val API: List<KClass<*>> = listOf(
            ScriptContext::class, ScriptTransform::class, ScriptEntity::class, ScriptRigidBody::class,
            ScriptScene::class, ScriptInput::class, ScriptPhysics::class, ScriptEvents::class,
            ScriptTime::class, ScriptLog::class,
        )

        private val PACKAGE_PREFIX = Regex("\\b(?:[a-z_][a-z0-9_]*\\.)+(?=[A-Z])")
        private val OBJECT_METHODS = setOf("equals", "hashCode", "toString")

        private fun render(t: KType) = t.toString().replace(PACKAGE_PREFIX, "")

        private fun typeName(t: KType): String? =
            if (t.toString().startsWith("kotlin.collections.MutableMap")) "MutableMap" else (t.classifier as? KClass<*>)?.simpleName

        fun fromReflection(): ApiIndex =
            ApiIndex(API.map { build(it) } + vector() + mapType("Map", mutable = false) + mapType("MutableMap", mutable = true))

        private fun build(kc: KClass<*>): ApiType {
            val owner = kc.simpleName!!
            val members = mutableListOf<ApiMember>()
            kc.members.filterIsInstance<KProperty1<*, *>>().forEach { p ->
                val kw = if (p is KMutableProperty<*>) "var" else "val"
                members += ApiMember(
                    p.name, CompletionKind.PROPERTY, typeName(p.returnType),
                    "$kw ${p.name}: ${render(p.returnType)}", Docs.of(owner, p.name),
                )
            }
            kc.members.filterIsInstance<KFunction<*>>().filter { it.name !in OBJECT_METHODS }.forEach { f ->
                val params = f.parameters.drop(1).joinToString(", ") { "${it.name}: ${render(it.type)}" }
                val generics = f.typeParameters.takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = "<", postfix = "> ") { tp ->
                        tp.name + (tp.upperBounds.firstOrNull()?.let { " : ${render(it)}" } ?: "")
                    }.orEmpty()
                members += ApiMember(
                    f.name, CompletionKind.METHOD, typeName(f.returnType),
                    "fun $generics${f.name}($params): ${render(f.returnType)}", Docs.of(owner, f.name),
                )
            }
            return ApiType(owner, members.sortedBy { it.name })
        }

        /** Scripts see JOML's Vector3f; only the commonly used, safe subset is suggested. */
        private fun vector() = ApiType(
            "Vector3f",
            listOf(
                ApiMember("x", CompletionKind.PROPERTY, null, "var x: Float", Docs.of("Vector3f", "x")),
                ApiMember("y", CompletionKind.PROPERTY, null, "var y: Float", Docs.of("Vector3f", "y")),
                ApiMember("z", CompletionKind.PROPERTY, null, "var z: Float", Docs.of("Vector3f", "z")),
                ApiMember("length", CompletionKind.METHOD, null, "fun length(): Float", Docs.of("Vector3f", "length")),
                ApiMember("normalize", CompletionKind.METHOD, "Vector3f", "fun normalize(): Vector3f", Docs.of("Vector3f", "normalize")),
                ApiMember("add", CompletionKind.METHOD, "Vector3f", "fun add(v: Vector3f): Vector3f", Docs.of("Vector3f", "add")),
                ApiMember("sub", CompletionKind.METHOD, "Vector3f", "fun sub(v: Vector3f): Vector3f", Docs.of("Vector3f", "sub")),
                ApiMember("mul", CompletionKind.METHOD, "Vector3f", "fun mul(scalar: Float): Vector3f", Docs.of("Vector3f", "mul")),
                ApiMember("dot", CompletionKind.METHOD, null, "fun dot(v: Vector3f): Float", Docs.of("Vector3f", "dot")),
            ),
        )

        private fun mapType(name: String, mutable: Boolean): ApiType {
            val members = mutableListOf(
                ApiMember("get", CompletionKind.METHOD, null, "operator fun get(key: String): Any?", Docs.of("Map", "get")),
                ApiMember("containsKey", CompletionKind.METHOD, null, "fun containsKey(key: String): Boolean", Docs.of("Map", "containsKey")),
            )
            if (mutable) {
                members += ApiMember("put", CompletionKind.METHOD, null, "fun put(key: String, value: Any?): Any?", Docs.of("Map", "put"))
            }
            return ApiType(name, members)
        }
    }
}

internal object Docs {
    private val docs = mapOf(
        "ScriptContext.entity" to "The entity this script is attached to.",
        "ScriptContext.scene" to "Find other entities in the running scene.",
        "ScriptContext.input" to "Keyboard, mouse and named-action input.",
        "ScriptContext.physics" to "Physics queries such as gravity and raycasts.",
        "ScriptContext.events" to "String-keyed event bus shared by all scripts.",
        "ScriptContext.time" to "Frame timing.",
        "ScriptContext.log" to "Writes to the editor console (Engine tab).",
        "ScriptContext.properties" to "Inspector-exposed string values stored on the script component.",
        "ScriptContext.state" to "Values that survive hot reloads of this script instance.",
        "ScriptContext.reloadCount" to "How many times this script was hot reloaded; 0 on first creation.",
        "ScriptTransform.position" to "World position, mutable and backed by the entity's transform.",
        "ScriptTransform.rotation" to "Euler angles in degrees.",
        "ScriptTransform.scale" to "Scale per axis.",
        "ScriptEntity.id" to "Unique id of the entity.",
        "ScriptEntity.name" to "Entity name (editable).",
        "ScriptEntity.transform" to "The entity's transform.",
        "ScriptEntity.getComponent" to "Returns a whitelisted component facade (e.g. RigidBody) or null when the entity lacks it.",
        "ScriptEntity.hasComponent" to "True when the entity has the given component.",
        "ScriptEntity.component" to "Looks a component facade up by name (e.g. \"RigidBody\"); null when absent.",
        "ScriptRigidBody.mass" to "Mass in kilograms.",
        "ScriptRigidBody.linearVelocity" to "Linear velocity in m/s.",
        "ScriptRigidBody.angularVelocity" to "Angular velocity in rad/s.",
        "ScriptScene.find" to "First entity with the given name, or null.",
        "ScriptScene.findAll" to "All entities with the given name.",
        "ScriptInput.isKeyDown" to "True while the key (GLFW key code) is held.",
        "ScriptInput.isKeyPressed" to "True on the frame the key was pressed.",
        "ScriptInput.isKeyReleased" to "True on the frame the key was released.",
        "ScriptInput.isMouseButtonDown" to "True while the mouse button is held.",
        "ScriptInput.action" to "Value of a named input action in [-1, 1]; 0 when unknown.",
        "ScriptPhysics.gravity" to "World gravity vector.",
        "ScriptPhysics.raycast" to "Distance to the closest hit along from->to, or null.",
        "ScriptEvents.on" to "Subscribes to a custom event; removed when the script is destroyed.",
        "ScriptEvents.emit" to "Raises a custom event with an optional payload.",
        "ScriptTime.deltaTime" to "Seconds since the previous frame.",
        "ScriptTime.fixedDeltaTime" to "Fixed step length in seconds.",
        "ScriptTime.elapsed" to "Seconds since the script started.",
        "ScriptLog.info" to "Logs an informational message.",
        "ScriptLog.warn" to "Logs a warning.",
        "ScriptLog.error" to "Logs an error.",
        "Vector3f.x" to "X component.",
        "Vector3f.y" to "Y component.",
        "Vector3f.z" to "Z component.",
        "Vector3f.length" to "Euclidean length.",
        "Vector3f.normalize" to "Scales to unit length (in place).",
        "Vector3f.add" to "Adds a vector (in place).",
        "Vector3f.sub" to "Subtracts a vector (in place).",
        "Vector3f.mul" to "Multiplies by a scalar (in place).",
        "Vector3f.dot" to "Dot product.",
        "Map.get" to "Value for the key, or null.",
        "Map.containsKey" to "True when the key exists.",
        "Map.put" to "Stores a value under the key.",
    )

    fun of(type: String, member: String): String? = docs["$type.$member"]
}
