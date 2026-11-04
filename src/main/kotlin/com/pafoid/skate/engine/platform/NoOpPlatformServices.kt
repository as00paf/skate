package com.pafoid.skate.engine.platform

import com.pafoid.skate.engine.net.PeerNetwork

/**
 * Fallback used when no platform is present. Achievements, stats and the leaderboard live in memory for the
 * session so game logic behaves the same with and without Steam; lobbies and peer networking are unavailable.
 * Callbacks are deferred to [update] to match the real backends.
 */
class NoOpPlatformServices(override val localUserName: String = System.getProperty("user.name") ?: "Player") : PlatformServices {
    private val deferred = ArrayDeque<() -> Unit>()

    override val name = "None"
    override val isPlatformAvailable = false

    override val achievements = object : Achievements {
        private val unlocked = HashSet<String>()
        override fun unlock(id: String) = unlocked.add(id).let { true }
        override fun isUnlocked(id: String) = id in unlocked
        override fun clear(id: String) = unlocked.remove(id)
    }

    override val stats = object : Stats {
        private val ints = HashMap<String, Int>()
        private val floats = HashMap<String, Float>()
        override fun getInt(name: String, default: Int) = ints[name] ?: default
        override fun setInt(name: String, value: Int) = true.also { ints[name] = value }
        override fun getFloat(name: String, default: Float) = floats[name] ?: default
        override fun setFloat(name: String, value: Float) = true.also { floats[name] = value }
        override fun store() = true
    }

    override val leaderboards = object : Leaderboards {
        private val boards = HashMap<String, MutableList<Int>>()

        override fun submitScore(board: String, score: Int, order: LeaderboardOrder, onDone: (Boolean) -> Unit) {
            boards.getOrPut(board) { mutableListOf() }.add(score)
            deferred += { onDone(true) }
        }

        override fun fetchTop(board: String, count: Int, order: LeaderboardOrder, onResult: (List<LeaderboardEntry>) -> Unit) {
            val sorted = (boards[board] ?: emptyList<Int>()).let { if (order == LeaderboardOrder.DESCENDING) it.sortedDescending() else it.sorted() }
            val result = sorted.take(count).mapIndexed { i, s -> LeaderboardEntry(i + 1, 0L, s) }
            deferred += { onResult(result) }
        }
    }

    override val richPresence = object : RichPresence {
        val values = HashMap<String, String>()
        override fun set(key: String, value: String) = true.also { values[key] = value }
        override fun clear() = values.clear()
    }

    override val lobbies = object : Lobbies {
        override val current: LobbyInfo? = null
        override fun create(maxMembers: Int, visibility: LobbyVisibility, onResult: (LobbyInfo?) -> Unit) { deferred += { onResult(null) } }
        override fun join(lobbyId: Long, onResult: (LobbyInfo?) -> Unit) { deferred += { onResult(null) } }
        override fun leave() {}
        override fun setData(key: String, value: String) = false
        override fun getData(key: String): String? = null
        override fun list(onResult: (List<LobbyInfo>) -> Unit) { deferred += { onResult(emptyList()) } }
    }

    /** No platform peers: multiplayer falls back to direct UDP (see `LobbySession`). */
    override val peerNetwork: PeerNetwork? = null

    override fun update() {
        while (deferred.isNotEmpty()) deferred.removeFirst()()
    }

    override fun shutdown() { deferred.clear() }
}

/** Creates the best available [PlatformServices]: Steam when it initializes, otherwise [NoOpPlatformServices]. */
object PlatformServicesFactory {
    /** Set to false (or pass `-Dskate.steam=false`) to force the fallback. */
    fun create(
        steamEnabled: Boolean = System.getProperty("skate.steam") != "false",
        steam: () -> PlatformServices? = { SteamPlatformServices.tryCreate() },
        onFallback: (reason: String) -> Unit = {},
    ): PlatformServices {
        if (!steamEnabled) {
            onFallback("Steam disabled")
            return NoOpPlatformServices()
        }
        return try {
            steam() ?: NoOpPlatformServices().also { onFallback("Steam not available") }
        } catch (t: Throwable) { // includes NoClassDefFoundError / UnsatisfiedLinkError when natives are missing
            onFallback("Steam failed: ${t.javaClass.simpleName}: ${t.message}")
            NoOpPlatformServices()
        }
    }
}
