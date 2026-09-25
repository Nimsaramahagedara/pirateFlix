package com.cinesubz.tv.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cinesubz.tv.R
import com.cinesubz.tv.adapter.EpisodeAdapter
import com.cinesubz.tv.data.WatchHistoryManager
import com.cinesubz.tv.model.Episode
import com.cinesubz.tv.model.Movie
import com.cinesubz.tv.model.Season
import com.cinesubz.tv.model.SeriesDetail
import com.cinesubz.tv.network.ApiClient
import kotlinx.coroutines.launch

class SeriesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERIES_ID = "extra_series_id"
        const val EXTRA_SERIES_TITLE = "extra_series_title"
        const val EXTRA_POSTER = "extra_poster"
        const val EXTRA_BACKDROP = "extra_backdrop"
        const val EXTRA_PAGE_URL = "extra_page_url"
    }

    private lateinit var ivSeriesBackdrop: ImageView
    private lateinit var tvSeriesTitle: TextView
    private lateinit var tvSeriesYear: TextView
    private lateinit var tvSeriesIMDb: TextView
    private lateinit var tvSeriesRating: TextView
    private lateinit var tvSeriesGenres: TextView
    private lateinit var tvSeriesSynopsis: TextView
    private lateinit var layoutSeasons: LinearLayout
    private lateinit var tvEpisodesHeader: TextView
    private lateinit var rvEpisodes: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    private lateinit var episodeAdapter: EpisodeAdapter
    private var seriesId: String = ""
    private var seriesTitle: String = ""
    private var seriesPageUrl: String? = null
    private var currentSeriesDetail: SeriesDetail? = null
    private var selectedSeason: Season? = null
    private val seasonButtons = mutableListOf<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series)

        seriesId = intent.getStringExtra(EXTRA_SERIES_ID) ?: ""
        seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE) ?: "TV Series"
        seriesPageUrl = intent.getStringExtra(EXTRA_PAGE_URL)
        val initialBackdrop = intent.getStringExtra(EXTRA_BACKDROP) ?: intent.getStringExtra(EXTRA_POSTER)

        initViews()

        // Set initial preview before network call finishes
        tvSeriesTitle.text = seriesTitle
        if (!initialBackdrop.isNullOrEmpty()) {
            ivSeriesBackdrop.load(initialBackdrop) {
                crossfade(true)
            }
        }

        loadSeriesDetails(seriesId, seriesPageUrl)
    }

    private fun initViews() {
        ivSeriesBackdrop = findViewById(R.id.ivSeriesBackdrop)
        tvSeriesTitle = findViewById(R.id.tvSeriesTitle)
        tvSeriesYear = findViewById(R.id.tvSeriesYear)
        tvSeriesIMDb = findViewById(R.id.tvSeriesIMDb)
        tvSeriesRating = findViewById(R.id.tvSeriesRating)
        tvSeriesGenres = findViewById(R.id.tvSeriesGenres)
        tvSeriesSynopsis = findViewById(R.id.tvSeriesSynopsis)
        layoutSeasons = findViewById(R.id.layoutSeasons)
        tvEpisodesHeader = findViewById(R.id.tvEpisodesHeader)
        rvEpisodes = findViewById(R.id.rvEpisodes)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)

        rvEpisodes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        episodeAdapter = EpisodeAdapter(emptyList()) { episode ->
            playEpisode(episode)
        }
        rvEpisodes.adapter = episodeAdapter
    }

    private fun loadSeriesDetails(id: String, pageUrl: String? = null) {
        progressBar.visibility = View.VISIBLE
        tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = ApiClient.service.getSeriesDetails(id, pageUrl)
                progressBar.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    val detail = response.body()!!
                    currentSeriesDetail = detail
                    bindSeriesDetails(detail)
                } else {
                    tvError.text = "Unable to load series details (Error ${response.code()})"
                    tvError.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                tvError.text = "Connection error: ${e.localizedMessage}"
                tvError.visibility = View.VISIBLE
            }
        }
    }

    private fun bindSeriesDetails(detail: SeriesDetail) {
        tvSeriesTitle.text = detail.title
        tvSeriesYear.text = detail.year ?: "2026"
        tvSeriesSynopsis.text = detail.description ?: "Watch this TV series on NimsaraTV."

        if (!detail.imdb.isNullOrEmpty()) {
            tvSeriesIMDb.text = "IMDb ${detail.imdb}"
            tvSeriesIMDb.visibility = View.VISIBLE
        } else {
            tvSeriesIMDb.visibility = View.GONE
        }

        if (!detail.rating.isNullOrEmpty()) {
            tvSeriesRating.text = detail.rating
            tvSeriesRating.visibility = View.VISIBLE
        } else {
            tvSeriesRating.text = "HD"
            tvSeriesRating.visibility = View.VISIBLE
        }

        if (detail.genres.isNotEmpty()) {
            tvSeriesGenres.text = detail.genres.joinToString(" • ") { it.replaceFirstChar(Char::titlecase) }
            tvSeriesGenres.visibility = View.VISIBLE
        } else {
            tvSeriesGenres.visibility = View.GONE
        }

        val backdropUrl = detail.backdrop ?: detail.poster
        if (!backdropUrl.isNullOrEmpty()) {
            ivSeriesBackdrop.load(backdropUrl) {
                crossfade(true)
            }
        }

        setupSeasonTabs(detail.seasons)
    }

    private fun setupSeasonTabs(seasons: List<Season>) {
        layoutSeasons.removeAllViews()
        seasonButtons.clear()

        if (seasons.isEmpty()) {
            tvEpisodesHeader.text = "NO EPISODES AVAILABLE"
            episodeAdapter.updateEpisodes(emptyList())
            return
        }

        for ((index, season) in seasons.withIndex()) {
            val btn = Button(this).apply {
                text = season.title
                isFocusable = true
                isClickable = true
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                background = ContextCompat.getDrawable(this@SeriesActivity, R.drawable.bg_filter_chip_selector)
                gravity = Gravity.CENTER
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(38)
                ).apply {
                    marginEnd = dpToPx(12)
                }
                layoutParams = params

                setOnClickListener {
                    selectSeason(season, this)
                }
            }

            seasonButtons.add(btn)
            layoutSeasons.addView(btn)
        }

        // Auto-select first season
        if (seasonButtons.isNotEmpty()) {
            selectSeason(seasons[0], seasonButtons[0])
            seasonButtons[0].requestFocus()
        }
    }

    private fun selectSeason(season: Season, selectedBtn: Button) {
        selectedSeason = season
        for (btn in seasonButtons) {
            btn.isSelected = (btn == selectedBtn)
        }

        tvEpisodesHeader.text = "${season.title.uppercase()} (${season.episodes.size} EPISODES)"
        episodeAdapter.updateEpisodes(season.episodes)
        rvEpisodes.scrollToPosition(0)
    }

    private fun playEpisode(episode: Episode) {
        val seriesName = currentSeriesDetail?.title ?: seriesTitle
        val seasonName = selectedSeason?.title ?: "Season"
        val epTitle = "$seriesName - $seasonName E${episode.episodeNumber}: ${episode.title}"

        val finalPageUrl = currentSeriesDetail?.pageUrl?.takeIf { it.isNotBlank() } ?: seriesPageUrl
        val finalSeriesId = currentSeriesDetail?.id?.takeIf { it.isNotBlank() } ?: seriesId

        // Record the TV series itself (not the episode) in Continue Watching
        val seriesMovie = Movie(
            id = finalSeriesId,
            title = seriesName,
            poster = currentSeriesDetail?.poster ?: intent.getStringExtra(EXTRA_POSTER),
            backdrop = currentSeriesDetail?.backdrop ?: intent.getStringExtra(EXTRA_BACKDROP),
            description = currentSeriesDetail?.description,
            year = currentSeriesDetail?.year,
            imdb = currentSeriesDetail?.imdb,
            rating = currentSeriesDetail?.rating,
            genres = currentSeriesDetail?.genres ?: emptyList(),
            pageUrl = finalPageUrl,
            isTvShow = true
        )
        WatchHistoryManager.recordWatch(this, seriesMovie)

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_MOVIE_ID, episode.id)
            putExtra(PlayerActivity.EXTRA_MOVIE_TITLE, epTitle)
            putExtra(PlayerActivity.EXTRA_STREAM_TYPE, "ep")
        }
        startActivity(intent)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
