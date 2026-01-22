package com.pafoid.skate.components

import com.google.gson.*
import java.lang.reflect.Type
import kotlin.jvm.javaClass

class ComponentDeserializer: JsonSerializer<Component>, JsonDeserializer<Component> {


    override fun serialize(src: Component, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val result = JsonObject()
        result.add("type", JsonPrimitive(src.javaClass.canonicalName))
        result.add("properties", context.serialize(src, src.javaClass))

        return result
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Component {
        val obj = json.asJsonObject
        val type = obj.get("type").asString
        val element = obj.get("properties")

        try {
            return context.deserialize(element, Class.forName(type))
        } catch (e: ClassNotFoundException){
            throw JsonParseException("Unknown element type: $type", e)
        }
    }
}