package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.Prefabs
import com.pafoid.skate.engine.assets.Sprite
import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.editor.PropertiesWindow
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Vector2f
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT

open class Gizmo(private val arrowSprite: Sprite, private val propertiesWindow: PropertiesWindow) : Component() {

    private val xAxisColor = Vector4f(1f, 0.3f, 0.3f, 1f)
    private val xAxisColorHover = Vector4f(1f, 0f, 0f, 1f)
    private val yAxisColor = Vector4f(0.3f, 1f, 0.3f, 1f)
    private val yAxisColorHover = Vector4f(0f, 1f, 0f, 1f)

    private val scale = 100f

    private val gizmoWidth = 16f / scale
    private val gizmoHeight = 48f / scale
    
    private lateinit var xAxisObject: GameObject
    private lateinit var yAxisObject: GameObject
    
    private var xAxisSprite: SpriteRenderer? = null
    private var yAxisSprite: SpriteRenderer? = null
    
    private var xAxisOffset = Vector2f(24f / scale, -6f / scale)
    private var yAxisOffset = Vector2f(-7f / scale, 21f / scale)

    protected var xAxisActive = false
    protected var yAxisActive = false

    protected var activeGameObject: GameObject? = null

    private var inUse = false

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        
        xAxisObject = Prefabs.generateSpriteObject(arrowSprite, gizmoWidth, gizmoHeight, "GizmoX")
        yAxisObject = Prefabs.generateSpriteObject(arrowSprite, gizmoWidth, gizmoHeight, "GizmoY")
        
        xAxisSprite = xAxisObject.getComponent<SpriteRenderer>()
        yAxisSprite = yAxisObject.getComponent<SpriteRenderer>()
        
        xAxisObject.addComponent(NonPickable())
        yAxisObject.addComponent(NonPickable())
        
        val scene = SceneManager.getCurrentScene()
        scene?.addGameObjectToScene(xAxisObject)
        scene?.addGameObjectToScene(yAxisObject)
    }

    override fun start() {
        xAxisObject.transform.rotation.z = 90f
        yAxisObject.transform.rotation.z = 180f
        xAxisObject.setNoSerialize()
        yAxisObject.setNoSerialize()
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

        val xAxisHot = checkXHoverState()
        val yAxisHot = checkYHoverState()

        if ((xAxisHot || xAxisActive) && MouseListener.isDragging() && MouseListener.isMouseButtonDown(
                GLFW_MOUSE_BUTTON_LEFT
            )
        ) {
            xAxisActive = true
            yAxisActive = false
        } else if ((yAxisHot || yAxisActive) && MouseListener.isDragging() && MouseListener.isMouseButtonDown(
                GLFW_MOUSE_BUTTON_LEFT
            )
        ) {
            yAxisActive = true
            xAxisActive = false
        } else if (!MouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT) && !MouseListener.isDragging()) {
            xAxisActive = false
            yAxisActive = false
        }

        xAxisObject.transform.translation.set(go.transform.translation)
        yAxisObject.transform.translation.set(go.transform.translation)

        xAxisObject.transform.translation.add(xAxisOffset.x, xAxisOffset.y, 0f)
        yAxisObject.transform.translation.add(yAxisOffset.x, yAxisOffset.y, 0f)
    }

    fun setActive() {
        xAxisSprite?.setColor(xAxisColor)
        yAxisSprite?.setColor(yAxisColor)
    }

    fun setInactive() {
        activeGameObject = null
        xAxisSprite?.setColor(Vector4f(0f, 0f, 0f, 0f))
        yAxisSprite?.setColor(Vector4f(0f, 0f, 0f, 0f))
    }

    private fun checkXHoverState(): Boolean {
        val mousePos = MouseListener.getWorld()

        if (
            mousePos.x in xAxisObject.transform.translation.x - (gizmoHeight / 2f)..xAxisObject.transform.translation.x + (gizmoHeight / 2f) &&
            mousePos.y in xAxisObject.transform.translation.y - gizmoWidth..xAxisObject.transform.translation.y
        ) {
            xAxisSprite?.setColor(xAxisColorHover)
            return true
        }

        xAxisSprite?.setColor(xAxisColor)
        return false
    }

    private fun checkYHoverState(): Boolean {
        val mousePos = MouseListener.getWorld()
        if (
            mousePos.x <= yAxisObject.transform.translation.x + (gizmoWidth / 2f) &&
            mousePos.x >= yAxisObject.transform.translation.x - (gizmoWidth / 2f) &&
            mousePos.y <= yAxisObject.transform.translation.y + (gizmoHeight / 2f) &&
            mousePos.y >= yAxisObject.transform.translation.y - (gizmoHeight / 2f)
        ) {

            yAxisSprite?.setColor(yAxisColorHover)
            return true
        }

        yAxisSprite?.setColor(yAxisColor)
        return false
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