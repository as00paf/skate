package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.scenes.GameObject
import imgui.type.ImInt
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.lang.reflect.Modifier

@Serializable
@Polymorphic
abstract class Component {

    companion object {
        private var ID_COUNTER: Int = 0

        fun init(maxId: Int) {
            ID_COUNTER = maxId
        }
    }

    var uId = -1
    var enabled = true

    @Transient
    lateinit var gameObject: GameObject

    open fun init(gameObject:GameObject) {
        this.gameObject = gameObject
    }
    open fun start() {}
    open fun update(dt: Float) {}
    open fun editorUpdate(dt: Float) {}

    open fun imgui() {
        try {
            imgui.ImGui.pushID(this.javaClass.simpleName)
            val fields = this.javaClass.declaredFields
            fields.forEach { field ->
                val modifiers = field.modifiers
                if (Modifier.isTransient(modifiers) || Modifier.isStatic(modifiers)) return@forEach
                
                if (Modifier.isPrivate(modifiers)) field.isAccessible = true

                val type = field.type
                val value = field.get(this)
                val name = field.name
                val isFinal = Modifier.isFinal(modifiers)

                when (type) {
                    Int::class.java -> {
                        if (!isFinal) {
                            val typedValue = value as Int
                            field.set(this, com.pafoid.skate.engine.utils.MImGui.dragInt(name, typedValue))
                        }
                    }
                    Float::class.java -> {
                        if (!isFinal) {
                            val typedValue = value as Float
                            field.set(this, com.pafoid.skate.engine.utils.MImGui.dragFloat(name, typedValue))
                        }
                    }
                    Boolean::class.java -> {
                        if (!isFinal) {
                            val typedValue = value as Boolean
                            if (imgui.ImGui.checkbox("$name", typedValue)) {
                                field.set(this, !typedValue)
                            }
                        }
                    }
                    Vector2f::class.java -> {
                        val typedValue = value as Vector2f
                        com.pafoid.skate.engine.utils.MImGui.drawVec2Control(name, typedValue)
                    }
                    Vector3f::class.java -> {
                        val typedValue = value as Vector3f
                        com.pafoid.skate.engine.utils.MImGui.drawVec3Control(name, typedValue)
                    }
                    Vector4f::class.java -> {
                        val typedValue = value as Vector4f
                        val imVec = floatArrayOf(typedValue.x, typedValue.y, typedValue.z, typedValue.w)
                        if (imgui.ImGui.dragFloat4("$name", imVec)) {
                            typedValue.set(imVec[0], imVec[1], imVec[2], imVec[3])
                        }
                    }
                    else -> {
                        if (type.isEnum && !isFinal) {
                            val enumValues = getEnumValues(type as Class<out Enum<*>>)
                            val enumType = (value as Enum<*>).name
                            val index = ImInt(indexOf(enumType, enumValues))
                            if (imgui.ImGui.combo(field.name, index, enumValues, enumValues.size)) {
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
            imgui.ImGui.popID()
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

    open fun destroy() {
    }
}
