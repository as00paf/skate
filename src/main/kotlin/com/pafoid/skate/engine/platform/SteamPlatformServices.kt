package com.pafoid.skate.engine.platform

import com.codedisaster.steamworks.SteamAPI
import com.codedisaster.steamworks.SteamAPICall
import com.codedisaster.steamworks.SteamAuth
import com.codedisaster.steamworks.SteamAuthTicket
import com.codedisaster.steamworks.SteamFriends
import com.codedisaster.steamworks.SteamFriendsCallback
import com.codedisaster.steamworks.SteamID
import com.codedisaster.steamworks.SteamLeaderboardEntriesHandle
import com.codedisaster.steamworks.SteamLeaderboardEntry
import com.codedisaster.steamworks.SteamLeaderboardHandle
import com.codedisaster.steamworks.SteamMatchmaking
import com.codedisaster.steamworks.SteamMatchmakingCallback
import com.codedisaster.steamworks.SteamNativeHandle
import com.codedisaster.steamworks.SteamResult
import com.codedisaster.steamworks.SteamUser
import com.codedisaster.steamworks.SteamUserCallback
import com.codedisaster.steamworks.SteamUserStats
import com.codedisaster.steamworks.SteamUserStatsCallback
import com.pafoid.skate.engine.net.PeerNetwork

/**
 * Steamworks (steamworks4j) backed services. Requires the Steam client running, `steam_appid.txt` (or a launch
 * through Steam) and the Steamworks redistributable next to the game. Use [tryCreate]; any failure returns null
 * so the caller falls back to [NoOpPlatformServices].
 */
class SteamPlatformServices private constructor() : PlatformServices {
    override val name = "Steam"
    override val isPlatformAvailable = true

    private val boardHandles = HashMap<String, SteamLeaderboardHandle>()
    private val boardsFinding = HashMap<String, MutableList<(SteamLeaderboardHandle?) -> Unit>>()
    private val uploads = HashMap<String, ArrayDeque<(Boolean) -> Unit>>()
    private val downloads = HashMap<String, ArrayDeque<(SteamLeaderboardEntriesHandle, Int) -> Unit>>()

    private var pendingCreate: ((LobbyInfo?) -> Unit)? = null
    private var pendingCreateMax = 0
    private var pendingJoin: Pair<Long, (LobbyInfo?) -> Unit>? = null
    private var pendingList: ((List<LobbyInfo>) -> Unit)? = null
    private var currentLobby: SteamID? = null
    private var currentLobbyInfo: LobbyInfo? = null

    private val statsCallback = object : SteamUserStatsCallback {
        override fun onUserStatsReceived(gameId: Long, user: SteamID, result: SteamResult) {}
        override fun onUserStatsStored(gameId: Long, result: SteamResult) {}
        override fun onUserStatsUnloaded(user: SteamID) {}
        override fun onUserAchievementStored(gameId: Long, isGroup: Boolean, name: String, curProgress: Int, maxProgress: Int) {}
        override fun onLeaderboardFindResult(leaderboard: SteamLeaderboardHandle, found: Boolean) {
            val key = userStats.getLeaderboardName(leaderboard) ?: return
            val waiting = boardsFinding.remove(key) ?: return
            if (found) boardHandles[key] = leaderboard
            waiting.forEach { it(if (found) leaderboard else null) }
        }
        override fun onLeaderboardScoresDownloaded(leaderboard: SteamLeaderboardHandle, entries: SteamLeaderboardEntriesHandle, numEntries: Int) {
            val key = userStats.getLeaderboardName(leaderboard) ?: return
            downloads[key]?.removeFirstOrNull()?.invoke(entries, numEntries)
        }
        override fun onLeaderboardScoreUploaded(success: Boolean, leaderboard: SteamLeaderboardHandle, score: Int, scoreChanged: Boolean, globalRankNew: Int, globalRankPrevious: Int) {
            val key = userStats.getLeaderboardName(leaderboard) ?: return
            uploads[key]?.removeFirstOrNull()?.invoke(success)
        }
        override fun onNumberOfCurrentPlayersReceived(success: Boolean, players: Int) {}
        override fun onGlobalStatsReceived(gameId: Long, result: SteamResult) {}
    }

    private val matchCallback = object : SteamMatchmakingCallback {
        override fun onFavoritesListChanged(ip: Int, queryPort: Int, connPort: Int, appID: Int, flags: Int, add: Boolean, accountID: Int) {}
        override fun onLobbyInvite(user: SteamID, lobby: SteamID, gameID: Long) {}
        override fun onLobbyEnter(lobby: SteamID, chatPermissions: Int, blocked: Boolean, response: SteamMatchmaking.ChatRoomEnterResponse) {
            val join = pendingJoin ?: return
            if (join.first != SteamNativeHandle.getNativeHandle(lobby)) return
            pendingJoin = null
            if (response == SteamMatchmaking.ChatRoomEnterResponse.Success) {
                currentLobby = lobby
                currentLobbyInfo = info(lobby)
            }
            join.second(if (response == SteamMatchmaking.ChatRoomEnterResponse.Success) currentLobbyInfo else null)
        }
        override fun onLobbyDataUpdate(lobby: SteamID, member: SteamID, success: Boolean) {}
        override fun onLobbyChatUpdate(lobby: SteamID, changedUser: SteamID, makingChange: SteamID, stateChange: SteamMatchmaking.ChatMemberStateChange) {
            if (lobby == currentLobby) currentLobbyInfo = info(lobby)
        }
        override fun onLobbyChatMessage(lobby: SteamID, user: SteamID, entryType: SteamMatchmaking.ChatEntryType, chatID: Int) {}
        override fun onLobbyGameCreated(lobby: SteamID, gameServer: SteamID, ip: Int, port: Short) {}
        override fun onLobbyMatchList(lobbiesMatching: Int) {
            val cb = pendingList ?: return
            pendingList = null
            cb((0 until lobbiesMatching).map { info(matchmaking.getLobbyByIndex(it)) })
        }
        override fun onLobbyKicked(lobby: SteamID, admin: SteamID, kickedDueToDisconnect: Boolean) {
            if (lobby == currentLobby) { currentLobby = null; currentLobbyInfo = null }
        }
        override fun onLobbyCreated(result: SteamResult, lobby: SteamID) {
            val cb = pendingCreate ?: return
            pendingCreate = null
            if (result == SteamResult.OK) {
                matchmaking.setLobbyData(lobby, MAX_KEY, pendingCreateMax.toString())
                currentLobby = lobby
                currentLobbyInfo = info(lobby)
                cb(currentLobbyInfo)
            } else cb(null)
        }
        override fun onFavoritesListAccountsUpdated(result: SteamResult) {}
    }

    private val friendsCallback = object : SteamFriendsCallback {
        override fun onSetPersonaNameResponse(success: Boolean, localSuccess: Boolean, result: SteamResult) {}
        override fun onPersonaStateChange(steamID: SteamID, change: SteamFriends.PersonaChange) {}
        override fun onGameOverlayActivated(active: Boolean) {}
        override fun onGameLobbyJoinRequested(steamIDLobby: SteamID, steamIDFriend: SteamID) {}
        override fun onAvatarImageLoaded(steamID: SteamID, image: Int, width: Int, height: Int) {}
        override fun onFriendRichPresenceUpdate(steamIDFriend: SteamID, appID: Int) {}
        override fun onGameRichPresenceJoinRequested(steamIDFriend: SteamID, connect: String) {}
        override fun onGameServerChangeRequested(server: String, password: String) {}
    }

    private val userStats: SteamUserStats = SteamUserStats(statsCallback)
    private val friends: SteamFriends = SteamFriends(friendsCallback)
    private val matchmaking: SteamMatchmaking = SteamMatchmaking(matchCallback)

    private val user = SteamUser(object : SteamUserCallback {
        override fun onAuthSessionTicket(authTicket: SteamAuthTicket, result: SteamResult) {}
        override fun onValidateAuthTicket(steamID: SteamID, authSessionResponse: SteamAuth.AuthSessionResponse, ownerSteamID: SteamID) {}
        override fun onMicroTxnAuthorization(appID: Int, orderID: Long, authorized: Boolean) {}
        override fun onEncryptedAppTicket(result: SteamResult) {}
    })

    private val steamPeers = SteamPeerNetwork(SteamNativeHandle.getNativeHandle(user.steamID), ::isCurrentLobbyMember)
    override val peerNetwork: PeerNetwork = steamPeers

    private fun isCurrentLobbyMember(peer: Long): Boolean {
        val lobby = currentLobby ?: return false
        return (0 until matchmaking.getNumLobbyMembers(lobby)).any {
            SteamNativeHandle.getNativeHandle(matchmaking.getLobbyMemberByIndex(lobby, it)) == peer
        }
    }

    init { userStats.requestCurrentStats() }

    private fun info(lobby: SteamID) = LobbyInfo(
        SteamNativeHandle.getNativeHandle(lobby),
        matchmaking.getNumLobbyMembers(lobby),
        matchmaking.getLobbyData(lobby, MAX_KEY)?.toIntOrNull() ?: 0,
    )

    override val localUserName: String get() = friends.personaName

    override val achievements: Achievements = object : Achievements {
        override fun unlock(id: String) = userStats.setAchievement(id) && userStats.storeStats()
        override fun isUnlocked(id: String) = userStats.isAchieved(id, false)
        override fun clear(id: String) = userStats.clearAchievement(id) && userStats.storeStats()
    }

    override val stats: Stats = object : Stats {
        override fun getInt(name: String, default: Int) = userStats.getStatI(name, default)
        override fun setInt(name: String, value: Int) = userStats.setStatI(name, value)
        override fun getFloat(name: String, default: Float) = userStats.getStatF(name, default)
        override fun setFloat(name: String, value: Float) = userStats.setStatF(name, value)
        override fun store() = userStats.storeStats()
    }

    override val leaderboards: Leaderboards = object : Leaderboards {
        private fun withBoard(board: String, order: LeaderboardOrder, use: (SteamLeaderboardHandle?) -> Unit) {
            boardHandles[board]?.let { return use(it) }
            val waiting = boardsFinding.getOrPut(board) { mutableListOf() }
            waiting += use
            if (waiting.size == 1) {
                val sort = if (order == LeaderboardOrder.ASCENDING) SteamUserStats.LeaderboardSortMethod.Ascending else SteamUserStats.LeaderboardSortMethod.Descending
                userStats.findOrCreateLeaderboard(board, sort, SteamUserStats.LeaderboardDisplayType.Numeric)
            }
        }

        override fun submitScore(board: String, score: Int, order: LeaderboardOrder, onDone: (Boolean) -> Unit) {
            withBoard(board, order) { handle ->
                if (handle == null) return@withBoard onDone(false)
                uploads.getOrPut(board) { ArrayDeque() }.addLast(onDone)
                userStats.uploadLeaderboardScore(handle, SteamUserStats.LeaderboardUploadScoreMethod.KeepBest, score, IntArray(0))
            }
        }

        override fun fetchTop(board: String, count: Int, order: LeaderboardOrder, onResult: (List<LeaderboardEntry>) -> Unit) {
            withBoard(board, order) { handle ->
                if (handle == null) return@withBoard onResult(emptyList())
                downloads.getOrPut(board) { ArrayDeque() }.addLast { entries, n ->
                    val list = ArrayList<LeaderboardEntry>(n)
                    for (i in 0 until n) {
                        val e = SteamLeaderboardEntry()
                        if (userStats.getDownloadedLeaderboardEntry(entries, i, e, IntArray(0))) {
                            list += LeaderboardEntry(e.globalRank, SteamNativeHandle.getNativeHandle(e.steamIDUser), e.score)
                        }
                    }
                    onResult(list)
                }
                userStats.downloadLeaderboardEntries(handle, SteamUserStats.LeaderboardDataRequest.Global, 1, count)
            }
        }
    }

    override val richPresence: RichPresence = object : RichPresence {
        override fun set(key: String, value: String) = friends.setRichPresence(key, value)
        override fun clear() = friends.clearRichPresence()
    }

    override val lobbies: Lobbies = object : Lobbies {
        override val current: LobbyInfo? get() = currentLobbyInfo

        override fun create(maxMembers: Int, visibility: LobbyVisibility, onResult: (LobbyInfo?) -> Unit) {
            if (pendingCreate != null) return onResult(null)
            pendingCreate = onResult
            pendingCreateMax = maxMembers
            val type = when (visibility) {
                LobbyVisibility.PRIVATE -> SteamMatchmaking.LobbyType.Private
                LobbyVisibility.FRIENDS_ONLY -> SteamMatchmaking.LobbyType.FriendsOnly
                LobbyVisibility.PUBLIC -> SteamMatchmaking.LobbyType.Public
            }
            matchmaking.createLobby(type, maxMembers)
        }

        override fun join(lobbyId: Long, onResult: (LobbyInfo?) -> Unit) {
            if (pendingJoin != null) return onResult(null)
            pendingJoin = lobbyId to onResult
            matchmaking.joinLobby(SteamID.createFromNativeHandle(lobbyId))
        }

        override fun leave() {
            currentLobby?.let { matchmaking.leaveLobby(it) }
            currentLobby = null
            currentLobbyInfo = null
        }

        override fun setData(key: String, value: String): Boolean = currentLobby?.let { matchmaking.setLobbyData(it, key, value) } ?: false
        override fun getData(key: String): String? = currentLobby?.let { matchmaking.getLobbyData(it, key) }?.takeIf { it.isNotEmpty() }

        override fun list(onResult: (List<LobbyInfo>) -> Unit) {
            if (pendingList != null) return onResult(emptyList())
            pendingList = onResult
            matchmaking.requestLobbyList()
        }
    }

    override fun update() = SteamAPI.runCallbacks()

    override fun shutdown() {
        leave()
        steamPeers.dispose(); user.dispose()
        userStats.dispose(); friends.dispose(); matchmaking.dispose()
        SteamAPI.shutdown()
    }

    private fun leave() = lobbies.leave()

    companion object {
        private const val MAX_KEY = "skate_max_members"

        /** Returns null if Steam is not running/initializable; throws only for unexpected linkage problems (handled by the factory). */
        fun tryCreate(): SteamPlatformServices? {
            SteamAPI.loadLibraries()
            if (!SteamAPI.init()) return null
            return SteamPlatformServices()
        }
    }
}
