package com.pafoid.skate.engine.render.renderer.passes

/**
 * Stand-in for the [ShadowPass] when the device has no shadow maps: draws nothing, but keeps the real pass name in
 * the render graph (an anonymous pass showed up as "UnknownPass", REN-24).
 */
class NoShadowPass : BaseRenderPass() {
    override val name: String = "ShadowPass"
    override val description: String = "Shadows unavailable: no shadow maps on this device"
}
