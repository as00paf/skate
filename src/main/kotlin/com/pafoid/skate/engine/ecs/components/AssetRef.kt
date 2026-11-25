package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.AssetFolder

/**
 * Marks a component `String` field that holds the path (or `engine:` reference) of an asset of type [kind], e.g. a
 * particle texture or a `.skmat` material. The generic inspector shows such fields as a text field plus a picker
 * filtered to that type, accepts files dragged from the Asset Browser or the Project window, and flags a path whose
 * file does not exist. Next to [GameObjectRef], which does the same for scene objects.
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class AssetRef(val kind: AssetFolder)
