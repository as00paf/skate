package com.pafoid.skate.engine.platform.steampipe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppBuildScriptTest {

    private fun script(preview: Boolean = false, description: String = "Skate 1.0 (2026-11-18)") = AppBuildScript(
        appId = 1234560,
        description = description,
        contentRoot = "C:\\Games\\Skate\\content",
        buildOutput = "C:\\Steam\\output\\",
        depots = listOf(DepotMapping(1234561, listOf("steam_appid.txt", "*.pdb"))),
        preview = preview,
    )

    @Test
    fun AppBuildScript_WritesValveLayoutWithoutSetLive() {
        val expected = """
            "AppBuild"
            {
            	"AppID" "1234560"
            	"Desc" "Skate 1.0 (2026-11-18)"
            	"ContentRoot" "C:\Games\Skate\content\"
            	"BuildOutput" "C:\Steam\output\"
            	"Preview" "0"
            	"Depots"
            	{
            		"1234561"
            		{
            			"FileMapping"
            			{
            				"LocalPath" "*"
            				"DepotPath" "."
            				"Recursive" "1"
            			}
            			"FileExclusion" "steam_appid.txt"
            			"FileExclusion" "*.pdb"
            		}
            	}
            }
        """.trimIndent() + "\n"
        assertEquals(expected, script().toVdf())
        assertFalse(script().toVdf().contains("SetLive"))
        assertEquals("app_build_1234560.vdf", script().fileName)
    }

    @Test
    fun AppBuildScript_PreviewIsADryRun() {
        assertTrue(script(preview = true).toVdf().contains("\t\"Preview\" \"1\"\n"))
        assertTrue(script(preview = false).toVdf().contains("\t\"Preview\" \"0\"\n"))
    }

    @Test
    fun AppBuildScript_IsDeterministic() {
        val a = AppBuildScript(1, "d", "c", "o", listOf(DepotMapping(9), DepotMapping(3)))
        val b = AppBuildScript(1, "d", "c", "o", listOf(DepotMapping(3), DepotMapping(9)))
        assertEquals(a.toVdf(), b.toVdf())
        assertTrue(a.toVdf().indexOf("\"3\"") < a.toVdf().indexOf("\"9\""))
    }

    @Test
    fun AppBuildScript_SanitizesTheDescription() {
        val vdf = script(description = "say \"hi\"\n\tnow  ok").toVdf()
        assertTrue(vdf.contains("\"Desc\" \"say 'hi' now ok\""), vdf)
        assertEquals(AppBuildScript.MAX_DESCRIPTION, AppBuildScript.sanitizeDescription("x".repeat(500)).length)
    }

    @Test
    fun AppBuildScript_RefusesUnsafeValues() {
        assertThrows<IllegalArgumentException> { script().copy(contentRoot = "C:\\a\"b") }
        assertThrows<IllegalArgumentException> { script().copy(buildOutput = "") }
        assertThrows<IllegalArgumentException> { script().copy(appId = 0) }
        assertThrows<IllegalArgumentException> { script().copy(depots = emptyList()) }
        assertThrows<IllegalArgumentException> { script().copy(depots = listOf(DepotMapping(5), DepotMapping(5))) }
        assertThrows<IllegalArgumentException> { script().copy(depots = listOf(DepotMapping(5, listOf("../x")))) }
    }
}
