package com.cinesubz.tv.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.cinesubz.tv.AppConfig
import com.cinesubz.tv.R
import com.cinesubz.tv.network.ApiClient
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MOVIE_ID = "extra_movie_id"
        const val EXTRA_MOVIE_TITLE = "extra_movie_title"
    }

    private lateinit var playerView: PlayerView
    private lateinit var tvPlayerTitle: TextView
    private lateinit var tvPlayerError: TextView
    private lateinit var playerLoading: ProgressBar
    private lateinit var btnServer1: Button
    private lateinit var btnServer2: Button

    private var exoPlayer: ExoPlayer? = null
    private var movieId: String = ""
    private var movieTitle: String = ""
    private var currentServer: String = "1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        movieId = intent.getStringExtra(EXTRA_MOVIE_ID) ?: ""
        movieTitle = intent.getStringExtra(EXTRA_MOVIE_TITLE) ?: "Streaming"

        initViews()
        setupListeners()
        resolveAndPlayStream(currentServer)
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle)
        tvPlayerError = findViewById(R.id.tvPlayerError)
        playerLoading = findViewById(R.id.playerLoading)
        btnServer1 = findViewById(R.id.btnServer1)
        btnServer2 = findViewById(R.id.btnServer2)

        tvPlayerTitle.text = movieTitle
        playerView.requestFocus()
    }

    private fun setupListeners() {
        btnServer1.setOnClickListener {
            if (currentServer != "1") {
                currentServer = "1"
                resolveAndPlayStream(currentServer)
            }
        }

        btnServer2.setOnClickListener {
            if (currentServer != "2") {
                currentServer = "2"
                resolveAndPlayStream(currentServer)
            }
        }
    }

    private fun resolveAndPlayStream(server: String) {
        playerLoading.visibility = View.VISIBLE
        tvPlayerError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = ApiClient.service.getStream(movieId, server)
                if (response.isSuccessful && response.body()?.streamUrl != null) {
                    val streamData = response.body()!!
                    initExoPlayer(streamData.streamUrl!!, streamData.headers)
                } else {
                    showError("Unable to resolve streaming link for server $server")
                }
            } catch (e: Exception) {
                showError("Stream error: ${e.localizedMessage}")
            }
        }
    }

    private fun initExoPlayer(streamUrl: String, headers: Map<String, String>) {
        releasePlayer()

        // Configure HTTP Data Source with required Referer header
        val referer = headers["Referer"] ?: AppConfig.DEFAULT_REFERER
        val userAgent = headers["User-Agent"] ?: AppConfig.USER_AGENT

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(mapOf("Referer" to referer))

        val mediaSource = ProgressiveMediaSource.Factory(httpDataSourceFactory)
            .createMediaSource(MediaItem.fromUri(streamUrl))

        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> playerLoading.visibility = View.VISIBLE
                        Player.STATE_READY -> playerLoading.visibility = View.GONE
                        Player.STATE_ENDED -> playerLoading.visibility = View.GONE
                        Player.STATE_IDLE -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    playerLoading.visibility = View.GONE
                    showError("Playback error: ${error.errorCodeName}")
                }
            })
        }

        playerView.player = exoPlayer
    }

    private fun showError(message: String) {
        playerLoading.visibility = View.GONE
        tvPlayerError.text = message
        tvPlayerError.visibility = View.VISIBLE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        exoPlayer?.let { player ->
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    player.seekTo(maxOf(0, player.currentPosition - 10000))
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    player.seekTo(minOf(player.duration, player.currentPosition + 10000))
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    if (player.isPlaying) player.pause() else player.play()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    finish()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onStop() {
        super.onStop()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}
