package marki

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import components.Component
import java.lang.reflect.Type

class GameObjectSerializer: JsonDeserializer<GameObject> {

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): GameObject {
        val obj = json.asJsonObject
        val name = obj.get("name").asString
        val components = obj.getAsJsonArray("components")

        val go = GameObject(name)
        components.forEach { element ->
            val component = context.deserialize<Component>(element, Component::class.java)
            go.addComponent(component)
        }

        go.transform = go.getComponent(Transform::class.java)!!

        return go
    }
}