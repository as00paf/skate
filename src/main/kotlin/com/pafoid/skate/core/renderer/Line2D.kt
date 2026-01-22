package marki.renderer

import org.joml.Vector2f
import org.joml.Vector3f

class Line2D(
    val from:Vector2f = Vector2f(),
    val to:Vector2f = Vector2f(),
    val color:Vector3f = Vector3f(),
    var lifetime: Int = 1
) {

    fun beginFrame():Int {
        lifetime --
        return lifetime
    }

    fun getStart() = from
    fun getEnd() = to

    override fun toString(): String {
        return "Line2D: [from: ${from.x}, ${from.y}, to: ${to.x}, ${to.y}]"
    }

    fun lengthSquared(): Float {
        return Vector2f(to).sub(from).lengthSquared()
    }
}