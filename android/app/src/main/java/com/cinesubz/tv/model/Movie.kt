package com.cinesubz.tv.model

import com.google.gson.annotations.SerializedName

data class ServerOption(
    @SerializedName("type") val type: String = "mv",
    @SerializedName("number") val number: String = "1",
    @SerializedName("name") val name: String = "CS Player"
)

data class Movie(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("backdrop") val backdrop: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("imdb") val imdb: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("genres") val genres: List<String> = emptyList(),
    @SerializedName("page_url") val pageUrl: String? = null,
    @SerializedName("is_tv_show") val isTvShow: Boolean = false,
    @SerializedName("servers") val servers: List<ServerOption> = emptyList()
)

data class Episode(
    @SerializedName("id") val id: String,
    @SerializedName("episode_number") val episodeNumber: Int,
    @SerializedName("title") val title: String,
    @SerializedName("date") val date: String? = null,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("page_url") val pageUrl: String? = null
)

data class Season(
    @SerializedName("season_number") val seasonNumber: Int,
    @SerializedName("title") val title: String,
    @SerializedName("episodes") val episodes: List<Episode> = emptyList()
)

data class SeriesDetail(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("backdrop") val backdrop: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("year") val year: String? = null,
    @SerializedName("imdb") val imdb: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("genres") val genres: List<String> = emptyList(),
    @SerializedName("page_url") val pageUrl: String? = null,
    @SerializedName("seasons") val seasons: List<Season> = emptyList()
)
