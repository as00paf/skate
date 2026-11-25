package com.pafoid.skate.engine.platform.steampipe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SteamPublishTargetTest {

    @Test
    fun SteamPublishTarget_DefaultIsInvalidUntilIdsAreSet() {
        assertEquals(listOf(SteamPublishIssue.APP_ID_INVALID, SteamPublishIssue.DEPOT_ID_INVALID), SteamPublishTarget().issues())
        assertTrue(SteamPublishTarget(appId = 480, depotId = 481).isValid)
    }

    @Test
    fun SteamPublishTarget_IdsAreUnsigned32BitAndDistinct() {
        assertEquals(listOf(SteamPublishIssue.APP_ID_INVALID), SteamPublishTarget(appId = 0x1_0000_0000L, depotId = 481).issues())
        assertEquals(listOf(SteamPublishIssue.DEPOT_ID_SAME_AS_APP_ID), SteamPublishTarget(appId = 480, depotId = 480).issues())
        assertTrue(SteamPublishTarget(appId = 0xFFFF_FFFFL, depotId = 1).isValid)
    }

    @Test
    fun SteamPublishTarget_DescriptionAndExclusionsAreChecked() {
        val ok = SteamPublishTarget(appId = 480, depotId = 481)
        assertEquals(listOf(SteamPublishIssue.DESCRIPTION_EMPTY), ok.copy(descriptionTemplate = "  ").issues())
        listOf("", "../x", "a/../b", "C:\\x", "/abs", "a\"b", "a\nb").forEach {
            assertEquals(listOf(SteamPublishIssue.EXCLUSION_INVALID), ok.copy(exclusions = listOf(it)).issues(), it)
        }
        assertTrue(ok.copy(exclusions = listOf("*.pdb", "logs/*", "Data\\debug.txt")).isValid)
    }

    @Test
    fun SteamPublishTarget_DefaultExclusionsKeepDevFilesOutOfTheDepot() {
        assertTrue("steam_appid.txt" in SteamPublishTarget().exclusions)
    }

    @Test
    fun SteamPublishTarget_DescribeFillsPlaceholders() {
        val target = SteamPublishTarget(descriptionTemplate = "{project} v{version} on {date} {other}")
        assertEquals("Skate v1.2 on 2026-11-18 {other}", target.describe("Skate", "1.2", "2026-11-18"))
    }

    @Test
    fun SteamPublishTarget_ParseSteamIdAcceptsDigitsInRangeOnly() {
        assertEquals(480L, SteamPublishTarget.parseSteamId(" 480 "))
        assertEquals(4294967295L, SteamPublishTarget.parseSteamId("4294967295"))
        listOf("", "0", "-1", "4294967296", "12a", "99999999999").forEach { assertNull(SteamPublishTarget.parseSteamId(it), it) }
        assertFalse(SteamPublishTarget.isSteamId(0))
    }
}
