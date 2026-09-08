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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.cinesubz.tv.AppConfig
import com.cinesubz.tv.R
import com.cinesubz.tv.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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

    /**
     * Resolves the raw playable video URL (e.g. from CDN) if the backend returned an Artplayer/HTML wrapper.
     */
    private suspend fun resolvePlayableUrl(rawUrl: String): String = withContext(Dispatchers.IO) {
        if (rawUrl.contains("skylines") || rawUrl.contains(".m3u8")) {
            return@withContext rawUrl
        }
        if (rawUrl.contains("csplayer") || rawUrl.contains("player") || rawUrl.contains("/mv/")) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()
                val request = Request.Builder()
                    .url(rawUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://cinesubz.lk/")
                    .build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                // 1. Look for ALL_QUALITIES default or any quality URL
                val defQualityRegex = Regex("""\{[^{}]*?"url"\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["'][^{}]*?"default"\s*:\s*true""")
                val defMatch = defQualityRegex.find(body)
                if (defMatch != null) {
                    return@withContext defMatch.groupValues[1]
                }

                // 2. Look for Artplayer main url: '...'
                val artUrlRegex = Regex("""url\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""")
                val artMatch = artUrlRegex.find(body)
                if (artMatch != null) {
                    return@withContext artMatch.groupValues[1]
                }

                // 3. Fallback to any direct media link
                val anyMediaRegex = Regex("""(https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*)""")
                val anyMatch = anyMediaRegex.find(body)
                if (anyMatch != null) {
                    return@withContext anyMatch.groupValues[1]
                }
            } catch (_: Exception) {
                // Return fallback rawUrl
            }
        }
        rawUrl
    }

    private fun resolveAndPlayStream(server: String) {
        playerLoading.visibility = View.VISIBLE
        tvPlayerError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = ApiClient.service.getStream(movieId, server)
                if (response.isSuccessful && response.body()?.streamUrl != null) {
                    val streamData = response.body()!!
                    val playableUrl = resolvePlayableUrl(streamData.streamUrl!!)
                    initExoPlayer(playableUrl, streamData.headers)
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

        // Configure HTTP Data Source with required headers and cross-protocol redirects
        val referer = headers["Referer"] ?: AppConfig.DEFAULT_REFERER
        val userAgent = headers["User-Agent"] ?: AppConfig.USER_AGENT

        val requestProperties = HashMap<String, String>().apply {
            putAll(headers)
            put("Referer", referer)
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(requestProperties)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        val mediaItem = MediaItem.fromUri(streamUrl)
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
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
                            else -> Unit
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
        val player = exoPlayer ?: return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                player.seekTo(maxOf(0, player.currentPosition - 10000))
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                player.seekTo(minOf(player.duration, player.currentPosition + 10000))
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (player.isPlaying) player.pause() else player.play()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
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
