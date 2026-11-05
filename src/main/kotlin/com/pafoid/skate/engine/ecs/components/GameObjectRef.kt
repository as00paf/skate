package com.pafoid.skate.engine.ecs.components

/**
 * Marks a component `String` field that holds the **name** of a GameObject in the same scene (e.g. a follow
 * camera's target). The generic inspector shows such fields as a text field plus an object picker (scene object
 * dropdown, or drop an object from the hierarchy). Runtime code resolves the name with [com.pafoid.skate.engine.ecs.Scene.findFirstByName];
 * names are not unique, so the first match in hierarchy order wins.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class GameObjectRef
