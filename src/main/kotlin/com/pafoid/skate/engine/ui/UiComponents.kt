package com.pafoid.skate.engine.ui

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.components.Component
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector4f

/** Root of a screen-space UI tree; its descendants carrying [UIElementComponent] belong to this canvas. */
@Serializable
class UICanvasComponent(
    var sortOrder: Int = 0,
    var scaleMode: UiScaleMode = UiScaleMode.SCALE_WITH_SCREEN,
    var referenceWidth: Float = 1920f,
    var referenceHeight: Float = 1080f,
    var match: Float = 0.5f,
    var visible: Boolean = true,
    /** A modal canvas takes all navigation input and traps focus. */
    var modal: Boolean = false,
) : Component() {
    @Transient var scale: Float = 1f
    @Transient var width: Float = 0f
    @Transient var height: Float = 0f
    /** Invoked when the cancel action fires while this canvas is the top input target. */
    @Transient var onCancel: (() -> Unit)? = null
}

/** Rect transform and interaction flags shared by every UI element. See [UiNodeSpec] for the anchor semantics. */
@Serializable
class UIElementComponent(
    var anchorMinX: Float = 0f, var anchorMinY: Float = 0f,
    var anchorMaxX: Float = 0f, var anchorMaxY: Float = 0f,
    var pivotX: Float = 0f, var pivotY: Float = 0f,
    var x: Float = 0f, var y: Float = 0f,
    var width: Float = 100f, var height: Float = 100f,
    var layout: UiLayoutGroup = UiLayoutGroup(),
    var visible: Boolean = true,
    var interactable: Boolean = true,
    var opacity: Float = 1f,
    var zOrder: Int = 0,
) : Component() {
    /** Resolved rect in canvas units; written by the layout system. */
    @Transient var rect: UiRectF = UiRectF(0f, 0f, 0f, 0f)

    /** Set by the input system. */
    @Transient var focused: Boolean = false

    fun anchors(minX: Float, minY: Float, maxX: Float, maxY: Float) = apply {
        anchorMinX = minX; anchorMinY = minY; anchorMaxX = maxX; anchorMaxY = maxY
    }

    /** Fixed-size element anchored (and pivoted) at one point of its parent. */
    fun placed(anchorX: Float, anchorY: Float, offsetX: Float, offsetY: Float, w: Float, h: Float) = apply {
        anchors(anchorX, anchorY, anchorX, anchorY)
        pivotX = anchorX; pivotY = anchorY
        x = offsetX; y = offsetY; width = w; height = h
    }

    fun stretched(left: Float = 0f, top: Float = 0f, right: Float = 0f, bottom: Float = 0f) = apply {
        anchors(0f, 0f, 1f, 1f)
        pivotX = 0.5f; pivotY = 0.5f
        x = (left - right) * 0.5f; y = (top - bottom) * 0.5f
        width = -(left + right); height = -(top + bottom)
    }
}

/** Text drawn from a [com.pafoid.skate.engine.core.StringManager] key; never holds user-visible literals. */
@Serializable
class UITextComponent(
    var key: String = "",
    var args: List<String> = emptyList(),
    @Contextual var color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
    /** Line height in canvas units. */
    var fontSize: Float = 32f,
    var alignX: Float = 0f,
    var alignY: Float = 0.5f,
    /** When set, [key] is a plural base (`key.one`, `key.few`, `key.other`, ...) chosen for this count in the current locale. */
    var quantity: Int? = null,
) : Component()

/** Solid color and/or textured rect; 9-slice when any inset is positive. */
@Serializable
class UIImageComponent(
    var texturePath: String? = null,
    @Contextual var tint: Vector4f = Vector4f(1f, 1f, 1f, 1f),
    var sliceLeft: Int = 0, var sliceTop: Int = 0, var sliceRight: Int = 0, var sliceBottom: Int = 0,
) : Component() {
    @Transient var texture: Texture? = null
}

/** Clickable element: draws its background per state and a centered label from [labelKey]. */
@Serializable
class UIButtonComponent(
    var labelKey: String = "",
    @Contextual var normalColor: Vector4f = Vector4f(0.2f, 0.2f, 0.25f, 0.9f),
    @Contextual var hoverColor: Vector4f = Vector4f(0.3f, 0.3f, 0.4f, 0.9f),
    @Contextual var pressedColor: Vector4f = Vector4f(0.1f, 0.1f, 0.15f, 0.9f),
    @Contextual var disabledColor: Vector4f = Vector4f(0.15f, 0.15f, 0.15f, 0.5f),
    @Contextual var focusColor: Vector4f = Vector4f(1f, 0.8f, 0.2f, 1f),
    @Contextual var labelColor: Vector4f = Vector4f(1f, 1f, 1f, 1f),
    var fontSize: Float = 32f,
) : Component() {
    @Transient var hovered = false
    @Transient var pressed = false
    @Transient var onClick: (() -> Unit)? = null
}

@Serializable
class UISliderComponent(
    var min: Float = 0f,
    var max: Float = 1f,
    var value: Float = 0f,
    var step: Float = 0f,
    @Contextual var trackColor: Vector4f = Vector4f(0.15f, 0.15f, 0.15f, 0.9f),
    @Contextual var fillColor: Vector4f = Vector4f(0.9f, 0.6f, 0.1f, 1f),
) : Component() {
    @Transient var dragging = false
    @Transient var onValueChanged: ((Float) -> Unit)? = null

    fun setValueFromUser(v: Float) {
        val snapped = UiWidgetMath.snap(v, min, max, step)
        if (snapped != value) {
            value = snapped
            onValueChanged?.invoke(snapped)
        }
    }
}

enum class UiFillDirection { LEFT_TO_RIGHT, RIGHT_TO_LEFT, BOTTOM_TO_TOP, TOP_TO_BOTTOM }

@Serializable
class UIProgressBarComponent(
    /** 0..1 */
    var value: Float = 0f,
    var direction: UiFillDirection = UiFillDirection.LEFT_TO_RIGHT,
    @Contextual var backgroundColor: Vector4f = Vector4f(0.1f, 0.1f, 0.1f, 0.8f),
    @Contextual var fillColor: Vector4f = Vector4f(0.2f, 0.8f, 0.3f, 1f),
) : Component()

@Serializable
class UiListItem(val key: String, val args: List<String> = emptyList())

/** Vertical scrolling list of text rows with a selection highlight. */
@Serializable
class UIListComponent(
    var items: List<UiListItem> = emptyList(),
    var itemHeight: Float = 40f,
    var selectedIndex: Int = 0,
    var scroll: Float = 0f,
    @Contextual var selectionColor: Vector4f = Vector4f(0.9f, 0.6f, 0.1f, 0.6f),
    @Contextual var textColor: Vector4f = Vector4f(1f, 1f, 1f, 1f),
) : Component() {
    @Transient var onSelected: ((Int) -> Unit)? = null
    @Transient var onActivated: ((Int) -> Unit)? = null

    fun select(index: Int) {
        if (items.isEmpty()) return
        val clamped = index.coerceIn(0, items.size - 1)
        if (clamped != selectedIndex) {
            selectedIndex = clamped
            onSelected?.invoke(clamped)
        }
    }
}
