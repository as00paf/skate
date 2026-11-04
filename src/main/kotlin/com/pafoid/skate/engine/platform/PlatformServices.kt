package com.pafoid.skate.engine.platform

import com.pafoid.skate.engine.net.PeerNetwork

/**
 * Platform (store) services. Game code only talks to this interface; the Steam implementation is optional and
 * [NoOpPlatformServices] is used when Steam is absent. Calls must be made from the main thread; callbacks are
 * invoked from [update] on that same thread.
 */
interface PlatformServices {
    /** Backend name, e.g. "Steam" or "None". */
    val name: String

    /** True when backed by a real platform (false for the local no-op fallback). */
    val isPlatformAvailable: Boolean

    val localUserName: String

    val achievements: Achievements
    val stats: Stats
    val leaderboards: Leaderboards
    val richPresence: RichPresence
    val lobbies: Lobbies

    /** Peer-to-peer datagrams between platform users (NET-05), or null when the platform has none. */
    val peerNetwork: PeerNetwork?

    /** Pumps platform callbacks; call once per frame. */
    fun update()

    fun shutdown()
}

interface Achievements {
    fun unlock(id: String): Boolean
    fun isUnlocked(id: String): Boolean
    fun clear(id: String): Boolean
}

interface Stats {
    fun getInt(name: String, default: Int = 0): Int
    fun setInt(name: String, value: Int): Boolean
    fun getFloat(name: String, default: Float = 0f): Float
    fun setFloat(name: String, value: Float): Boolean

    /** Persists changed stats/achievements. */
    fun store(): Boolean
}

data class LeaderboardEntry(val rank: Int, val userId: Long, val score: Int)

enum class LeaderboardOrder { ASCENDING, DESCENDING }

interface Leaderboards {
    /** [onDone] receives true if the score was submitted. */
    fun submitScore(board: String, score: Int, order: LeaderboardOrder = LeaderboardOrder.DESCENDING, onDone: (Boolean) -> Unit = {})

    /** Delivers up to [count] best entries (rank order); an empty list on failure. */
    fun fetchTop(board: String, count: Int, order: LeaderboardOrder = LeaderboardOrder.DESCENDING, onResult: (List<LeaderboardEntry>) -> Unit)
}

interface RichPresence {
    fun set(key: String, value: String): Boolean
    fun clear()
}

enum class LobbyVisibility { PRIVATE, FRIENDS_ONLY, PUBLIC }

/** [maxMembers] is 0 if unknown. */
data class LobbyInfo(val id: Long, val memberCount: Int, val maxMembers: Int)

interface Lobbies {
    val current: LobbyInfo?

    /** [onResult] receives the new lobby, or null on failure. */
    fun create(maxMembers: Int, visibility: LobbyVisibility, onResult: (LobbyInfo?) -> Unit)

    /** [onResult] receives the joined lobby, or null on failure. */
    fun join(lobbyId: Long, onResult: (LobbyInfo?) -> Unit)

    fun leave()
    fun setData(key: String, value: String): Boolean
    fun getData(key: String): String?

    /** Finds public lobbies. */
    fun list(onResult: (List<LobbyInfo>) -> Unit)
}
