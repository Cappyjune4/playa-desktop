package com.cappyjune.playa.desktop.data

import com.cappyjune.playa.desktop.playback.JfRef
import com.cappyjune.playa.desktop.playback.Media
import com.cappyjune.playa.desktop.playback.Trick
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Turning a Jellyfin item into something mpv plays, and telling the server how it's going.
 * mpv plays nearly every container and codec, so the device profile asks for the original file
 * as it is (direct play); the server only transcodes if it can't hand the file over.
 */
object JellyfinPlay {
    private fun profile() = buildJsonObject {
        put("Name", "Playa Desktop")
        put("MaxStreamingBitrate", 400_000_000); put("MaxStaticBitrate", 400_000_000)
        // No codec or container lists: anything goes, mpv sorts it out.
        put("DirectPlayProfiles", buildJsonArray { add(buildJsonObject { put("Type", "Video") }) })
        put("TranscodingProfiles", buildJsonArray {
            add(buildJsonObject {
                put("Type", "Video"); put("Container", "ts"); put("Protocol", "hls"); put("Context", "Streaming")
                put("VideoCodec", "hevc,h264"); put("AudioCodec", "aac,ac3,eac3"); put("MaxAudioChannels", "8")
            })
        })
        // Subtitles: external files as files, everything in the container plays as it is.
        put("SubtitleProfiles", buildJsonArray {
            listOf("srt", "subrip", "ass", "ssa", "vtt", "webvtt").forEach { f -> add(buildJsonObject { put("Format", f); put("Method", "External") }) }
            listOf("srt", "subrip", "ass", "ssa", "vtt", "pgssub", "pgs", "dvdsub", "dvbsub", "mov_text", "ttml").forEach { f -> add(buildJsonObject { put("Format", f); put("Method", "Embed") }) }
        })
    }

    /** Relative server paths made absolute, minus any ApiKey: the Authorization header does that job. */
    private fun abs(path: String): String {
        val url = if (path.startsWith("http")) path else Jellyfin.acct().server + path
        return url.replace(Regex("([?&])(api_key|ApiKey)=[^&]*&?"), "$1").trimEnd('&', '?')
    }

    /** The token, for mpv's requests to the server: a header, never in the URL. */
    fun headers(): Map<String, String> = mapOf("Authorization" to Jellyfin.authHeader())

    fun title(i: JfItem) = if (i.Type == "Episode") listOfNotNull(i.episodeCode, i.Name).joinToString("  ") else i.Name.orEmpty()
    fun subtitle(i: JfItem) = if (i.Type == "Episode") i.SeriesName.orEmpty() else listOfNotNull(i.ProductionYear?.toString(), i.OfficialRating).joinToString(" · ")

    /** An episode queued behind the one playing: worked out only when its turn comes. */
    fun placeholder(i: JfItem) = Media("jellyfin:${i.Id}", title(i), subtitle(i), JfApi.still(i), key = "jellyfin:${i.Id}", jf = JfRef(i.Id, seriesId = i.SeriesId))

    /** Ask the server how to play this item here, and build the Media for it. */
    suspend fun resolve(itemId: String, startSeconds: Float?): Media {
        val a = Jellyfin.acct()
        val item = JfApi.item(itemId)
        val body = buildJsonObject {
            put("UserId", a.userId); put("MaxStreamingBitrate", 400_000_000)
            put("StartTimeTicks", ((startSeconds ?: 0f) * 10_000_000).toLong())
            put("DeviceProfile", profile())
            put("EnableDirectPlay", true); put("EnableDirectStream", true); put("EnableTranscoding", true)
            put("AllowVideoStreamCopy", true); put("AllowAudioStreamCopy", true); put("AutoOpenLiveStream", true)
        }.toString()
        val info = Http.json.decodeFromString(JfPlaybackInfo.serializer(), Jellyfin.post(a.server, "/Items/$itemId/PlaybackInfo?userId=${a.userId}", body, a.token))
        info.ErrorCode?.let { error("Jellyfin can't play this here ($it)") }
        val src = info.MediaSources.firstOrNull() ?: error("Jellyfin has no playable version of this")
        val direct = src.SupportsDirectPlay || src.SupportsDirectStream
        val uri = if (direct) "${a.server}/Videos/$itemId/stream?static=true&MediaSourceId=${src.Id}" + (info.PlaySessionId?.let { "&PlaySessionId=$it" } ?: "")
            else abs(src.TranscodingUrl ?: error("Jellyfin offered no way to stream this"))
        // External subtitle files (the ones inside the file mpv finds itself).
        val subs = src.MediaStreams.orEmpty().filter { it.Type == "Subtitle" && it.DeliveryUrl != null && (it.IsExternal || it.DeliveryMethod == "External") }
            .map { abs(it.DeliveryUrl!!) }
        val trick = item.Trickplay?.get(src.Id)?.values?.minByOrNull { kotlin.math.abs(it.Width - 320) }?.let {
            Trick(it.Width, it.Height, it.TileWidth, it.TileHeight, it.ThumbnailCount, it.Interval)
        }
        return Media(uri, title(item), subtitle(item), JfApi.backdrop(item), headers(), key = "jellyfin:$itemId", subtitles = subs, startSeconds = startSeconds,
            jf = JfRef(itemId, src.Id, info.PlaySessionId, if (src.SupportsDirectPlay) "DirectPlay" else if (direct) "DirectStream" else "Transcode", item.SeriesId, trick))
    }

    /**
     * What to play for an item: a film or episode itself (from where it was left, unless [fromStart]),
     * a show or season from its next unwatched episode; episodes carry on into the rest of the show.
     */
    suspend fun queueFor(item: JfItem, fromStart: Boolean): List<Media> {
        val target = when (item.Type) {
            "Series", "Season" -> {
                val eps = JfApi.episodes(item.SeriesId ?: item.Id, seasonId = if (item.Type == "Season") item.Id else null)
                eps.firstOrNull { !it.played } ?: eps.firstOrNull() ?: error("No episodes to play")
            }
            else -> item
        }
        val resume = if (fromStart) 0f else target.resumeMs.takeIf { it > 15_000 && !target.played }?.let { it / 1000f } ?: 0f
        val first = resolve(target.Id, resume)
        val next = if (target.Type == "Episode" && target.SeriesId != null)
            runCatching { JfApi.episodes(target.SeriesId, startAt = target.Id, limit = 13) }.getOrDefault(emptyList()).drop(1).map(::placeholder)
        else emptyList()
        return listOf(first) + next
    }

    // --- reporting: Jellyfin's resume points, "watched" and its dashboard follow these ---------------

    private suspend fun report(path: String, j: JfRef, positionSeconds: Float, paused: Boolean) {
        val a = Jellyfin.account.value ?: return
        runCatching {
            Jellyfin.post(a.server, path, buildJsonObject {
                put("ItemId", j.itemId); j.sourceId?.let { put("MediaSourceId", it) }; j.sessionId?.let { put("PlaySessionId", it) }
                put("PositionTicks", (positionSeconds * 10_000_000).toLong()); put("IsPaused", paused); put("CanSeek", true); put("PlayMethod", j.method)
            }.toString(), a.token)
        }
    }

    suspend fun started(j: JfRef, position: Float) = report("/Sessions/Playing", j, position, false)
    suspend fun progress(j: JfRef, position: Float, paused: Boolean) = report("/Sessions/Playing/Progress", j, position, paused)
    suspend fun stopped(j: JfRef, position: Float) = report("/Sessions/Playing/Stopped", j, position, true)
}
