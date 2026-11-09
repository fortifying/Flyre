package com.flyre.lyrics.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.flyre.lyrics.FlyreApplication
import com.flyre.lyrics.R
import com.flyre.lyrics.data.local.FlyrePreferences
import com.flyre.lyrics.data.model.SongInfo
import com.flyre.lyrics.data.repository.LyricsRepository
import com.flyre.lyrics.databinding.ActivityMainBinding
import com.flyre.lyrics.service.FloatingLyricsService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: FlyrePreferences
    private lateinit var repository: LyricsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as FlyreApplication
        preferences = app.preferences
        repository = app.lyricsRepository

        setupPermissionsSection()
        setupServiceControls()
        setupCustomizationSection()
        setupManualSearchSection()

        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        updateServiceButtonState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val query = intent?.getStringExtra(EXTRA_SEARCH_QUERY)
        if (!query.isNullOrBlank()) {
            binding.etSearchTitle.setText(query)
            performSearch(query, "")
        }
    }

    private fun setupPermissionsSection() {
        binding.btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission not required on this Android version", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGrantNotification.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }
    }

    private fun updatePermissionStatuses() {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (hasOverlay) {
            binding.btnGrantOverlay.text = getString(R.string.perm_granted)
            binding.btnGrantOverlay.isEnabled = false
        } else {
            binding.btnGrantOverlay.text = getString(R.string.btn_grant_overlay)
            binding.btnGrantOverlay.isEnabled = true
        }

        val hasNotificationAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (hasNotificationAccess) {
            binding.btnGrantNotification.text = getString(R.string.perm_granted)
            binding.btnGrantNotification.isEnabled = false
        } else {
            binding.btnGrantNotification.text = getString(R.string.btn_grant_notification)
            binding.btnGrantNotification.isEnabled = true
        }
    }

    private fun setupServiceControls() {
        binding.btnToggleFloatingService.setOnClickListener {
            if (isServiceRunning(FloatingLyricsService::class.java)) {
                FloatingLyricsService.stop(this)
                binding.btnToggleFloatingService.text = getString(R.string.btn_start_service)
                binding.btnToggleFloatingService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Please grant Overlay permission first!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val hasNotif = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
                if (!hasNotif) {
                    Toast.makeText(this, "Please enable Notification Access to detect YouTube Music songs!", Toast.LENGTH_LONG).show()
                }

                FloatingLyricsService.start(this)
                binding.btnToggleFloatingService.text = getString(R.string.btn_stop_service)
                binding.btnToggleFloatingService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.surface_variant)
            }
        }
    }

    private fun updateServiceButtonState() {
        val running = isServiceRunning(FloatingLyricsService::class.java)
        if (running) {
            binding.btnToggleFloatingService.text = getString(R.string.btn_stop_service)
            binding.btnToggleFloatingService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.surface_variant)
        } else {
            binding.btnToggleFloatingService.text = getString(R.string.btn_start_service)
            binding.btnToggleFloatingService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
        }
    }

    private fun setupCustomizationSection() {
        // Opacity
        val initialOpacity = (preferences.opacity * 100).toInt()
        binding.sliderOpacity.value = initialOpacity.toFloat()
        binding.tvOpacityLabel.text = getString(R.string.label_opacity, initialOpacity)

        binding.sliderOpacity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                preferences.opacity = value / 100f
                binding.tvOpacityLabel.text = getString(R.string.label_opacity, value.toInt())
            }
        }

        // Font Size
        val initialFontSize = preferences.fontSizeSp.toInt()
        binding.sliderFontSize.value = initialFontSize.toFloat()
        binding.tvFontSizeLabel.text = getString(R.string.label_font_size, initialFontSize)

        binding.sliderFontSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                preferences.fontSizeSp = value
                binding.tvFontSizeLabel.text = getString(R.string.label_font_size, value.toInt())
            }
        }

        // Theme
        when (preferences.theme) {
            FlyrePreferences.THEME_AMOLED -> binding.rbThemeAmoled.isChecked = true
            FlyrePreferences.THEME_SLATE -> binding.rbThemeSlate.isChecked = true
            FlyrePreferences.THEME_DARK -> binding.rbThemeDark.isChecked = true
            else -> binding.rbThemeTransparent.isChecked = true
        }

        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val theme = when (checkedId) {
                R.id.rbThemeAmoled -> FlyrePreferences.THEME_AMOLED
                R.id.rbThemeSlate -> FlyrePreferences.THEME_SLATE
                R.id.rbThemeDark -> FlyrePreferences.THEME_DARK
                else -> FlyrePreferences.THEME_TRANSPARENT
            }
            preferences.theme = theme
        }

        // Lock / Touch Passthrough toggle
        binding.btnToggleTouchLock.setOnClickListener {
            preferences.isTouchLocked = !preferences.isTouchLocked
        }

        lifecycleScope.launchWhenStarted {
            preferences.touchLockedFlow.collect { locked ->
                if (locked) {
                    binding.btnToggleTouchLock.text = "Unlock Touch (Allow Drag & Resize)"
                    binding.btnToggleTouchLock.setIconResource(R.drawable.ic_lock_open)
                } else {
                    binding.btnToggleTouchLock.text = "Lock Touch (Passthrough Mode)"
                    binding.btnToggleTouchLock.setIconResource(R.drawable.ic_lock)
                }
            }
        }
    }

    private fun setupManualSearchSection() {
        binding.btnManualSearch.setOnClickListener {
            val title = binding.etSearchTitle.text?.toString()?.trim() ?: ""
            val artist = binding.etSearchArtist.text?.toString()?.trim() ?: ""

            if (title.isBlank()) {
                Toast.makeText(this, "Please enter a song title", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performSearch(title, artist)
        }
    }

    private fun performSearch(title: String, artist: String) {
        binding.tvSearchResult.visibility = View.VISIBLE
        binding.tvSearchResult.text = "Searching lyrics for '$title'..."

        lifecycleScope.launch {
            val dummySong = SongInfo(title = title, artist = artist)
            val result = repository.getLyrics(dummySong)

            result.onSuccess { lyrics ->
                val syncStatus = if (lyrics.isSynced) "[Synced]" else "[Plain]"
                val snippet = lyrics.lines.take(6).joinToString("\n") { it.text }
                binding.tvSearchResult.text = "$syncStatus Found lyrics (${lyrics.lines.size} lines):\n\n$snippet\n..."
            }.onFailure { err ->
                binding.tvSearchResult.text = "Error: ${err.message ?: "Lyrics not found"}"
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    companion object {
        const val EXTRA_SEARCH_QUERY = "extra_search_query"
    }
}

