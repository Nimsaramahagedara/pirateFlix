package com.cinesubz.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cinesubz.tv.R
import com.cinesubz.tv.adapter.MovieAdapter
import com.cinesubz.tv.model.Movie
import com.cinesubz.tv.network.ApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var btnBack: Button
    private lateinit var etSearchQuery: EditText
    private lateinit var btnClear: Button
    private lateinit var tvSearchStatus: TextView
    private lateinit var searchProgressBar: ProgressBar
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvEmptyMessage: TextView

    private lateinit var movieAdapter: MovieAdapter
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        etSearchQuery = findViewById(R.id.etSearchQuery)
        btnClear = findViewById(R.id.btnClear)
        tvSearchStatus = findViewById(R.id.tvSearchStatus)
        searchProgressBar = findViewById(R.id.searchProgressBar)
        rvSearchResults = findViewById(R.id.rvSearchResults)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage)

        // Android TV 5-column grid for 1080p layout
        rvSearchResults.layoutManager = GridLayoutManager(this, 5)
        movieAdapter = MovieAdapter(emptyList()) { movie ->
            onMovieItemClicked(movie)
        }
        rvSearchResults.adapter = movieAdapter

        tvSearchStatus.text = "Search across 90+ movies with Sinhala subtitles"
        etSearchQuery.requestFocus()
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnClear.setOnClickListener {
            etSearchQuery.setText("")
            etSearchQuery.requestFocus()
        }

        etSearchQuery.doAfterTextChanged { text ->
            val query = text?.toString()?.trim() ?: ""
            btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

            searchJob?.cancel()
            if (query.isEmpty()) {
                movieAdapter.updateMovies(emptyList())
                tvSearchStatus.text = "Search across 90+ movies with Sinhala subtitles"
                layoutEmpty.visibility = View.GONE
                searchProgressBar.visibility = View.GONE
                return@doAfterTextChanged
            }

            searchJob = lifecycleScope.launch {
                delay(400)
                performSearch(query)
            }
        }

        etSearchQuery.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                searchJob?.cancel()
                val query = etSearchQuery.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun performSearch(query: String) {
        searchProgressBar.visibility = View.VISIBLE
        layoutEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = ApiClient.service.searchMovies(query)
                searchProgressBar.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    val movies = response.body()!!
                    movieAdapter.updateMovies(movies)

                    if (movies.isEmpty()) {
                        tvSearchStatus.text = "No results for \"$query\""
                        tvEmptyMessage.text = "No movies found matching \"$query\". Try another title."
                        layoutEmpty.visibility = View.VISIBLE
                    } else {
                        tvSearchStatus.text = "Found ${movies.size} ${if (movies.size == 1) "movie" else "movies"} for \"$query\""
                        layoutEmpty.visibility = View.GONE
                    }
                } else {
                    tvSearchStatus.text = "Search failed. Please try again."
                }
            } catch (e: Exception) {
                searchProgressBar.visibility = View.GONE
                tvSearchStatus.text = "Error: ${e.localizedMessage}"
            }
        }
    }

    private fun onMovieItemClicked(movie: Movie) {
        val isTv = movie.isTvShow ||
                movie.pageUrl?.contains("/tvshows/") == true ||
                movie.title.contains("TV Series", ignoreCase = true) ||
                movie.title.contains("S01", ignoreCase = true) ||
                movie.title.contains("Season", ignoreCase = true)

        if (isTv) {
            val intent = Intent(this, SeriesActivity::class.java).apply {
                putExtra(SeriesActivity.EXTRA_SERIES_ID, movie.id)
                putExtra(SeriesActivity.EXTRA_SERIES_TITLE, movie.title)
                putExtra(SeriesActivity.EXTRA_POSTER, movie.poster)
                putExtra(SeriesActivity.EXTRA_BACKDROP, movie.backdrop)
            }
            startActivity(intent)
        } else {
            startPlayback(movie)
        }
    }

    private fun startPlayback(movie: Movie) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_MOVIE_ID, movie.id)
            putExtra(PlayerActivity.EXTRA_MOVIE_TITLE, movie.title)
            putExtra(PlayerActivity.EXTRA_STREAM_TYPE, "mv")
        }
        startActivity(intent)
    }
}
