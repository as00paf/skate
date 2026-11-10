package com.pafoid.skate.game.player

import com.pafoid.skate.editor.project.EngineAssetCopier
import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.assets.AssetPaths
import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.gameplay.CharacterBehaviors
import com.pafoid.skate.engine.gameplay.CharacterConfigs
import com.pafoid.skate.engine.gameplay.ConfigProperties
import com.pafoid.skate.game.prefabs.Skater
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * GPL-26: the skater configs are engine defaults (`engine:Data/skater.character.json`,
 * `engine:Animations/skater.controller.json`). A project copy (made at project creation, edited in the editor)
 * overrides them; without one the engine's file is used. Reads go through the engine's [AssetPaths], as in the editor.
 */
class SkaterConfigOverrideTest {

    private val project: File = createTempDirectory("gpl26").toFile()
    private val paths = AssetPaths().also { it.projectRoot = project }

    init {
        SkateGameplay.register()
        CharacterConfigs.reader = { paths.readText(it) }
    }

    @AfterEach
    fun cleanup() {
        CharacterConfigs.reader = null
        project.deleteRecursively()
    }

    private fun skaterConfig(): SkateControllerConfig =
        (CharacterBehaviors.create(SkateBehavior.ID, Skater.CONFIG_PATH) as SkateBehavior).config

    /** Edits the project copy of [reference] as the editor's config inspector does (one leaf, saved as JSON). */
    private fun editCopy(reference: String, path: String, value: JsonPrimitive): File {
        val copy = paths.engineDefaults.overrideFile(EngineDefaults.logicalPath(reference)!!)!!
        val edited = ConfigProperties.set(Json.parseToJsonElement(copy.readText()).jsonObject, path, value)
        copy.writeText(edited.toString())
        return copy
    }

    @Test
    fun skaterUsesEngineDefaultPaths() {
        assertEquals("engine:Data/skater.character.json", Skater.CONFIG_PATH)
        assertEquals("engine:Animations/skater.controller.json", Skater.CONTROLLER_PATH)
    }

    @Test
    fun withoutAnOverride_TheEngineConfigIsUsed() {
        val engineValue = Json.parseToJsonElement(File("assets/Data/skater.character.json").readText())
            .jsonObject.let { ConfigProperties.flatten(it).getValue("push.acceleration").content.toFloat() }

        assertEquals(engineValue, skaterConfig().push.acceleration)
    }

    @Test
    fun aProjectOverride_ChangesTheSkater() {
        EngineAssetCopier().copyEngineDefaults(project, copyAll = false).getOrThrow()
        val copy = editCopy(Skater.CONFIG_PATH, "push.acceleration", JsonPrimitive(42.5))
        paths.engineDefaults.invalidate()

        assertEquals(File(project, "Assets/Data/skater.character.json").absoluteFile, copy.absoluteFile)
        assertEquals(42.5f, skaterConfig().push.acceleration)
    }

    @Test
    fun aProjectControllerOverride_DrivesTheAnimator() {
        EngineAssetCopier().copyEngineDefaults(project, copyAll = false).getOrThrow()
        editCopy(Skater.CONTROLLER_PATH, "defaultState", JsonPrimitive("walking"))
        paths.engineDefaults.invalidate()
        val clips = listOf("idle_0", "walking", "running", "jump", "falling idle", "skate riding")
        val animator = Animator(animations = clips.map { Animation(it, emptyList(), 1f, "$it.fbx") }.toMutableList())
            .apply { controllerPath = Skater.CONTROLLER_PATH }

        assertTrue(animator.resolveController(LoggerService(), TestHumanoid.skeleton(), read = { paths.readText(it) }))

        assertEquals("walking", animator.stateMachine!!.currentState)
    }

    @Test
    fun theJarHoldsAClasspathCopyForExportsAndNoProject() {
        // build.gradle.kts copies the engine files to /configs/ (EngineDefaults.classpathResource).
        listOf(Skater.CONFIG_PATH, Skater.CONTROLLER_PATH).forEach { ref ->
            val resource = EngineDefaults.classpathResource(EngineDefaults.logicalPath(ref)!!)
            assertNotNull(resource, ref)
            assertNotNull(javaClass.getResource(resource!!), "$resource missing from the jar")
        }
    }
}
