package com.cinesubz.tv.network

import com.cinesubz.tv.model.HomeFeed
import com.cinesubz.tv.model.Movie
import com.cinesubz.tv.model.StreamResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("home")
    suspend fun getHomeFeed(): Response<HomeFeed>

    @GET("stream/{id}")
    suspend fun getStream(
        @Path("id") movieId: String,
        @Query("server") server: String = "1"
    ): Response<StreamResponse>

    @GET("search")
    suspend fun searchMovies(
        @Query("q") query: String
    ): Response<List<Movie>>

    @GET("movie/{id}")
    suspend fun getMovieDetails(
        @Path("id") movieId: String
    ): Response<Movie>
}
