package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.events.Event

sealed class SelectionEvent(eventName: String) : Event(eventName)


data class GameObjectSelected(val gameObject: GameObject) : SelectionEvent("editor.gameobject_selected")
object SelectionCleared : SelectionEvent("editor.selection_cleared")
data class MultiSelectionChanged(val selectedObjects: List<GameObject>) : SelectionEvent("editor.multi_selection_changed")
