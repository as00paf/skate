package com.pafoid.skate.engine.render.renderer.passes

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/** REN-24: every pass and post effect shows its own name in the Render Graph window (they all read "Effect"). */
class RenderPassNamingTest {
    private fun strings(file: String) = Properties().apply { File("assets/strings/editor/$file").reader(Charsets.UTF_8).use { load(it) } }

    @Test
    fun postEffects_HaveDistinctLabelKeys() {
        val keys = PostEffect.entries.map { it.labelKey }
        assertEquals(keys.size, keys.toSet().size, "duplicate keys: $keys")
        assertEquals("lbl.post_process.fxaa", PostEffect.FXAA.labelKey)
        assertEquals("lbl.post_process.gamma_correction", PostEffect.GAMMA_CORRECTION.labelKey)
    }

    @Test
    fun postEffectLabels_AreTranslatedToTheirRealNames() {
        for (file in listOf("render.properties", "render_fr.properties")) {
            val props = strings(file)
            val labels = PostEffect.entries.map { effect ->
                val label = props.getProperty(effect.labelKey)
                assertTrue(!label.isNullOrBlank(), "$file has no ${effect.labelKey}")
                assertFalse(label.equals("Effect", ignoreCase = true) || label.equals("Effet", ignoreCase = true), "$file: $label")
                label
            }
            assertEquals(labels.size, labels.toSet().size, "$file: duplicate effect labels $labels")
        }
        assertEquals("FXAA", strings("render.properties").getProperty(PostEffect.FXAA.labelKey))
        assertEquals("SSAO", strings("render.properties").getProperty(PostEffect.SSAO.labelKey))
    }

    @Test
    fun noShadowPass_KeepsTheShadowPassName() {
        val pass = NoShadowPass()
        assertEquals("ShadowPass", pass.name)
        assertEquals("Shadow", pass.displayName)
    }

    @Test
    fun displayNames_AreReadable() {
        val names = mapOf(
            "PostProcessPass" to "Post Process",
            "GeometryPass" to "Geometry",
            "ShadowPass" to "Shadow",
            "UiPass" to "Ui",
        )
        for ((name, display) in names) {
            val pass = object : BaseRenderPass() { override val name = name }
            assertEquals(display, pass.displayName)
        }
    }
}
