package com.pafoid.skate.engine.render.renderer.passes

/**
 * Plans which buffer each post effect reads and which target it writes.
 *
 * The first effect reads the scene colour. Each later effect reads the previous output. Outputs alternate between
 * two scratch targets ([Target.PING], [Target.PONG]), so no effect samples a texture attached to the framebuffer it
 * draws into. Effects never write [Target.SCENE] either: SSAO samples the scene depth, and the scene depth must
 * stay intact for the debug pass. The pass copies the last output back into the scene colour at the end.
 */
object PostChain {
    enum class Target { SCENE, PING, PONG }

    data class Step(val source: Target, val destination: Target)

    fun plan(effectCount: Int): List<Step> = List(effectCount) { i ->
        Step(
            source = if (i == 0) Target.SCENE else scratch(i - 1),
            destination = scratch(i),
        )
    }

    /** Target holding the result after [effectCount] effects (the scene itself when no effect runs). */
    fun resultTarget(effectCount: Int): Target = if (effectCount == 0) Target.SCENE else scratch(effectCount - 1)

    private fun scratch(i: Int) = if (i % 2 == 0) Target.PING else Target.PONG
}
