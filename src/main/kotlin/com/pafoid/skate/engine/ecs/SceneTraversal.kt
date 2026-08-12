package com.pafoid.skate.engine.ecs

fun Scene.collectGameObjectsDepthFirst(): List<GameObject> {
    val allObjects = ArrayList<GameObject>(children.size)
    children.forEach { root ->
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
