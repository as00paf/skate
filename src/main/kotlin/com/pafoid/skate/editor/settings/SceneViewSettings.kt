package com.pafoid.skate.editor.settings

import kotlinx.serialization.Serializable

/**
 * Snap steps used while the toolbar's Snap toggle is on (EDT-57); each in the editor's world units, except [ui]
 * which is in canvas units for the UI rect gizmo (EDT-75).
 */
@Serializable
data class GizmoSnapSettings(
    val translate: Float = DEFAULT_TRANSLATE,
    val rotateDegrees: Float = DEFAULT_ROTATE_DEGREES,
    val scale: Float = DEFAULT_SCALE,
    val ui: Float = DEFAULT_UI,
) {
    /** Steps clamped to their editable ranges (a hand-edited config can hold anything). */
    fun sanitized() = GizmoSnapSettings(
        translate.coerceIn(MIN_STEP, MAX_TRANSLATE),
        rotateDegrees.coerceIn(MIN_STEP, MAX_ROTATE_DEGREES),
        scale.coerceIn(MIN_STEP, MAX_SCALE),
        ui.coerceIn(MIN_UI, MAX_UI),
    )

    companion object {
        const val DEFAULT_TRANSLATE = 0.5f
        const val DEFAULT_ROTATE_DEGREES = 15f
        const val DEFAULT_SCALE = 0.1f
        const val MIN_STEP = 0.001f
        const val MAX_TRANSLATE = 100f
        const val MAX_ROTATE_DEGREES = 180f
        const val MAX_SCALE = 10f
        const val DEFAULT_UI = 10f
        const val MIN_UI = 1f
        const val MAX_UI = 200f
    }
}

/** The editor camera's feel (EDT-57): fly speed in world units per second and a mouse-look multiplier. */
@Serializable
data class EditorCameraSettings(
    val moveSpeed: Float = DEFAULT_MOVE_SPEED,
    val lookSensitivity: Float = DEFAULT_LOOK_SENSITIVITY,
) {
    fun sanitized() = EditorCameraSettings(
        moveSpeed.coerceIn(MIN_MOVE_SPEED, MAX_MOVE_SPEED),
        lookSensitivity.coerceIn(MIN_LOOK_SENSITIVITY, MAX_LOOK_SENSITIVITY),
    )

    companion object {
        const val DEFAULT_MOVE_SPEED = 6f
        const val DEFAULT_LOOK_SENSITIVITY = 1f
        const val MIN_MOVE_SPEED = 0.5f
        const val MAX_MOVE_SPEED = 100f
        const val MIN_LOOK_SENSITIVITY = 0.1f
        const val MAX_LOOK_SENSITIVITY = 5f
    }
}
