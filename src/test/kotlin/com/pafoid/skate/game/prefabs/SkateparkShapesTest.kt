package com.pafoid.skate.game.prefabs

import com.pafoid.skate.engine.assets.EngineDefaults
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringTables
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class SkateparkShapesTest {

    private val generated = PrefabCatalog.entries.filter { it.generatedMesh != null }
    private val obstacles = PrefabType.entries.mapNotNull { type -> PrefabCatalog.shape(type)?.let { type to it } }

    /**
     * The OBJs in assets/Models are the generator's output. After changing a shape, regenerate them with
     * `cmd.exe /c "set SKATE_REGEN_OBSTACLES=1&& gradlew.bat :test --tests *SkateparkShapesTest*"`.
     */
    @Test
    fun PrefabModels_CommittedObjFilesMatchTheGenerator() {
        val regenerate = System.getenv("SKATE_REGEN_OBSTACLES") == "1"
        val stale = generated.filter { entry ->
            val file = File(entry.modelPath)
            val obj = entry.generatedMesh!!().toObj(entry.type.name.lowercase())
            if (regenerate) file.writeText(obj)
            !file.exists() || file.readText().replace("\r\n", "\n") != obj
        }
        assertTrue(stale.isEmpty(), "Regenerate (see KDoc): ${stale.map { it.modelPath }}")
    }

    @Test
    fun PrefabShapes_StandOnTheGroundCentredOnTheOrigin() {
        obstacles.forEach { (type, shape) ->
            val (min, max) = shape.mesh.bounds()
            assertEquals(0f, min.y, 1e-4f, "$type bottom")
            assertTrue(max.y > 0.1f, "$type is flat")
            assertEquals(0f, min.x + max.x, 1e-3f, "$type not centred in x")
            assertEquals(0f, min.z + max.z, 1e-3f, "$type not centred in z")
        }
    }

    @Test
    fun PrefabShapes_CollisionStaysInsideTheMeshBoundsAndReachesItsTop() {
        obstacles.forEach { (type, shape) ->
            val (min, max) = shape.mesh.bounds()
            assertTrue(shape.hulls.isNotEmpty(), "$type has no collision")
            val points = shape.hulls.flatten()
            assertTrue(shape.hulls.all { it.size >= 4 }, "$type hull with too few points")
            points.forEach { p ->
                val slack = 0.06f
                assertTrue(
                    p.x in min.x - slack..max.x + slack && p.y in min.y - slack..max.y + slack && p.z in min.z - slack..max.z + slack,
                    "$type collision point $p outside $min..$max",
                )
            }
            assertEquals(max.y, points.maxOf { it.y }, 0.06f, "$type collision top")
        }
    }

    @Test
    fun PrefabShapes_TransitionsRiseAwayFromTheRidingFace() {
        val shape = SkateparkShapes.quarterPipe(height = 1.5f)
        val tops = shape.hulls.map { hull -> hull.maxOf { it.z } to hull.maxOf { it.y } }.sortedBy { it.first }

        // Toward +Z (the riding face) the columns get lower; the toe column is the lowest.
        assertTrue(tops.zipWithNext().all { (a, b) -> b.second <= a.second + 1e-4f }, "quarter pipe not rising toward -Z: $tops")
        assertEquals(1.5f, tops.first().second, 1e-4f)
        assertTrue(tops.last().second < 0.1f)
    }

    @Test
    fun PrefabShapes_TransitionCurveIsTangentToTheGround() {
        val curve = SkateparkShapes.transition(height = 1.2f, radius = 1.8f)

        assertEquals(Vector3f(0f), Vector3f(curve.first().x, curve.first().y, 0f))
        assertEquals(1.2f, curve.last().y, 1e-5f)
        // First segment nearly flat, last one steep.
        val first = curve[1].y / curve[1].x
        val last = (curve.last().y - curve[curve.size - 2].y) / (curve.last().x - curve[curve.size - 2].x)
        assertTrue(first < 0.1f && last > 2f, "slopes $first .. $last")
    }

    @Test
    fun PrefabShapes_StairsHaveOneColumnPerStep() {
        listOf(3, 5).forEach { steps ->
            val shape = SkateparkShapes.stairs(steps, riser = 0.17f)
            assertEquals(steps, shape.hulls.size)
            assertEquals(steps * 0.17f, shape.mesh.bounds().second.y, 1e-4f)
        }
    }

    @Test
    fun PrefabShapes_HandrailAndHubbaFollowTheFiveStairNoses() {
        val stairs = SkateparkShapes.stairs(5)
        val rail = SkateparkShapes.handrail(steps = 5, height = 0.8f)
        val (stairMin, stairMax) = stairs.mesh.bounds()

        // Same run as the stairs, so both spawned at the same Z line up.
        assertEquals(stairMin.z, rail.mesh.bounds().first.z, 0.05f)
        assertEquals(stairMax.z, rail.mesh.bounds().second.z, 0.05f)
        val nose = SkateparkShapes.noseLine(5)
        assertEquals(stairMax.y, nose.first().y, 1e-5f)
        // Last nose = top of the lowest step.
        assertEquals(0.17f, nose.last().y, 1e-5f)
        assertEquals(nose.first().y + 0.45f, SkateparkShapes.hubba(5, height = 0.45f).mesh.bounds().second.y, 1e-4f)
    }

    @Test
    fun PrefabCatalog_EveryTypeHasABundledModelAndATranslatedName() {
        val en = StringTables.merged(File(Assets.Strings.EDITOR_STRINGS))
        val fr = StringTables.merged(File(Assets.Strings.EDITOR_STRINGS), "fr")
        PrefabType.entries.forEach { type ->
            val entry = PrefabCatalog.entry(type)
            assertTrue(File(entry.modelPath).exists(), "$type model ${entry.modelPath} missing")
            assertEquals(entry.modelPath, "assets/" + EngineDefaults.logicalPath(entry.bundledModelPath), "$type model is not its engine default")
            assertTrue(entry.bundledModelPath in EngineDefaults.CATALOG, "$type model not in the engine defaults catalog")
            assertTrue(en.containsKey(type.nameKey) && fr.containsKey(type.nameKey), "${type.nameKey} missing")
        }
        PrefabCategory.entries.forEach { category ->
            val key = "lbl.prefabs.${category.name.lowercase()}"
            assertTrue(en.containsKey(key) && fr.containsKey(key), "$key missing")
            assertTrue(PrefabCatalog.inCategory(category).isNotEmpty(), "$category is empty")
        }
    }

    @Test
    fun PrefabCatalog_MaterialFallsBackToThePrefabsDefault() {
        assertEquals(MaterialType.WOOD_TAN, PrefabCatalog.materialFor(PrefabType.QUARTER_PIPE, MaterialType.WOOD_TAN))
        assertEquals(MaterialType.METAL, PrefabCatalog.materialFor(PrefabType.HANDRAIL, MaterialType.CONCRETE))
        assertEquals(MaterialType.CONCRETE, PrefabCatalog.materialFor(PrefabType.BOWL, null))
        assertNull(PrefabCatalog.materialFor(PrefabType.POINT_LIGHT, MaterialType.CONCRETE))
        assertNotNull(PrefabCatalog.shape(PrefabType.HALF_PIPE))
        assertNull(PrefabCatalog.shape(PrefabType.SKATER))
    }

}
