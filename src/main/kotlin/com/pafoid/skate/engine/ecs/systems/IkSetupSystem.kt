package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.animation.ik.IkSetupBinder
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.IkSetup
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import org.joml.Matrix4f
import java.util.IdentityHashMap

/**
 * Turns each character's [IkSetup] into its [Animator]'s IK constraints (ANI-28 slice b), while playing. Runs after
 * gameplay and physics (which set goals) and before [AnimationSystem] (which solves): it is registered right before
 * it. Target names are looked up in the character's own hierarchy first, then in the scene outside other characters.
 */
class IkSetupSystem : System(priority = ExecutionPriority.DEFAULT) {
    private val characters = query(IkSetup::class, Animator::class, SkeletonComponent::class, topLevelOnly = true)
    private val binders = IdentityHashMap<IkSetup, Pair<IkSetupBinder, Animator>>()

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        // By identity: IkSetup is a data class, and two characters can have equal setups.
        val seen = java.util.Collections.newSetFromMap(IdentityHashMap<IkSetup, Boolean>())
        characters.forEach { go ->
            val setup = go.getComponent<IkSetup>() ?: return@forEach
            val animator = go.getComponent<Animator>() ?: return@forEach
            val skeleton = go.getComponent<SkeletonComponent>()?.pose?.skeleton ?: return@forEach
            seen += setup
            val binder = binders.getOrPut(setup) { IkSetupBinder() to animator }.first
            binder.update(setup, skeleton, animator.ik, dt) { name -> worldMatrixOf(go, name) }
        }
        // A removed setup (or character) takes its constraints with it.
        binders.entries.removeIf { (setup, entry) -> (setup !in seen).also { gone -> if (gone) entry.first.detach(entry.second.ik) } }
    }

    private fun worldMatrixOf(character: GameObject, name: String): Matrix4f? {
        val target = findIn(character, name, skipCharacters = false) ?: findIn(scene, name, skipCharacters = true) ?: return null
        return target.getComponent<Transform>()?.worldMatrix
    }

    /**
     * First object named [name] under [root] in hierarchy order. With [skipCharacters], the hierarchies of characters
     * with their own [IkSetup] are skipped: a character's targets are its own (two skaters never share a "front foot").
     */
    private fun findIn(root: GameObject, name: String, skipCharacters: Boolean): GameObject? {
        for (child in root.children) {
            if (skipCharacters && child.getComponent<IkSetup>() != null) continue
            if (child.name == name) return child
            findIn(child, name, skipCharacters)?.let { return it }
        }
        return null
    }
}
