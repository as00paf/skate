package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import imgui.ImGui
import imgui.type.ImInt
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.lang.reflect.Modifier

@Serializable
@Polymorphic
abstract class Component: KoinComponent {

    private val stringManager: StringManager by inject()

    companion object {
        private var ID_COUNTER: Int = 0

        fun init(maxId: Int) {
            ID_COUNTER = maxId
        }

        fun getIdCounter(): Int = ID_COUNTER

        /** Cache for reflection results — avoids expensive Class.getDeclaredFields() per frame */
        private val fieldCache = mutableMapOf<Class<*>, Array<java.lang.reflect.Field>>()

        fun getCachedFields(clazz: Class<*>): Array<java.lang.reflect.Field> {
            return fieldCache.getOrPut(clazz) { clazz.declaredFields }
        }
    }

    var uId = -1
    var enabled = true

    @Transient
    lateinit var gameObject: GameObject
        private set

    /**
     * Called by GameObject.addComponent() and after scene deserialization.
     * Idempotent — safe to call multiple times.
     */
    open fun init(gameObject: GameObject) {
        if (::gameObject.isInitialized) return
        this.gameObject = gameObject
        uId = ID_COUNTER++
    }
    open fun start() {}
    open fun update(dt: Float) {}
    open fun editorUpdate(dt: Float) {}

    open fun imgui() {
        try {
            ImGui.pushID(this.javaClass.simpleName)
            val fields = getCachedFields(this.javaClass)
            fields.forEach { field ->
                val modifiers = field.modifiers
                if (Modifier.isTransient(modifiers) || Modifier.isStatic(modifiers)) return@forEach

                if (Modifier.isPrivate(modifiers)) field.isAccessible = true

                val type = field.type
                val value = field.get(this)
                val name = field.name
                val localizedName = stringManager.getString("component.${this.javaClass.simpleName}.$name")
                val isFinal = Modifier.isFinal(modifiers)

                when (type) {
                    Int::class.java -> {
                        if (!isFinal) {
                            val typedValue = value as Int
                            field.set(this, MImGui.dragInt(localizedName, typedValue))
                        }
                    }
                    Float::class.java -> {
                        if (!isFinal) {
                            val typedValue = value as Float
                            field.set(this, MImGui.dragFloat(localizedName, typedValue))
                        }
                    }
                    Boolean::class.java -> {
                        if (!isFinal) {
                            val typedValue = value as Boolean
                            if (ImGui.checkbox("$localizedName", typedValue)) {
                                field.set(this, !typedValue)
                            }
                        }
                    }
                    Vector2f::class.java -> {
                        val typedValue = value as Vector2f
                        MImGui.drawVec2Control(localizedName, typedValue)
                    }
                    Vector3f::class.java -> {
                        val typedValue = value as Vector3f
                        MImGui.drawVec3Control(localizedName, typedValue)
                    }
                    Vector4f::class.java -> {
                        val typedValue = value as Vector4f
                        val imVec = floatArrayOf(typedValue.x, typedValue.y, typedValue.z, typedValue.w)
                        if (ImGui.dragFloat4("$localizedName", imVec)) {
                            typedValue.set(imVec[0], imVec[1], imVec[2], imVec[3])
                        }
                    }
                    else -> {
                        if (type.isEnum && !isFinal) {
                            val enumValues = getEnumValues(type as Class<out Enum<*>>)
                            val enumType = (value as Enum<*>).name
                            val index = ImInt(indexOf(enumType, enumValues))
                            if (ImGui.combo(localizedName, index, enumValues, enumValues.size)) {
                                field.set(this, type.enumConstants[index.get()])
                            }
                        }
                    }
                }

                if (Modifier.isPrivate(modifiers)) field.isAccessible = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            ImGui.popID()
        }
    }

    private fun <T:Enum<T>> getEnumValues(type: Class<T>): Array<String> {
        val enumValues = arrayOfNulls<String>(type.enumConstants.size)
        var i = 0
        for (enumIntegerValue in type.enumConstants) {
            enumValues[i] = enumIntegerValue.name
            i++
        }
        return enumValues.filterNotNull().toTypedArray()
    }

    private fun indexOf(str: String, arr: Array<String>): Int {
        for (i in arr.indices) {
            if (str == arr[i]) {
                return i
            }
        }
        return -1
    }

    fun generateId() {
        if (uId == -1) uId = ID_COUNTER++
    }

    fun getUid() = uId

    open fun getName(): String {
        return this.javaClass.simpleName
    }

    open fun destroy() {
    }
}