package physics2d

import marki.GameObject
import org.jbox2d.callbacks.RayCastCallback
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.Fixture
import org.joml.Vector2f

class RaycastInfo(val requestingObject: GameObject? = null): RayCastCallback{

    var fixture: Fixture? = null
    var point: Vector2f = Vector2f()
    var normal: Vector2f = Vector2f()
    var fraction: Float = 0f
    var hit: Boolean = false
    var hitObject: GameObject? = null

    override fun reportFixture(fixture: Fixture?, point: Vec2, normal: Vec2, fraction: Float): Float {
        if(fixture?.m_userData == requestingObject) return 1f

        this.fixture = fixture
        this.point = Vector2f(point.x, point.y)
        this.normal = Vector2f(normal.x, normal.y)
        this.fraction = fraction
        this.hit = fraction != 0f
        this.hitObject = fixture?.userData as GameObject

        return fraction
    }
}