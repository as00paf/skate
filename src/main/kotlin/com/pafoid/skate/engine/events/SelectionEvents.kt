package com.pafoid.skate.engine.events

import com.pafoid.skate.engine.ecs.GameObject

/**
 * Base class for all selection events.
 * 
 * Selection events are published when the user selects or deselects
 * GameObjects in the editor.
 * 
 * @property eventName The event name for scripting integration
 */
sealed class SelectionEvent(eventName: String) : GameEvent(eventName)

/**
 * Published when a GameObject is selected in the editor.
 * 
 * @param gameObject The selected GameObject
 * 
 * ## Usage
 * 
 * ```kotlin
 * eventSystem.subscribe<GameObjectSelected> { event ->
 *     propertiesWindow.show(event.gameObject)
 * }
 * ```
 */
data class GameObjectSelected(val gameObject: GameObject) : SelectionEvent("editor.gameobject_selected")

/**
 * Published when selection is cleared (no object selected).
 * 
 * This is an object to simplify usage:
 * 
 * ```kotlin
 * eventSystem.subscribe<SelectionCleared> {
 *     propertiesWindow.hide()
 * }
 * ```
 */
object SelectionCleared : SelectionEvent("editor.selection_cleared")

/**
 * Published when multiple objects are selected.
 * 
 * @param selectedObjects List of selected GameObjects
 */
data class MultiSelectionChanged(val selectedObjects: List<GameObject>) : SelectionEvent("editor.multi_selection_changed")
