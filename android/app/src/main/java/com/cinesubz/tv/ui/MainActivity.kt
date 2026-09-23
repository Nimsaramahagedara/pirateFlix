package com.cinesubz.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cinesubz.tv.R
import com.cinesubz.tv.adapter.CategoryAdapter
import com.cinesubz.tv.model.CategoryRow
import com.cinesubz.tv.model.Movie
import com.cinesubz.tv.network.ApiClient
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var ivHeroBackdrop: ImageView
    private lateinit var tvHeroTitle: TextView
    private lateinit var tvHeroDesc: TextView
    private lateinit var tvHeroYear: TextView
    private lateinit var tvHeroQuality: TextView
    private lateinit var tvHeroIMDb: TextView
    private lateinit var btnPlayHero: Button
    private lateinit var btnSearch: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnRefresh: Button
    private lateinit var btnFilterAll: Button
    private lateinit var btnFilterMovies: Button
    private lateinit var btnFilterTVShows: Button
    private lateinit var rvCategories: RecyclerView
    private lateinit var progressBar: ProgressBar

    private var currentHeroMovie: Movie? = null
    private var allCategoryRows: List<CategoryRow> = emptyList()
    private var selectedFilter: String = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        loadHomeFeed()
    }

    private fun initViews() {
        ivHeroBackdrop = findViewById(R.id.ivHeroBackdrop)
        tvHeroTitle = findViewById(R.id.tvHeroTitle)
        tvHeroDesc = findViewById(R.id.tvHeroDesc)
        tvHeroYear = findViewById(R.id.tvHeroYear)
        tvHeroQuality = findViewById(R.id.tvHeroQuality)
        tvHeroIMDb = findViewById(R.id.tvHeroIMDb)
        btnPlayHero = findViewById(R.id.btnPlayHero)
        btnSearch = findViewById(R.id.btnSearch)
        btnSettings = findViewById(R.id.btnSettings)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnFilterAll = findViewById(R.id.btnFilterAll)
        btnFilterMovies = findViewById(R.id.btnFilterMovies)
        btnFilterTVShows = findViewById(R.id.btnFilterTVShows)
        rvCategories = findViewById(R.id.rvCategories)
        progressBar = findViewById(R.id.progressBar)

        rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        btnFilterAll.isSelected = true
        // Set initial TV focus
        btnPlayHero.requestFocus()
    }

    private fun setupListeners() {
        btnPlayHero.setOnClickListener {
            currentHeroMovie?.let { onMovieItemClicked(it) }
        }

        btnSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnRefresh.setOnClickListener {
            loadHomeFeed()
        }

        btnFilterAll.setOnClickListener {
            applyCategoryFilter("all")
        }

        btnFilterMovies.setOnClickListener {
            applyCategoryFilter("movies")
        }

        btnFilterTVShows.setOnClickListener {
            applyCategoryFilter("tvshows")
        }
    }

    private fun applyCategoryFilter(filter: String) {
        selectedFilter = filter
        btnFilterAll.isSelected = (filter == "all")
        btnFilterMovies.isSelected = (filter == "movies")
        btnFilterTVShows.isSelected = (filter == "tvshows")

        val filteredRows = when (filter) {
            "movies" -> allCategoryRows.filter {
                it.category != "tvshows"
            }
            "tvshows" -> allCategoryRows.filter {
                it.category == "tvshows" ||
                        it.title.contains("TV", ignoreCase = true) ||
                        it.title.contains("Series", ignoreCase = true)
            }
            else -> allCategoryRows
        }

        rvCategories.adapter = CategoryAdapter(filteredRows) { movie ->
            onMovieItemClicked(movie)
        }
    }

    private fun loadHomeFeed() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = ApiClient.service.getHomeFeed()
                progressBar.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    val feed = response.body()!!

                    // Bind Hero Banner
                    feed.heroBanner?.let { hero ->
                        currentHeroMovie = hero
                        tvHeroTitle.text = hero.title
                        tvHeroDesc.text = hero.description ?: "Now streaming on NimsaraTV"
                        tvHeroYear.text = hero.year ?: "2026"
                        tvHeroQuality.text = hero.rating ?: "HD"
                        tvHeroIMDb.text = if (!hero.imdb.isNullOrEmpty()) "IMDb: ${hero.imdb}" else ""

                        ivHeroBackdrop.load(hero.backdrop ?: hero.poster) {
                            crossfade(true)
                            placeholder(R.color.netflix_card_bg)
                        }
                    }

                    // Store all rows and render active category
                    allCategoryRows = feed.rows
                    applyCategoryFilter(selectedFilter)
                } else {
                    Toast.makeText(this@MainActivity, "Failed to load catalog from server", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Connection Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
