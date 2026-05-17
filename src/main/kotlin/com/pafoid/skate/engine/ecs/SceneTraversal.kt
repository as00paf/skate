package com.pafoid.skate.engine.ecs

/**
 * Canonical recursive scene graph traversal utilities.
 */
fun Scene.collectGameObjectsDepthFirst(): List<GameObject> {
    val allObjects = ArrayList<GameObject>(gameObjects.size)
    gameObjects.forEach { root ->
        root.collectDepthFirstInto(allObjects)
    }
    return allObjects
}

private fun GameObject.collectDepthFirstInto(target: MutableList<GameObject>) {
    target.add(this)
    children.forEach { child ->
        child.collectDepthFirstInto(target)
    }
}
