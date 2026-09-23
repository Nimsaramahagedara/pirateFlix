package com.cinesubz.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.cinesubz.tv.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    @SerializedName("tag_name") val tagName: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("body") val body: String = "",
    @SerializedName("assets") val assets: List<ReleaseAsset> = emptyList()
)

data class ReleaseAsset(
    @SerializedName("name") val name: String = "",
    @SerializedName("browser_download_url") val downloadUrl: String = "",
    @SerializedName("size") val size: Long = 0L
)

data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseTitle: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String? = null,
    val apkSize: Long = 0L
)

class UpdateManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    companion object {
        private const val GITHUB_REPO = "Nimsaramahagedara/pirateFlix"
        private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    }

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_API_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "NimsaraTV-AndroidApp")
            .build()

        val currentVersion = BuildConfig.VERSION_NAME

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext UpdateCheckResult(
                        hasUpdate = false,
                        latestVersion = currentVersion,
                        currentVersion = currentVersion,
                        releaseNotes = "No releases published yet on GitHub."
                    )
                }
                throw Exception("GitHub API error: HTTP ${response.code}")
            }

            val bodyStr = response.body?.string() ?: throw Exception("Empty response from GitHub API")
            val release = gson.fromJson(bodyStr, ReleaseInfo::class.java)

            val latestVersion = release.tagName.trimStart('v', 'V')
            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }

            val isNewer = isVersionNewer(latestVersion, currentVersion)

            UpdateCheckResult(
                hasUpdate = isNewer && apkAsset != null,
                latestVersion = release.tagName,
                currentVersion = currentVersion,
                releaseTitle = release.name.ifBlank { release.tagName },
                releaseNotes = release.body ?: "",
                downloadUrl = apkAsset?.downloadUrl,
                apkSize = apkAsset?.size ?: 0L
            )
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (percent: Int, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "NimsaraTV-AndroidApp")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download update: HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Response body is empty")
            val totalBytes = body.contentLength()
            val outputFile = File(context.cacheDir, "nimsaratv_update.apk")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            var bytesCopied: Long = 0
            val buffer = ByteArray(16 * 1024)
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(outputFile)

            try {
                var bytes = inputStream.read(buffer)
                while (bytes >= 0) {
                    outputStream.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    val percent = if (totalBytes > 0) ((bytesCopied * 100) / totalBytes).toInt() else 0
                    withContext(Dispatchers.Main) {
                        onProgress(percent, bytesCopied, totalBytes)
                    }
                    bytes = inputStream.read(buffer)
                }
                outputStream.flush()
            } finally {
                outputStream.close()
                inputStream.close()
            }

            outputFile
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(apkFile: File) {
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
