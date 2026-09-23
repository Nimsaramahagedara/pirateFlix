package com.cinesubz.tv.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cinesubz.tv.AppConfig
import com.cinesubz.tv.BuildConfig
import com.cinesubz.tv.databinding.ActivitySettingsBinding
import com.cinesubz.tv.update.UpdateCheckResult
import com.cinesubz.tv.update.UpdateManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var updateManager: UpdateManager
    private var pendingUpdate: UpdateCheckResult? = null
    private var downloadedApk: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateManager = UpdateManager(this)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        val versionName = BuildConfig.VERSION_NAME
        val versionCode = BuildConfig.VERSION_CODE
        binding.tvCurrentVersion.text = "App Version: v$versionName (Build $versionCode)"
        binding.tvServerEndpoint.text = "Backend API: ${AppConfig.BASE_URL}"

        binding.btnCheckUpdate.requestFocus()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnCheckUpdate.setOnClickListener {
            checkForUpdates()
        }

        binding.btnInstallUpdate.setOnClickListener {
            val update = pendingUpdate
            if (update?.downloadUrl != null) {
                downloadAndInstall(update.downloadUrl)
            } else if (downloadedApk != null) {
                installUpdate(downloadedApk!!)
            }
        }

        binding.btnGrantPermission.setOnClickListener {
            updateManager.openInstallPermissionSettings()
        }
    }

    private fun checkForUpdates() {
        binding.btnCheckUpdate.isEnabled = false
        binding.tvUpdateStatus.text = "Connecting to GitHub releases..."
        binding.tvReleaseNotes.visibility = View.GONE
        binding.btnInstallUpdate.visibility = View.GONE
        binding.btnGrantPermission.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val result = updateManager.checkForUpdate()
                binding.btnCheckUpdate.isEnabled = true

                if (result.hasUpdate && result.downloadUrl != null) {
                    pendingUpdate = result
                    val sizeFormatted = formatFileSize(result.apkSize)
                    binding.tvUpdateStatus.text = "🎉 New version available: ${result.latestVersion} ($sizeFormatted)"

                    if (result.releaseNotes.isNotBlank()) {
                        binding.tvReleaseNotes.text = "Release Notes:\n${result.releaseNotes}"
                        binding.tvReleaseNotes.visibility = View.VISIBLE
                    }

                    binding.btnInstallUpdate.visibility = View.VISIBLE
                    binding.btnInstallUpdate.text = "⬇  Download & Install"
                    binding.btnInstallUpdate.requestFocus()
                } else {
                    pendingUpdate = null
                    binding.tvUpdateStatus.text = "✅ You have the latest version installed (v${BuildConfig.VERSION_NAME})."
                    if (result.releaseNotes.isNotBlank()) {
                        binding.tvReleaseNotes.text = result.releaseNotes
                        binding.tvReleaseNotes.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                binding.btnCheckUpdate.isEnabled = true
                binding.tvUpdateStatus.text = "❌ Failed to check for updates: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    private fun downloadAndInstall(downloadUrl: String) {
        if (!updateManager.canRequestPackageInstalls()) {
            binding.tvUpdateStatus.text = "⚠️ Please enable 'Install unknown apps' permission to allow updating."
            binding.btnGrantPermission.visibility = View.VISIBLE
            binding.btnGrantPermission.requestFocus()
            return
        }

        binding.btnCheckUpdate.isEnabled = false
        binding.btnInstallUpdate.isEnabled = false
        binding.progressBarUpdate.visibility = View.VISIBLE
        binding.progressBarUpdate.progress = 0
        binding.tvDownloadProgress.visibility = View.VISIBLE
        binding.tvDownloadProgress.text = "Starting download..."

        lifecycleScope.launch {
            try {
                val apkFile = updateManager.downloadApk(downloadUrl) { percent, bytesRead, totalBytes ->
                    binding.progressBarUpdate.progress = percent
                    val readStr = formatFileSize(bytesRead)
                    val totalStr = formatFileSize(totalBytes)
                    binding.tvDownloadProgress.text = "Downloading: $percent% ($readStr / $totalStr)"
                }

                downloadedApk = apkFile
                binding.btnCheckUpdate.isEnabled = true
                binding.btnInstallUpdate.isEnabled = true
                binding.progressBarUpdate.visibility = View.GONE
                binding.tvDownloadProgress.visibility = View.GONE
                binding.tvUpdateStatus.text = "✅ Download complete! Launching package installer..."
                binding.btnInstallUpdate.text = "📦 Launch Installer"

                installUpdate(apkFile)
            } catch (e: Exception) {
                binding.btnCheckUpdate.isEnabled = true
                binding.btnInstallUpdate.isEnabled = true
                binding.progressBarUpdate.visibility = View.GONE
                binding.tvDownloadProgress.visibility = View.GONE
                binding.tvUpdateStatus.text = "❌ Download failed: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    private fun installUpdate(apkFile: File) {
        if (!updateManager.canRequestPackageInstalls()) {
            binding.tvUpdateStatus.text = "⚠️ Please enable 'Install unknown apps' permission to install the update."
            binding.btnGrantPermission.visibility = View.VISIBLE
            binding.btnGrantPermission.requestFocus()
            return
        }

        try {
            updateManager.installApk(apkFile)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
            binding.tvUpdateStatus.text = "❌ Failed to open installer: ${e.localizedMessage}"
        }
    }

    override fun onResume() {
        super.onResume()
        if (updateManager.canRequestPackageInstalls()) {
            binding.btnGrantPermission.visibility = View.GONE
        }
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val df = DecimalFormat("#.##")
        return when {
            bytes >= 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024.0))} MB"
            bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }
}
