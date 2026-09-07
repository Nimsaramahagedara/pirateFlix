package com.cinesubz.tv.model

import com.google.gson.annotations.SerializedName

data class StreamResponse(
    @SerializedName("post_id") val postId: String,
    @SerializedName("server") val server: String = "1",
    @SerializedName("stream_url") val streamUrl: String? = null,
    @SerializedName("type") val type: String = "mp4",
    @SerializedName("headers") val headers: Map<String, String> = emptyMap(),
    @SerializedName("error") val error: String? = null
)
