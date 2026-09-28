package com.cappyjune.playa.desktop.data

import kotlinx.serialization.Serializable

/** The slice of Jellyfin's BaseItemDto that Playa shows: movies, shows, seasons, episodes, people, libraries. */
@Serializable
data class JfItem(
    val Id: String,
    val Name: String? = null,
    val Type: String? = null,
    val CollectionType: String? = null,
    val Overview: String? = null,
    val Taglines: List<String>? = null,
    val ProductionYear: Int? = null,
    val PremiereDate: String? = null,
    val EndDate: String? = null,
    val Status: String? = null,
    val RunTimeTicks: Long? = null,
    val OfficialRating: String? = null,
    val CommunityRating: Float? = null,
    val CriticRating: Float? = null,
    val Genres: List<String>? = null,
    val Studios: List<JfNamed>? = null,
    val People: List<JfPerson>? = null,
    val SeriesName: String? = null,
    val SeriesId: String? = null,
    val SeasonId: String? = null,
    val SeasonName: String? = null,
    val IndexNumber: Int? = null,
    val ParentIndexNumber: Int? = null,
    val ChildCount: Int? = null,
    val ImageTags: Map<String, String>? = null,
    val BackdropImageTags: List<String>? = null,
    val ParentBackdropItemId: String? = null,
    val ParentBackdropImageTags: List<String>? = null,
    val ParentThumbItemId: String? = null,
    val ParentThumbImageTag: String? = null,
    val SeriesPrimaryImageTag: String? = null,
    val PrimaryImageAspectRatio: Double? = null,
    val UserData: JfUserData? = null,
    val MediaSources: List<JfMediaSource>? = null,
    val Chapters: List<JfChapter>? = null,
    /** mediaSourceId -> width -> tiles, for seek-bar thumbnails. */
    val Trickplay: Map<String, Map<String, JfTrickplay>>? = null,
    val LocationType: String? = null,
    val MediaType: String? = null,
) {
    val isFolder get() = Type in setOf("Series", "Season", "BoxSet", "CollectionFolder", "Folder", "UserView", "Playlist")
    val playable get() = Type in setOf("Movie", "Episode", "Video", "MusicVideo", "TvChannel", "Trailer")
    val runtimeMs get() = (RunTimeTicks ?: 0) / 10_000
    val resumeMs get() = (UserData?.PlaybackPositionTicks ?: 0) / 10_000
    val played get() = UserData?.Played == true
    val favourite get() = UserData?.IsFavorite == true
    /** "S2 · E5" for episodes. */
    val episodeCode get() = if (Type == "Episode") listOfNotNull(ParentIndexNumber?.let { "S$it" }, IndexNumber?.let { "E$it" }).joinToString(" · ") else null
}

@Serializable data class JfNamed(val Name: String? = null, val Id: String? = null)
@Serializable data class JfPerson(val Name: String? = null, val Id: String? = null, val Role: String? = null, val Type: String? = null, val PrimaryImageTag: String? = null)
@Serializable data class JfUserData(
    val PlaybackPositionTicks: Long = 0, val PlayedPercentage: Double? = null, val Played: Boolean = false,
    val IsFavorite: Boolean = false, val UnplayedItemCount: Int? = null, val LastPlayedDate: String? = null,
)
@Serializable data class JfChapter(val StartPositionTicks: Long = 0, val Name: String? = null)
@Serializable data class JfTrickplay(
    val Width: Int = 0, val Height: Int = 0, val TileWidth: Int = 10, val TileHeight: Int = 10,
    val ThumbnailCount: Int = 0, val Interval: Int = 10_000,
)

@Serializable
data class JfMediaSource(
    val Id: String,
    val Name: String? = null,
    val Container: String? = null,
    val Size: Long? = null,
    val Bitrate: Long? = null,
    val SupportsDirectPlay: Boolean = false,
    val SupportsDirectStream: Boolean = false,
    val SupportsTranscoding: Boolean = false,
    val TranscodingUrl: String? = null,
    val MediaStreams: List<JfStream>? = null,
    val DefaultAudioStreamIndex: Int? = null,
    val DefaultSubtitleStreamIndex: Int? = null,
    val RunTimeTicks: Long? = null,
)

@Serializable
data class JfStream(
    val Index: Int = 0,
    val Type: String? = null,
    val Codec: String? = null,
    val Language: String? = null,
    val DisplayTitle: String? = null,
    val Title: String? = null,
    val IsDefault: Boolean = false,
    val IsForced: Boolean = false,
    val IsExternal: Boolean = false,
    val IsTextSubtitleStream: Boolean = false,
    val DeliveryMethod: String? = null,
    val DeliveryUrl: String? = null,
    val Width: Int? = null,
    val Height: Int? = null,
    val Channels: Int? = null,
    val VideoRangeType: String? = null,
)

@Serializable data class JfItems(val Items: List<JfItem> = emptyList(), val TotalRecordCount: Int = 0)
@Serializable data class JfPlaybackInfo(val MediaSources: List<JfMediaSource> = emptyList(), val PlaySessionId: String? = null, val ErrorCode: String? = null)
@Serializable data class JfSegment(val Type: String = "Unknown", val StartTicks: Long = 0, val EndTicks: Long = 0) {
    val startMs get() = StartTicks / 10_000
    val endMs get() = EndTicks / 10_000
}
@Serializable data class JfSegments(val Items: List<JfSegment> = emptyList())
