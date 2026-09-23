package com.cinesubz.tv.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
import java.util.concurrent.atomic.AtomicBoolean

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MOVIE_ID = "extra_movie_id"
        const val EXTRA_MOVIE_TITLE = "extra_movie_title"
        const val EXTRA_STREAM_TYPE = "extra_stream_type"
    }

    private lateinit var playerView: PlayerView
    private lateinit var topBar: LinearLayout
    private lateinit var tvPlayerTitle: TextView
    private lateinit var tvPlayerError: TextView
    private lateinit var playerLoading: ProgressBar
    private lateinit var btnServer1: Button
    private lateinit var btnServer2: Button

    private var exoPlayer: ExoPlayer? = null
    private var movieId: String = ""
    private var movieTitle: String = ""
    private var streamType: String = "mv"
    private var currentServer: String = "1"
    private val hasSeekedToSavedPos = AtomicBoolean(false)

    private val playbackPrefs by lazy {
        getSharedPreferences("nimsaratv_playback", Context.MODE_PRIVATE)
    }

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideTopBarRunnable = Runnable { hideTopBar() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent TV screensaver from turning on while video player activity is open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        movieId = intent.getStringExtra(EXTRA_MOVIE_ID) ?: ""
        movieTitle = intent.getStringExtra(EXTRA_MOVIE_TITLE) ?: "Streaming"
        streamType = intent.getStringExtra(EXTRA_STREAM_TYPE) ?: "mv"

        initViews()
        setupListeners()
        resolveAndPlayStream(currentServer)
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        topBar = findViewById(R.id.topBar)
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle)
        tvPlayerError = findViewById(R.id.tvPlayerError)
        playerLoading = findViewById(R.id.playerLoading)
        btnServer1 = findViewById(R.id.btnServer1)
        btnServer2 = findViewById(R.id.btnServer2)

        tvPlayerTitle.text = movieTitle
        playerView.keepScreenOn = true
        playerView.requestFocus()

        // Sync topBar visibility with PlayerView controller visibility
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (visibility == View.VISIBLE) {
                showTopBar()
            } else {
                hideTopBar()
            }
        })
    }

    private fun showTopBar() {
        topBar.animate().cancel()
        topBar.visibility = View.VISIBLE
        topBar.alpha = 1.0f
        if (exoPlayer?.isPlaying == true) {
            scheduleHideTopBar()
        }
    }

    private fun hideTopBar() {
        if (isDestroyed || isFinishing) return
        topBar.animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction { topBar.visibility = View.GONE }
            .start()
    }

    private fun scheduleHideTopBar() {
        hideHandler.removeCallbacks(hideTopBarRunnable)
        hideHandler.postDelayed(hideTopBarRunnable, 3500)
    }

    private fun setupListeners() {
        btnServer1.setOnClickListener {
            if (currentServer != "1") {
                savePlaybackPosition()
                currentServer = "1"
                hasSeekedToSavedPos.set(false)
                resolveAndPlayStream(currentServer)
            }
        }

        btnServer2.setOnClickListener {
            if (currentServer != "2") {
                savePlaybackPosition()
                currentServer = "2"
                hasSeekedToSavedPos.set(false)
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
                val response = ApiClient.service.getStream(movieId, server, streamType)
                if (response.isSuccessful && response.body()?.streamUrl != null) {
                    val streamData = response.body()!!
                    val playableUrl = resolvePlayableUrl(streamData.streamUrl!!)
                    initExoPlayer(playableUrl, streamData.headers)
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    val isMaintenance = response.code() == 503 ||
                            errorBody.contains("maintenance", ignoreCase = true) ||
                            errorBody.contains("503")
                    if (isMaintenance) {
                        showError("CineSubz is currently undergoing maintenance.\nPlease try again shortly.")
                    } else {
                        showError("Unable to resolve streaming link for server $server")
                    }
                }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Unknown error"
                if (msg.contains("503") || msg.contains("maintenance", ignoreCase = true)) {
                    showError("CineSubz is currently undergoing maintenance.\nPlease try again shortly.")
                } else {
                    showError("Stream error: $msg")
                }
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
                            Player.STATE_READY -> {
                                playerLoading.visibility = View.GONE
                                // Resume last saved playback position
                                val savedPos = playbackPrefs.getLong("pos_$movieId", 0L)
                                if (savedPos > 3000L && hasSeekedToSavedPos.compareAndSet(false, true)) {
                                    seekTo(savedPos)
                                    Toast.makeText(
                                        this@PlayerActivity,
                                        "Resuming from ${formatDuration(savedPos)}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                scheduleHideTopBar()
                            }
                            Player.STATE_ENDED -> {
                                playerLoading.visibility = View.GONE
                                // Reset saved position on completion
                                playbackPrefs.edit().remove("pos_$movieId").apply()
                                showTopBar()
                            }
                            Player.STATE_IDLE -> Unit
                            else -> Unit
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playerView.keepScreenOn = isPlaying
                        if (isPlaying) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            scheduleHideTopBar()
                        } else {
                            showTopBar()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playerLoading.visibility = View.GONE
                        showError("Playback error: ${error.errorCodeName}")
                        showTopBar()
                    }
                })
            }

        playerView.player = exoPlayer
        scheduleHideTopBar()
    }

    private fun savePlaybackPosition() {
        val player = exoPlayer ?: return
        val pos = player.currentPosition
        val dur = player.duration
        if (movieId.isNotEmpty() && pos > 0 && dur > 0) {
            // Reset if watched to end (>95% or within 30 seconds of end)
            if (pos >= dur - 30_000 || pos >= dur * 0.95) {
                playbackPrefs.edit().remove("pos_$movieId").apply()
            } else {
                playbackPrefs.edit().putLong("pos_$movieId", pos).apply()
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun showError(message: String) {
        playerLoading.visibility = View.GONE
        tvPlayerError.text = message
        tvPlayerError.visibility = View.VISIBLE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Show controls upon any remote control activity
        showTopBar()

        val player = exoPlayer ?: return super.onKeyDown(keyCode, event)
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                player.seekTo(maxOf(0, player.currentPosition - 10000))
                scheduleHideTopBar()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                player.seekTo(minOf(player.duration, player.currentPosition + 10000))
                scheduleHideTopBar()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (player.isPlaying) {
                    player.pause()
                    showTopBar()
                } else {
                    player.play()
                    scheduleHideTopBar()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                showTopBar()
                btnServer1.requestFocus()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                savePlaybackPosition()
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun releasePlayer() {
        savePlaybackPosition()
        hideHandler.removeCallbacks(hideTopBarRunnable)
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onPause() {
        super.onPause()
        savePlaybackPosition()
    }

    override fun onStop() {
        super.onStop()
        savePlaybackPosition()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}
