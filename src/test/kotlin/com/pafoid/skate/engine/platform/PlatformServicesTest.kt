package com.pafoid.skate.engine.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlatformServicesTest {
    private val svc: PlatformServices = NoOpPlatformServices("tester")

    @Test
    fun Platform_NoOpReportsNoPlatformAndKeepsLocalState() {
        assertFalse(svc.isPlatformAvailable)
        assertEquals("tester", svc.localUserName)
        assertFalse(svc.achievements.isUnlocked("A"))
        assertTrue(svc.achievements.unlock("A"))
        assertTrue(svc.achievements.isUnlocked("A"))
        svc.achievements.clear("A")
        assertFalse(svc.achievements.isUnlocked("A"))

        assertEquals(7, svc.stats.getInt("s", 7))
        svc.stats.setInt("s", 3); svc.stats.setFloat("f", 1.5f)
        assertEquals(3, svc.stats.getInt("s")); assertEquals(1.5f, svc.stats.getFloat("f"))
        assertTrue(svc.stats.store())

        assertTrue(svc.richPresence.set("status", "skating"))
        svc.richPresence.clear()
    }

    @Test
    fun Platform_NoOpLeaderboardRanksAndDefersCallbacksToUpdate() {
        var done: Boolean? = null
        listOf(10, 50, 30).forEach { svc.leaderboards.submitScore("b", it) }
        svc.leaderboards.submitScore("b", 20) { done = it }
        assertNull(done)
        var top: List<LeaderboardEntry>? = null
        svc.leaderboards.fetchTop("b", 3) { top = it }
        svc.update()
        assertEquals(true, done)
        assertEquals(listOf(50, 30, 20), top!!.map { it.score })
        assertEquals(listOf(1, 2, 3), top!!.map { it.rank })

        var asc: List<LeaderboardEntry>? = null
        svc.leaderboards.fetchTop("b", 1, LeaderboardOrder.ASCENDING) { asc = it }
        svc.update()
        assertEquals(10, asc!!.single().score)
    }

    @Test
    fun Platform_NoOpLobbiesFailGracefully() {
        var created: LobbyInfo? = LobbyInfo(1, 1, 1); var joined: LobbyInfo? = LobbyInfo(1, 1, 1); var listed: List<LobbyInfo>? = null
        svc.lobbies.create(4, LobbyVisibility.PUBLIC) { created = it }
        svc.lobbies.join(42) { joined = it }
        svc.lobbies.list { listed = it }
        svc.update()
        assertNull(created); assertNull(joined); assertEquals(emptyList(), listed)
        assertNull(svc.lobbies.current)
        assertNull(svc.peerNetwork)
        assertFalse(svc.lobbies.setData("k", "v")); assertNull(svc.lobbies.getData("k"))
    }

    @Test
    fun PlatformFactory_UsesSteamWhenItInitializes() {
        val steam = NoOpPlatformServices("steam-like")
        assertSame(steam, PlatformServicesFactory.create(steamEnabled = true, steam = { steam }))
    }

    @Test
    fun PlatformFactory_FallsBackWhenDisabledMissingOrBroken() {
        val reasons = ArrayList<String>()
        val off = PlatformServicesFactory.create(steamEnabled = false, steam = { error("must not be called") }, onFallback = { reasons += it })
        val none = PlatformServicesFactory.create(steamEnabled = true, steam = { null }, onFallback = { reasons += it })
        val noClass = PlatformServicesFactory.create(steamEnabled = true, steam = { throw NoClassDefFoundError("com/codedisaster/steamworks/SteamAPI") }, onFallback = { reasons += it })
        val noLink = PlatformServicesFactory.create(steamEnabled = true, steam = { throw UnsatisfiedLinkError("steamworks4j") }, onFallback = { reasons += it })
        listOf(off, none, noClass, noLink).forEach { assertFalse(it.isPlatformAvailable); assertEquals("None", it.name) }
        assertEquals(4, reasons.size)
        assertTrue(reasons[2].contains("NoClassDefFoundError"))
    }

    @Test
    fun PlatformSystem_PumpsCallbacksEachFrameAndShutsDown() {
        var updates = 0; var down = false
        val delegate = NoOpPlatformServices()
        val counting = object : PlatformServices by delegate {
            override fun update() { updates++ }
            override fun shutdown() { down = true }
        }
        val sys = PlatformSystem(counting)
        sys.update(0.016f); sys.update(0.016f)
        sys.destroy()
        assertEquals(2, updates); assertTrue(down)
    }
}
