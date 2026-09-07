package com.cinesubz.tv.model

import com.google.gson.annotations.SerializedName

data class CategoryRow(
    @SerializedName("title") val title: String,
    @SerializedName("category") val category: String,
    @SerializedName("movies") val movies: List<Movie> = emptyList()
)

data class HomeFeed(
    @SerializedName("hero_banner") val heroBanner: Movie? = null,
    @SerializedName("rows") val rows: List<CategoryRow> = emptyList()
)
