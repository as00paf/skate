package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.Prefabs
import com.pafoid.skate.engine.assets.AssetPool
import com.pafoid.skate.engine.assets.ObjLoader
import com.pafoid.skate.engine.assets.Texture
import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.editor.PropertiesWindow
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.toMatrix
import org.joml.Vector3f
import org.joml.Vector4f
import org.joml.Matrix4f
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT

open class Gizmo(private val propertiesWindow: PropertiesWindow) : Component() {

    private val xAxisColor = Vector3f(1f, 0.3f, 0.3f)
    private val xAxisColorHover = Vector3f(1f, 0f, 0f)
    private val yAxisColor = Vector3f(0.3f, 1f, 0.3f)
    private val yAxisColorHover = Vector3f(0f, 1f, 0f)
    private val zAxisColor = Vector3f(0.3f, 0.3f, 1f)
    private val zAxisColorHover = Vector3f(0f, 0f, 1f)

    protected lateinit var xAxisObject: GameObject
    protected lateinit var yAxisObject: GameObject
    protected lateinit var zAxisObject: GameObject
    
    protected var xAxisActive = false
    protected var yAxisActive = false
    protected var zAxisActive = false

    protected var activeGameObject: GameObject? = null

    private var inUse = false
    
    // Dimensions for the gizmo handles
    protected val arrowLength = 2.0f
    protected val arrowThickness = 0.1f

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        
        val loader = VAOLoader() // Inefficient, but needed if we don't pass it down
        val model = AssetPool.getRawModel(ObjLoader.CUBE, loader)
        val texture = AssetPool.getTexture(Texture.WHITE)
        
        xAxisObject = Prefabs.generateEntityObject(model, texture, "GizmoX")
        yAxisObject = Prefabs.generateEntityObject(model, texture, "GizmoY")
        zAxisObject = Prefabs.generateEntityObject(model, texture, "GizmoZ")
        
        xAxisObject.addComponent(NonPickable())
        yAxisObject.addComponent(NonPickable())
        zAxisObject.addComponent(NonPickable())
        
        // Initial setup for visualization
        xAxisObject.transform.scale.set(arrowLength, arrowThickness, arrowThickness)
        yAxisObject.transform.scale.set(arrowThickness, arrowLength, arrowThickness)
        zAxisObject.transform.scale.set(arrowThickness, arrowThickness, arrowLength)
        
        // Colors? We need a way to set color on Entity or TexturedModel
        // Currently Entity uses texture. 
        // We can set a color uniform if supported, or use different textures.
        // For now, let's assume we can set a color property on Entity if it exists, 
        // or we'll just rely on picking highlighting if visual color isn't easily settable.
        // Actually, Renderer uploads "lightColor" but not per-object color.
        // Wait, 'Entity' does not have color. 
        // I should probably add a color tint to Entity or shader.
        // For now, they will be white. Highlighting might be tricky without color.
        
        val scene = SceneManager.getCurrentScene()
        scene?.addGameObjectToScene(xAxisObject)
        scene?.addGameObjectToScene(yAxisObject)
        scene?.addGameObjectToScene(zAxisObject)
    }

    override fun start() {
        xAxisObject.setNoSerialize()
        yAxisObject.setNoSerialize()
        zAxisObject.setNoSerialize()
    }

    override fun update(dt: Float) {
        if (inUse) setInactive()
    }

    override fun editorUpdate(dt: Float) {
        if (!inUse) return
        activeGameObject = propertiesWindow.getActiveObject()
        val go = activeGameObject
        if (go != null) {
            setActive()
        } else {
            setInactive()
            return
        }

        // Position gizmo at object center
        val pos = go.transform.translation
        xAxisObject.transform.translation.set(pos).add(arrowLength/2f, 0f, 0f)
        yAxisObject.transform.translation.set(pos).add(0f, arrowLength/2f, 0f)
        zAxisObject.transform.translation.set(pos).add(0f, 0f, arrowLength/2f)
        
        // Check hovering
        val scene = SceneManager.getCurrentScene() ?: return
        val mouseX = MouseListener.getScreenX()
        val mouseY = MouseListener.getScreenY()
        val ray = scene.camera.screenToRay(mouseX, mouseY, 1920f, 1080f) // TODO: Get actual window size
        
        val xAxisHot = checkIntersect(xAxisObject, ray)
        val yAxisHot = checkIntersect(yAxisObject, ray)
        val zAxisHot = checkIntersect(zAxisObject, ray)

        if ((xAxisHot || xAxisActive) && MouseListener.isDragging() && MouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT)) {
            xAxisActive = true
            yAxisActive = false
            zAxisActive = false
        } else if ((yAxisHot || yAxisActive) && MouseListener.isDragging() && MouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT)) {
            yAxisActive = true
            xAxisActive = false
            zAxisActive = false
        } else if ((zAxisHot || zAxisActive) && MouseListener.isDragging() && MouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT)) {
            zAxisActive = true
            xAxisActive = false
            yAxisActive = false
        } else {
            xAxisActive = false
            yAxisActive = false
            zAxisActive = false
        }
        
        // Color update (Placeholder logic until we have tinting)
        // if (xAxisActive || xAxisHot) setColor(xAxisObject, xAxisColorHover) else setColor(xAxisObject, xAxisColor)
    }
    
    private fun checkIntersect(go: GameObject, ray: com.pafoid.skate.engine.utils.Ray): Boolean {
        // Ray-OBB intersection
        // Transform ray to local space
        val modelMatrix = go.transform.toMatrix()
        val invModel = Matrix4f(modelMatrix).invert()
        
        val localOrigin = Vector4f(ray.origin, 1.0f).mul(invModel)
        val localDir = Vector4f(ray.direction, 0.0f).mul(invModel)
        
        // AABB in local space is -0.5 to 0.5 (Cube)
        val min = Vector3f(-0.5f, -0.5f, -0.5f)
        val max = Vector3f(0.5f, 0.5f, 0.5f)
        
        // Slab method
        val t1 = (min.x - localOrigin.x) / localDir.x
        val t2 = (max.x - localOrigin.x) / localDir.x
        val t3 = (min.y - localOrigin.y) / localDir.y
        val t4 = (max.y - localOrigin.y) / localDir.y
        val t5 = (min.z - localOrigin.z) / localDir.z
        val t6 = (max.z - localOrigin.z) / localDir.z
        
        val tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6))
        val tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6))
        
        // if tmax < 0, ray (line) is intersecting AABB, but whole AABB is behind us
        if (tmax < 0) return false
        
        // if tmin > tmax, ray doesn't intersect AABB
        if (tmin > tmax) return false
        
        return true
    }

    fun setActive() {
        // Show gizmos
        xAxisObject.transform.scale.set(arrowLength, arrowThickness, arrowThickness)
        yAxisObject.transform.scale.set(arrowThickness, arrowLength, arrowThickness)
        zAxisObject.transform.scale.set(arrowThickness, arrowThickness, arrowLength)
    }

    fun setInactive() {
        activeGameObject = null
        // Hide gizmos
        xAxisObject.transform.scale.set(0f, 0f, 0f)
        yAxisObject.transform.scale.set(0f, 0f, 0f)
        zAxisObject.transform.scale.set(0f, 0f, 0f)
    }

    fun isInUse(): Boolean = inUse
    fun setNotInUse() {
        inUse = false
        setInactive()
    }

    fun setInUse() {
        inUse = true
    }
}
