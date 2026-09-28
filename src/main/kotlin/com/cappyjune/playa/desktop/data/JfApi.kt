package com.cappyjune.playa.desktop.data

import java.net.URLEncoder

/** Jellyfin's video side: what the Home and Library tabs, detail pages and player ask for. */
object JfApi {
    private const val FIELDS = "Fields=Overview,Genres,PrimaryImageAspectRatio,ChildCount,ProductionYear,OfficialRating,CommunityRating,SeriesPrimaryImageTag,ParentBackdropImageTags" +
        "&EnableImageTypes=Primary,Backdrop,Thumb&EnableUserData=true&ImageTypeLimit=1"
    private val a get() = Jellyfin.acct()
    private suspend fun get(path: String) = Jellyfin.get(a.server, path, a.token)
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private suspend fun list(path: String) = Http.json.decodeFromString(JfItems.serializer(), get(path)).Items

    // --- home -------------------------------------------------------------------------------

    suspend fun views() = list("/UserViews?userId=${a.userId}").filter { it.CollectionType in setOf(null, "movies", "tvshows", "boxsets", "homevideos", "folders", "mixed", "musicvideos") }
    suspend fun resume() = list("/UserItems/Resume?userId=${a.userId}&MediaTypes=Video&Limit=24&$FIELDS")
    suspend fun nextUp() = list("/Shows/NextUp?userId=${a.userId}&Limit=24&EnableResumable=false&EnableRewatching=false&$FIELDS")
    suspend fun latest(parentId: String): List<JfItem> =
        Http.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(JfItem.serializer()),
            get("/Items/Latest?userId=${a.userId}&parentId=$parentId&Limit=24&GroupItems=true&$FIELDS"))

    // --- libraries ------------------------------------------------------------------------------

    enum class Sort(val label: String, val by: String, val order: String) {
        NAME("Name", "SortName", "Ascending"), ADDED("Date added", "DateCreated,SortName", "Descending"),
        RELEASED("Release date", "PremiereDate,ProductionYear,SortName", "Descending"),
        RATING("Rating", "CommunityRating,SortName", "Descending"), RANDOM("Random", "Random", "Ascending"),
    }

    /** One page of a library: movies, shows, collections or a folder's contents. */
    suspend fun browse(parentId: String, collectionType: String?, start: Int, sort: Sort, unwatched: Boolean, favourites: Boolean, genre: String?): JfItems {
        val types = when (collectionType) { "movies" -> "Movie"; "tvshows" -> "Series"; "boxsets" -> "BoxSet"; else -> null }
        val filters = listOfNotNull(if (unwatched) "IsUnplayed" else null, if (favourites) "IsFavorite" else null).joinToString(",")
        val q = buildString {
            append("/Items?userId=${a.userId}&parentId=$parentId&StartIndex=$start&Limit=100&SortBy=${sort.by}&SortOrder=${sort.order}&$FIELDS")
            if (types != null) append("&IncludeItemTypes=$types&Recursive=true")
            if (filters.isNotEmpty()) append("&Filters=$filters")
            genre?.let { append("&Genres=${enc(it)}") }
        }
        return Http.json.decodeFromString(JfItems.serializer(), get(q))
    }

    suspend fun genres(parentId: String): List<String> = list("/Genres?userId=${a.userId}&parentId=$parentId&SortBy=SortName").mapNotNull { it.Name }

    suspend fun children(parentId: String) = list("/Items?userId=${a.userId}&parentId=$parentId&SortBy=SortName&$FIELDS")

    suspend fun search(q: String) = list("/Items?userId=${a.userId}&searchTerm=${enc(q)}&IncludeItemTypes=Movie,Series,Episode,BoxSet&Recursive=true&Limit=60&$FIELDS")

    // --- details --------------------------------------------------------------------------------

    suspend fun item(id: String): JfItem = Http.json.decodeFromString(JfItem.serializer(),
        get("/Items/$id?userId=${a.userId}&Fields=Overview,Genres,People,Studios,Taglines,MediaSources,Chapters,Trickplay,ProductionYear,OfficialRating,CommunityRating,CriticRating,PremiereDate,EndDate,Status"))
    suspend fun seasons(seriesId: String) = list("/Shows/$seriesId/Seasons?userId=${a.userId}&$FIELDS")
    suspend fun episodes(seriesId: String, seasonId: String? = null, startAt: String? = null, limit: Int? = null) =
        list("/Shows/$seriesId/Episodes?userId=${a.userId}&$FIELDS" + (seasonId?.let { "&seasonId=$it" } ?: "") +
            (startAt?.let { "&startItemId=$it" } ?: "") + (limit?.let { "&Limit=$it" } ?: ""))
    suspend fun similar(id: String) = list("/Items/$id/Similar?userId=${a.userId}&Limit=16&$FIELDS")
    suspend fun byPerson(personId: String) = list("/Items?userId=${a.userId}&PersonIds=$personId&IncludeItemTypes=Movie,Series&Recursive=true&SortBy=PremiereDate&SortOrder=Descending&$FIELDS")

    suspend fun setFavourite(id: String, on: Boolean) { Jellyfin.send(if (on) "POST" else "DELETE", "/UserFavoriteItems/$id?userId=${a.userId}") }
    suspend fun setPlayed(id: String, on: Boolean) { Jellyfin.send(if (on) "POST" else "DELETE", "/UserPlayedItems/$id?userId=${a.userId}") }

    /** Intro, outro, recap and preview markers, for the Skip buttons. Empty if the server has none. */
    suspend fun segments(id: String): List<JfSegment> = runCatching {
        Http.json.decodeFromString(JfSegments.serializer(), get("/MediaSegments/$id")).Items
    }.getOrDefault(emptyList())

    // --- images ---------------------------------------------------------------------------------

    private fun img(id: String, type: String, w: Int, tag: String? = null) =
        "${a.server}/Items/$id/Images/$type?maxWidth=$w&quality=90" + (tag?.let { "&tag=$it" } ?: "")

    /** 2:3 poster: the item's own, or its series' for an episode or season. */
    fun poster(i: JfItem, w: Int = 400): String? = when {
        i.ImageTags?.containsKey("Primary") == true && i.Type != "Episode" -> img(i.Id, "Primary", w, i.ImageTags["Primary"])
        i.SeriesId != null && i.SeriesPrimaryImageTag != null -> img(i.SeriesId, "Primary", w, i.SeriesPrimaryImageTag)
        i.ImageTags?.containsKey("Primary") == true -> img(i.Id, "Primary", w, i.ImageTags["Primary"])
        else -> null
    }

    /** 16:9 still: an episode's own frame, else a thumb or backdrop. */
    fun still(i: JfItem, w: Int = 640): String? = when {
        i.Type == "Episode" && i.ImageTags?.containsKey("Primary") == true -> img(i.Id, "Primary", w, i.ImageTags["Primary"])
        i.ImageTags?.containsKey("Thumb") == true -> img(i.Id, "Thumb", w, i.ImageTags["Thumb"])
        i.ParentThumbItemId != null -> img(i.ParentThumbItemId, "Thumb", w, i.ParentThumbImageTag)
        else -> backdrop(i, w)
    }

    fun backdrop(i: JfItem, w: Int = 1280): String? = when {
        !i.BackdropImageTags.isNullOrEmpty() -> img(i.Id, "Backdrop", w, i.BackdropImageTags.first())
        i.ParentBackdropItemId != null && !i.ParentBackdropImageTags.isNullOrEmpty() -> img(i.ParentBackdropItemId, "Backdrop", w, i.ParentBackdropImageTags.first())
        else -> poster(i, w)
    }

    fun person(p: JfPerson, w: Int = 240): String? = p.PrimaryImageTag?.let { img(p.Id ?: return null, "Primary", w, it) }

    fun trickplayTile(itemId: String, mediaSourceId: String, width: Int, index: Int) =
        "${a.server}/Videos/$itemId/Trickplay/$width/$index.jpg?MediaSourceId=$mediaSourceId"
}
