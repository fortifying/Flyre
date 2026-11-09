package com.flyre.lyrics

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.flyre.lyrics.data.local.FlyrePreferences
import com.flyre.lyrics.data.local.LyricsDatabase
import com.flyre.lyrics.data.remote.RetrofitClient
import com.flyre.lyrics.data.repository.LyricsRepository

class FlyreApplication : Application() {

    lateinit var lyricsRepository: LyricsRepository
        private set

    lateinit var preferences: FlyrePreferences
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val database = LyricsDatabase.getInstance(this)
        lyricsRepository = LyricsRepository(database.lyricDao(), RetrofitClient.lrclibApi)
        preferences = FlyrePreferences(this)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_OVERLAY,
                getString(R.string.floating_service_notification_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.floating_service_notification_content)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_OVERLAY = "flyre_floating_service"
        lateinit var instance: FlyreApplication
            private set
    }
}

