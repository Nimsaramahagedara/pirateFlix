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
    @SerializedName("servers") val servers: List<ServerOption> = emptyList()
)
