package com.cinesubz.tv.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
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
    private lateinit var btnRefresh: Button
    private lateinit var rvCategories: RecyclerView
    private lateinit var progressBar: ProgressBar

    private var currentHeroMovie: Movie? = null

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
        btnRefresh = findViewById(R.id.btnRefresh)
        rvCategories = findViewById(R.id.rvCategories)
        progressBar = findViewById(R.id.progressBar)

        rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        // Set initial TV focus
        btnPlayHero.requestFocus()
    }

    private fun setupListeners() {
        btnPlayHero.setOnClickListener {
            currentHeroMovie?.let { startPlayback(it) }
        }

        btnRefresh.setOnClickListener {
            loadHomeFeed()
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
                        tvHeroDesc.text = hero.description ?: "Now streaming on CineSubz"
                        tvHeroYear.text = hero.year ?: "2026"
                        tvHeroQuality.text = hero.rating ?: "HD"
                        tvHeroIMDb.text = if (!hero.imdb.isNullOrEmpty()) "IMDb: ${hero.imdb}" else ""

                        ivHeroBackdrop.load(hero.backdrop ?: hero.poster) {
                            crossfade(true)
                            placeholder(R.color.netflix_card_bg)
                        }
                    }

                    // Bind Categories
                    rvCategories.adapter = CategoryAdapter(feed.rows) { movie ->
                        startPlayback(movie)
                    }
                } else {
                    Toast.makeText(this@MainActivity, "Failed to load catalog from server", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Connection Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startPlayback(movie: Movie) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_MOVIE_ID, movie.id)
            putExtra(PlayerActivity.EXTRA_MOVIE_TITLE, movie.title)
        }
        startActivity(intent)
    }
}
