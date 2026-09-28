package com.cappyjune.playa.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Jellyfin's admin dashboard, for an administrator's account: sessions and transcodes, tasks,
 * the activity log, libraries and storage. Device records carry access tokens; they're never
 * read into Playa (the DTO below simply has no such field).
 */
object JfAdmin {
    @Serializable data class SystemInfo(
        val ServerName: String? = null, val Version: String? = null, val OperatingSystemDisplayName: String? = null,
        val SystemArchitecture: String? = null, val HasPendingRestart: Boolean = false, val HasUpdateAvailable: Boolean = false,
        val CanSelfRestart: Boolean = false, val LocalAddress: String? = null,
    )
    @Serializable data class Transcode(
        val VideoCodec: String? = null, val AudioCodec: String? = null, val Container: String? = null, val IsVideoDirect: Boolean = false,
        val IsAudioDirect: Boolean = false, val Bitrate: Long? = null, val Framerate: Float? = null, val CompletionPercentage: Double? = null,
        val Width: Int? = null, val Height: Int? = null, val AudioChannels: Int? = null, val HardwareAccelerationType: String? = null,
        val TranscodeReasons: List<String>? = null,
    )
    @Serializable data class PlayState(val PositionTicks: Long? = null, val IsPaused: Boolean = false, val PlayMethod: String? = null)
    @Serializable data class Session(
        val Id: String, val UserName: String? = null, val Client: String? = null, val DeviceName: String? = null,
        val ApplicationVersion: String? = null, val LastActivityDate: String? = null, val RemoteEndPoint: String? = null,
        val NowPlayingItem: JfItem? = null, val PlayState: PlayState? = null, val TranscodingInfo: Transcode? = null,
        val SupportsRemoteControl: Boolean = false, val IsActive: Boolean = true,
    )
    @Serializable data class TaskResult(val EndTimeUtc: String? = null, val Status: String? = null, val ErrorMessage: String? = null)
    @Serializable data class Task(
        val Id: String, val Name: String? = null, val State: String? = null, val CurrentProgressPercentage: Double? = null,
        val Category: String? = null, val Description: String? = null, val IsHidden: Boolean = false, val LastExecutionResult: TaskResult? = null,
    )
    @Serializable data class Activity(val Id: Long = 0, val Name: String? = null, val ShortOverview: String? = null, val Overview: String? = null,
        val Type: String? = null, val Date: String? = null, val Severity: String? = null)
    @Serializable private data class ActivityPage(val Items: List<Activity> = emptyList())
    @Serializable data class Counts(val MovieCount: Int = 0, val SeriesCount: Int = 0, val EpisodeCount: Int = 0, val SongCount: Int = 0,
        val AlbumCount: Int = 0, val BoxSetCount: Int = 0, val MusicVideoCount: Int = 0)
    @Serializable data class Folder(val Path: String? = null, val FreeSpace: Long = -1, val UsedSpace: Long = -1)
    @Serializable data class LibraryStorage(val Id: String? = null, val Name: String? = null, val Folders: List<Folder> = emptyList())
    @Serializable data class Storage(val Libraries: List<LibraryStorage> = emptyList(), val TranscodingTempFolder: Folder? = null, val CacheFolder: Folder? = null)
    @Serializable data class Policy(val IsAdministrator: Boolean = false, val IsDisabled: Boolean = false)
    @Serializable data class User(val Id: String, val Name: String? = null, val LastActivityDate: String? = null, val Policy: Policy? = null)
    @Serializable data class Plugin(val Name: String? = null, val Version: String? = null, val Status: String? = null)
    @Serializable data class VirtualFolder(val Name: String? = null, val ItemId: String? = null, val CollectionType: String? = null, val RefreshProgress: Double? = null, val RefreshStatus: String? = null)

    private val _isAdmin = MutableStateFlow(false)
    /** Whether to show the Dashboard at all. */
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    suspend fun checkAdmin() {
        _isAdmin.value = runCatching { Http.json.decodeFromString(User.serializer(), get("/Users/Me")).Policy?.IsAdministrator == true }.getOrDefault(false)
    }

    private val a get() = Jellyfin.acct()
    private suspend fun get(path: String) = Jellyfin.get(a.server, path, a.token)
    private inline fun <reified T> list(s: kotlinx.serialization.KSerializer<T>, json: String) = Http.json.decodeFromString(ListSerializer(s), json)

    suspend fun system() = Http.json.decodeFromString(SystemInfo.serializer(), get("/System/Info"))
    suspend fun sessions() = list(Session.serializer(), get("/Sessions?activeWithinSeconds=960"))
    suspend fun tasks() = list(Task.serializer(), get("/ScheduledTasks?isHidden=false"))
    suspend fun activity() = Http.json.decodeFromString(ActivityPage.serializer(), get("/System/ActivityLog/Entries?limit=40")).Items
    suspend fun counts() = Http.json.decodeFromString(Counts.serializer(), get("/Items/Counts"))
    suspend fun storage() = runCatching { Http.json.decodeFromString(Storage.serializer(), get("/System/Info/Storage")) }.getOrNull()
    suspend fun users() = list(User.serializer(), get("/Users"))
    suspend fun plugins() = list(Plugin.serializer(), get("/Plugins"))
    suspend fun libraries() = list(VirtualFolder.serializer(), get("/Library/VirtualFolders"))

    suspend fun scanAll() { Jellyfin.send("POST", "/Library/Refresh") }
    suspend fun scan(itemId: String) = refresh(itemId, full = false, replace = false)
    /**
     * Jellyfin's "Refresh metadata", on an item and everything under it: [full] false only looks for
     * new, changed and removed files; true also fills in missing details and artwork; [replace]
     * re-fetches every detail over the old ones (artwork is kept either way).
     */
    suspend fun refresh(itemId: String, full: Boolean, replace: Boolean) {
        val mode = if (full) "FullRefresh" else "Default"
        Jellyfin.send("POST", "/Items/$itemId/Refresh?Recursive=true&MetadataRefreshMode=$mode&ImageRefreshMode=$mode&ReplaceAllMetadata=$replace&ReplaceAllImages=false")
    }
    suspend fun runTask(id: String) { Jellyfin.send("POST", "/ScheduledTasks/Running/$id") }
    suspend fun stopTask(id: String) { Jellyfin.send("DELETE", "/ScheduledTasks/Running/$id") }
    suspend fun restart() { Jellyfin.send("POST", "/System/Restart") }
    /** Stop, Pause, Unpause... on someone's player. */
    suspend fun command(sessionId: String, command: String) { Jellyfin.send("POST", "/Sessions/$sessionId/Playing/$command") }
    suspend fun message(sessionId: String, text: String) {
        Jellyfin.send("POST", "/Sessions/$sessionId/Message", buildJsonObject { put("Header", "Message from ${a.userName}"); put("Text", text); put("TimeoutMs", 10_000) }.toString())
    }
}
