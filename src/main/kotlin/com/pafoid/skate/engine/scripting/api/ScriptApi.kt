package com.pafoid.skate.engine.scripting.api

import org.joml.Vector3f
import kotlin.reflect.KClass

/** Language-agnostic script lifecycle. Every host adapts its scripts to this interface. */
interface Script {
    fun onCreate(ctx: ScriptContext) {}
    fun onUpdate(ctx: ScriptContext, dt: Float) {}
    fun onFixedUpdate(ctx: ScriptContext, dt: Float) {}
    fun onDestroy(ctx: ScriptContext) {}
}

/** The only surface scripts may use. No GL, Bullet, ImGui or editor types appear here. */
interface ScriptContext {
    val entity: ScriptEntity
    val scene: ScriptScene
    val input: ScriptInput
    val physics: ScriptPhysics
    val events: ScriptEvents
    val time: ScriptTime
    val log: ScriptLog

    /** Inspector-exposed values stored on the script component. */
    val properties: Map<String, String>

    /** Survives hot reloads of this script instance; keep values worth preserving here. */
    val state: MutableMap<String, Any?>

    /** Number of times this script was hot reloaded; 0 on first creation. Check it in `onCreate`. */
    val reloadCount: Int
}

interface ScriptTransform {
    /** Mutable, backed by the entity's transform. */
    val position: Vector3f

    /** Euler angles in degrees. */
    val rotation: Vector3f
    val scale: Vector3f
}

interface ScriptEntity {
    val id: Int
    var name: String
    val transform: ScriptTransform

    /** Returns a whitelisted facade (e.g. [ScriptRigidBody]) or null when the entity lacks it. */
    fun <T : Any> getComponent(type: KClass<T>): T?
    fun hasComponent(type: KClass<*>): Boolean
}

inline fun <reified T : Any> ScriptEntity.getComponent(): T? = getComponent(T::class)

interface ScriptRigidBody {
    var mass: Float
    var linearVelocity: Vector3f
    var angularVelocity: Vector3f
}

interface ScriptScene {
    fun find(name: String): ScriptEntity?
    fun findAll(name: String): List<ScriptEntity>
}

interface ScriptInput {
    fun isKeyDown(key: Int): Boolean
    fun isKeyPressed(key: Int): Boolean
    fun isKeyReleased(key: Int): Boolean
    fun isMouseButtonDown(button: Int): Boolean

    /** Value of a named action in [-1, 1]; 0 when unknown. */
    fun action(name: String): Float
}

interface ScriptPhysics {
    val gravity: Vector3f

    /** Distance to the closest hit along from->to, or null. */
    fun raycast(from: Vector3f, to: Vector3f): Float?
}

interface ScriptEvents {
    /** Custom string-keyed events, usable across languages. Handlers are removed when the script is destroyed. */
    fun on(name: String, handler: (Any?) -> Unit)
    fun emit(name: String, payload: Any? = null)
}

interface ScriptTime {
    val deltaTime: Float
    val fixedDeltaTime: Float
    val elapsed: Float
}

interface ScriptLog {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
}

/**
 * Base class of every `.kts` script. The script body registers lifecycle callbacks:
 * ```
 * onCreate { ctx -> ctx.log.info("hello") }
 * onUpdate { ctx, dt -> ctx.entity.transform.position.x += dt }
 * ```
 */
abstract class SkateScript : Script {
    private var create: ((ScriptContext) -> Unit)? = null
    private var update: ((ScriptContext, Float) -> Unit)? = null
    private var fixedUpdate: ((ScriptContext, Float) -> Unit)? = null
    private var destroy: ((ScriptContext) -> Unit)? = null

    fun onCreate(block: (ScriptContext) -> Unit) { create = block }
    fun onUpdate(block: (ScriptContext, Float) -> Unit) { update = block }
    fun onFixedUpdate(block: (ScriptContext, Float) -> Unit) { fixedUpdate = block }
    fun onDestroy(block: (ScriptContext) -> Unit) { destroy = block }

    override fun onCreate(ctx: ScriptContext) { create?.invoke(ctx) }
    override fun onUpdate(ctx: ScriptContext, dt: Float) { update?.invoke(ctx, dt) }
    override fun onFixedUpdate(ctx: ScriptContext, dt: Float) { fixedUpdate?.invoke(ctx, dt) }
    override fun onDestroy(ctx: ScriptContext) { destroy?.invoke(ctx) }
}
