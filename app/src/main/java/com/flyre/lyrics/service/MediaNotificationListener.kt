package com.flyre.lyrics.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.flyre.lyrics.data.model.SongInfo
import com.flyre.lyrics.data.repository.MediaStateRepository

class MediaNotificationListener : NotificationListenerService() {

    private var activeController: MediaController? = null

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            extractAndPublishMedia(activeController)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            if (state != null) {
                val isPlaying = state.state == PlaybackState.STATE_PLAYING
                val position = state.position
                MediaStateRepository.updatePlaybackState(isPlaying, position)
            }
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            activeController?.unregisterCallback(this)
            activeController = null
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        scanActiveSessions()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        if (isTargetPlayer(pkg)) {
            val extras = sbn.notification?.extras ?: return

            @Suppress("DEPRECATION")
            val token = extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)
            if (token != null) {
                bindToMediaToken(token)
            } else {
                // Fallback to notification title and text
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                if (title.isNotBlank()) {
                    val song = SongInfo(
                        title = title,
                        artist = text,
                        isPlaying = true
                    )
                    MediaStateRepository.updateSong(song)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn != null && isTargetPlayer(sbn.packageName)) {
            // Check if another session is still active
            scanActiveSessions()
        }
    }

    private fun isTargetPlayer(packageName: String): Boolean {
        return packageName == YOUTUBE_MUSIC_PACKAGE ||
                packageName.contains("youtube", ignoreCase = true) ||
                packageName == SPOTIFY_PACKAGE
    }

    private fun bindToMediaToken(token: MediaSession.Token) {
        try {
            activeController?.unregisterCallback(mediaControllerCallback)
            val controller = MediaController(applicationContext, token)
            activeController = controller
            controller.registerCallback(mediaControllerCallback)
            extractAndPublishMedia(controller)
        } catch (e: Exception) {
            Log.e(TAG, "Error binding media controller", e)
        }
    }

    private fun extractAndPublishMedia(controller: MediaController?) {
        if (controller == null) return

        val metadata = controller.metadata
        val state = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val position = state?.position ?: 0L

        if (title.isNotBlank()) {
            val song = SongInfo(
                title = title,
                artist = artist,
                album = album,
                durationMs = duration,
                isPlaying = isPlaying,
                positionMs = position
            )
            MediaStateRepository.updateSong(song)
        }
    }

    private fun scanActiveSessions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                val component = ComponentName(this, MediaNotificationListener::class.java)
                val controllers = sessionManager?.getActiveSessions(component)

                val ytMusic = controllers?.firstOrNull { isTargetPlayer(it.packageName) }
                    ?: controllers?.firstOrNull()

                if (ytMusic != null) {
                    activeController?.unregisterCallback(mediaControllerCallback)
                    activeController = ytMusic
                    ytMusic.registerCallback(mediaControllerCallback)
                    extractAndPublishMedia(ytMusic)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification listener permission not yet granted or active: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning active media sessions", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeController?.unregisterCallback(mediaControllerCallback)
        activeController = null
    }

    companion object {
        private const val TAG = "MediaNotifListener"
        const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
        const val SPOTIFY_PACKAGE = "com.spotify.music"
    }
}

