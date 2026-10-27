package com.pafoid.skate.engine.scripting.runtime

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getAllChildren
import com.pafoid.skate.engine.getComponent
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
import org.joml.Vector3f
import kotlin.reflect.KClass

/** Engine-side providers the runtime needs. Defaults are inert so the runtime works without them. */
class ScriptServices(
    val input: ScriptInput = NoOpScriptInput,
    val physics: ScriptPhysics = NoOpScriptPhysics,
)

object NoOpScriptInput : ScriptInput {
    override fun isKeyDown(key: Int) = false
    override fun isKeyPressed(key: Int) = false
    override fun isKeyReleased(key: Int) = false
    override fun isMouseButtonDown(button: Int) = false
    override fun action(name: String) = 0f
}

object NoOpScriptPhysics : ScriptPhysics {
    override val gravity: Vector3f = Vector3f(0f, -9.81f, 0f)
    override fun raycast(from: Vector3f, to: Vector3f): Float? = null
}

/** Whitelist of component facades scripts may obtain; other domains can register more. */
object ScriptFacades {
    private val factories = mutableMapOf<KClass<*>, (GameObject) -> Any?>()

    private val names = mutableMapOf<String, KClass<*>>()

    init {
        register(ScriptRigidBody::class) { go ->
            go.getComponent<RigidBody3D>()?.let { RigidBodyFacade(it) }
        }
        registerName("RigidBody", ScriptRigidBody::class)
    }

    fun <T : Any> register(type: KClass<T>, factory: (GameObject) -> T?) {
        factories[type] = factory
    }

    fun registerName(name: String, type: KClass<*>) {
        names[name] = type
    }

    fun create(name: String, go: GameObject): Any? = names[name]?.let { create(it, go) }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> create(type: KClass<T>, go: GameObject): T? = factories[type]?.invoke(go) as T?
}

private class RigidBodyFacade(private val body: RigidBody3D) : ScriptRigidBody {
    override var mass: Float
        get() = body.mass
        set(value) { body.mass = value }
    override var linearVelocity: Vector3f
        get() = Vector3f(body.linearVelocity)
        set(value) { body.linearVelocity = Vector3f(value) }
    override var angularVelocity: Vector3f
        get() = Vector3f(body.angularVelocity)
        set(value) { body.angularVelocity = Vector3f(value) }
}

private class TransformFacade(transform: Transform) : ScriptTransform {
    override val position: Vector3f = transform.translation
    override val rotation: Vector3f = transform.rotation
    override val scale: Vector3f = transform.scale
}

class ScriptEntityImpl(val gameObject: GameObject) : ScriptEntity {
    override val id: Int get() = gameObject.uId
    override var name: String
        get() = gameObject.name
        set(value) { gameObject.name = value }

    override val transform: ScriptTransform =
        TransformFacade(gameObject.getComponent<Transform>() ?: Transform())

    override fun <T : Any> getComponent(type: KClass<T>): T? = ScriptFacades.create(type, gameObject)

    override fun hasComponent(type: KClass<*>): Boolean = ScriptFacades.create(type, gameObject) != null

    override fun component(name: String): Any? = ScriptFacades.create(name, gameObject)
}

class ScriptSceneImpl(private val root: () -> GameObject) : ScriptScene {
    private fun all() = root().getAllChildren()

    override fun find(name: String): ScriptEntity? =
        all().firstOrNull { it.name == name }?.let { ScriptEntityImpl(it) }

    override fun findAll(name: String): List<ScriptEntity> =
        all().filter { it.name == name }.map { ScriptEntityImpl(it) }
}

/** Per-runtime string-keyed event bus; handlers are tracked per owner so they can be dropped on destroy. */
class ScriptEventBus {
    private class Handler(val owner: Any, val name: String, val callback: (Any?) -> Unit)

    private val handlers = mutableListOf<Handler>()

    fun on(owner: Any, name: String, callback: (Any?) -> Unit) {
        handlers.add(Handler(owner, name, callback))
    }

    fun emit(name: String, payload: Any?, onError: (Throwable) -> Unit) {
        handlers.filter { it.name == name }.forEach {
            try {
                it.callback(payload)
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }

    fun removeOwner(owner: Any) {
        handlers.removeAll { it.owner === owner }
    }

    fun clear() = handlers.clear()
}

class ScriptTimeImpl(
    override var deltaTime: Float = 0f,
    override val fixedDeltaTime: Float,
    override var elapsed: Float = 0f,
) : ScriptTime

class ScriptContextImpl(
    override val entity: ScriptEntity,
    override val scene: ScriptScene,
    override val input: ScriptInput,
    override val physics: ScriptPhysics,
    override val events: ScriptEvents,
    override val time: ScriptTime,
    override val log: ScriptLog,
    override val properties: Map<String, String>,
    override val state: MutableMap<String, Any?>,
    override val reloadCount: Int,
) : ScriptContext
