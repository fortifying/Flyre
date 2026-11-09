package com.flyre.lyrics.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.flyre.lyrics.FlyreApplication
import com.flyre.lyrics.R
import com.flyre.lyrics.data.local.FlyrePreferences
import com.flyre.lyrics.data.model.LyricsData
import com.flyre.lyrics.data.model.SongInfo
import com.flyre.lyrics.data.repository.LyricsRepository
import com.flyre.lyrics.data.repository.MediaStateRepository
import com.flyre.lyrics.data.util.LrcParser
import com.flyre.lyrics.databinding.LayoutFloatingBubbleBinding
import com.flyre.lyrics.databinding.LayoutFloatingLyricsBinding
import com.flyre.lyrics.databinding.LayoutLockPinBinding
import com.flyre.lyrics.ui.MainActivity
import com.flyre.lyrics.ui.adapter.LyricsAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingLyricsService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var preferences: FlyrePreferences
    private lateinit var repository: LyricsRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var lyricsFetchJob: Job? = null

    // Views & Bindings
    private var floatingBinding: LayoutFloatingLyricsBinding? = null
    private var bubbleBinding: LayoutFloatingBubbleBinding? = null
    private var pinBinding: LayoutLockPinBinding? = null

    private var floatingParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var pinParams: WindowManager.LayoutParams? = null

    private lateinit var lyricsAdapter: LyricsAdapter
    private var currentLyricsData: LyricsData? = null
    private var isMinimized = false
    private var isPinAttached = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val app = application as FlyreApplication
        preferences = app.preferences
        repository = app.lyricsRepository

        startForegroundService()
        initViews()
        observeMediaState()
        observePreferences()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_LOCK -> {
                preferences.isTouchLocked = !preferences.isTouchLocked
            }
            ACTION_CLOSE -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        updateNotification(preferences.isTouchLocked)
    }

    private fun updateNotification(isLocked: Boolean) {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val lockIntent = Intent(this, FloatingLyricsService::class.java).apply {
            action = ACTION_TOGGLE_LOCK
        }
        val pendingLock = PendingIntent.getService(
            this,
            1,
            lockIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val lockActionTitle = if (isLocked) "🔓 Unlock Touch" else "🔒 Lock (Passthrough)"

        val closeIntent = Intent(this, FloatingLyricsService::class.java).apply {
            action = ACTION_CLOSE
        }
        val pendingClose = PendingIntent.getService(
            this,
            2,
            closeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, FlyreApplication.CHANNEL_ID_OVERLAY)
            .setContentTitle(getString(R.string.floating_service_notification_title))
            .setContentText(if (isLocked) "Touch passthrough active (Locked)" else "Overlay touch active (Drag & Resize)")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingOpen)
            .addAction(if (isLocked) R.drawable.ic_lock_open else R.drawable.ic_lock, lockActionTitle, pendingLock)
            .addAction(R.drawable.ic_close, "Exit", pendingClose)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initViews() {
        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_Flyre)
        val inflater = LayoutInflater.from(themedContext)

        // 1. Inflate Floating Lyrics Window
        floatingBinding = LayoutFloatingLyricsBinding.inflate(inflater)
        val fBinding = floatingBinding!!

        lyricsAdapter = LyricsAdapter(preferences.fontSizeSp)
        fBinding.rvLyrics.apply {
            layoutManager = LinearLayoutManager(themedContext)
            adapter = lyricsAdapter
            setHasFixedSize(false)
        }

        applyTheme(preferences.theme)
        applyOpacity(preferences.opacity)

        // Window LayoutParams
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = resources.displayMetrics
        val initialWidth = dpToPx(preferences.overlayWidthDp, displayMetrics)
        val initialHeight = dpToPx(preferences.overlayHeightDp, displayMetrics)

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        if (preferences.isTouchLocked) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        floatingParams = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.overlayX
            y = preferences.overlayY
        }

        // Header touch listener for dragging
        setupWindowDrag(fBinding.headerBar)

        // Resize handle touch listener
        setupWindowResize(fBinding.btnResizeHandle)

        // Controls
        fBinding.btnClose.setOnClickListener {
            stopSelf()
        }

        fBinding.btnMinimize.setOnClickListener {
            minimizeToBubble()
        }

        fBinding.btnLockPassthrough.setOnClickListener {
            preferences.isTouchLocked = !preferences.isTouchLocked
        }

        fBinding.btnQuickOpacity.setOnClickListener {
            val panel = fBinding.panelQuickOpacity
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        fBinding.seekQuickOpacity.progress = (preferences.opacity * 100).toInt()
        fBinding.seekQuickOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val opacity = progress.coerceIn(10, 100) / 100f
                    applyOpacity(opacity)
                    preferences.opacity = opacity
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        fBinding.btnSearchLyrics.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_SEARCH_QUERY, fBinding.tvTrackTitle.text.toString())
            }
            startActivity(intent)
        }

        // 2. Inflate Floating Bubble (Minimized state)
        bubbleBinding = LayoutFloatingBubbleBinding.inflate(inflater)
        val bBinding = bubbleBinding!!

        val bubbleSize = dpToPx(56, displayMetrics)
        bubbleParams = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.overlayX
            y = preferences.overlayY
        }

        setupBubbleDragAndClick(bBinding.bubbleRoot)

        // 3. Inflate Lock Pin (Micro unlock button for passthrough mode)
        pinBinding = LayoutLockPinBinding.inflate(inflater)
        val pBinding = pinBinding!!

        val pinSize = dpToPx(44, displayMetrics)
        pinParams = WindowManager.LayoutParams(
            pinSize,
            pinSize,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.overlayX + initialWidth - pinSize
            y = preferences.overlayY.coerceAtLeast(60)
        }

        pBinding.pinRoot.setOnClickListener {
            preferences.isTouchLocked = false
            Toast.makeText(this, "Lyrics Unlocked (Drag & Resize enabled)", Toast.LENGTH_SHORT).show()
        }

        // Initial addition to WindowManager
        if (preferences.isMinimized) {
            isMinimized = true
            windowManager.addView(bBinding.root, bubbleParams)
        } else {
            isMinimized = false
            windowManager.addView(fBinding.root, floatingParams)
            if (preferences.isTouchLocked) {
                setTouchPassthrough(true)
            }
        }
    }

    private fun setTouchPassthrough(locked: Boolean) {
        val params = floatingParams ?: return
        val binding = floatingBinding ?: return

        if (locked) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            binding.headerBar.visibility = View.GONE
            binding.btnResizeHandle.visibility = View.GONE
            binding.panelQuickOpacity.visibility = View.GONE
            binding.floatingRoot.background = null
            binding.floatingRoot.setBackgroundColor(Color.TRANSPARENT)
            binding.btnLockPassthrough.setImageResource(R.drawable.ic_lock)

            if (!isMinimized && !isPinAttached && pinBinding != null) {
                try {
                    val pParams = pinParams ?: return
                    pParams.x = (preferences.overlayX + params.width - dpToPx(48, resources.displayMetrics)).coerceAtLeast(0)
                    pParams.y = (preferences.overlayY - dpToPx(10, resources.displayMetrics)).coerceAtLeast(60)
                    windowManager.addView(pinBinding!!.root, pParams)
                    isPinAttached = true
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding lock pin view", e)
                }
            }
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            binding.headerBar.visibility = View.VISIBLE
            binding.btnResizeHandle.visibility = View.VISIBLE
            binding.btnLockPassthrough.setImageResource(R.drawable.ic_lock_open)
            applyTheme(preferences.theme)

            if (isPinAttached && pinBinding != null) {
                try {
                    windowManager.removeView(pinBinding!!.root)
                    isPinAttached = false
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing lock pin view", e)
                }
            }
        }

        try {
            if (!isMinimized && binding.root.isAttachedToWindow) {
                windowManager.updateViewLayout(binding.root, params)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating window touch flags", e)
        }

        updateNotification(locked)
    }

    private fun applyOpacity(opacity: Float) {
        floatingBinding?.floatingRoot?.alpha = opacity
    }

    private fun applyTheme(themeName: String) {
        val root = floatingBinding?.floatingRoot ?: return
        if (preferences.isTouchLocked || themeName == FlyrePreferences.THEME_TRANSPARENT) {
            root.background = null
            root.setBackgroundColor(Color.TRANSPARENT)
        } else {
            val colorStr = when (themeName) {
                FlyrePreferences.THEME_AMOLED -> "#CC000000"
                FlyrePreferences.THEME_SLATE -> "#991E293B"
                else -> "#99121212"
            }
            root.background = null
            root.setBackgroundColor(Color.parseColor(colorStr))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupWindowDrag(dragHandle: View) {
        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = floatingParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingBinding?.root, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        preferences.overlayX = params.x
                        preferences.overlayY = params.y
                        return true
                    }
                }
                return false
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupWindowResize(resizeHandle: View) {
        resizeHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialWidth = 0
            private var initialHeight = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = floatingParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWidth = params.width
                        initialHeight = params.height
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val minWidth = dpToPx(180, resources.displayMetrics)
                        val minHeight = dpToPx(180, resources.displayMetrics)
                        val newWidth = (initialWidth + (event.rawX - initialTouchX).toInt()).coerceAtLeast(minWidth)
                        val newHeight = (initialHeight + (event.rawY - initialTouchY).toInt()).coerceAtLeast(minHeight)

                        params.width = newWidth
                        params.height = newHeight
                        windowManager.updateViewLayout(floatingBinding?.root, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val metrics = resources.displayMetrics
                        preferences.overlayWidthDp = (params.width / metrics.density).toInt()
                        preferences.overlayHeightDp = (params.height / metrics.density).toInt()
                        return true
                    }
                }
                return false
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBubbleDragAndClick(bubble: View) {
        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var hasMoved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = bubbleParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        hasMoved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            hasMoved = true
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(bubbleBinding?.root, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!hasMoved) {
                            expandToWindow()
                        } else {
                            preferences.overlayX = params.x
                            preferences.overlayY = params.y
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun minimizeToBubble() {
        if (isMinimized) return
        isMinimized = true
        preferences.isMinimized = true

        try {
            floatingBinding?.root?.let { windowManager.removeView(it) }
            if (isPinAttached && pinBinding != null) {
                windowManager.removeView(pinBinding!!.root)
                isPinAttached = false
            }
            bubbleParams?.let { bp ->
                floatingParams?.let { fp ->
                    bp.x = fp.x
                    bp.y = fp.y
                }
                bubbleBinding?.root?.let { windowManager.addView(it, bp) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching to bubble view", e)
        }
    }

    private fun expandToWindow() {
        if (!isMinimized) return
        isMinimized = false
        preferences.isMinimized = false

        try {
            bubbleBinding?.root?.let { windowManager.removeView(it) }
            floatingParams?.let { fp ->
                bubbleParams?.let { bp ->
                    fp.x = bp.x
                    fp.y = bp.y
                }
                floatingBinding?.root?.let { windowManager.addView(it, fp) }
                if (preferences.isTouchLocked) {
                    setTouchPassthrough(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching to window view", e)
        }
    }

    private fun observePreferences() {
        serviceScope.launch {
            preferences.opacityFlow.collectLatest { opacity ->
                withContext(Dispatchers.Main) {
                    applyOpacity(opacity)
                    floatingBinding?.seekQuickOpacity?.progress = (opacity * 100).toInt()
                }
            }
        }

        serviceScope.launch {
            preferences.fontSizeFlow.collectLatest { sizeSp ->
                withContext(Dispatchers.Main) {
                    lyricsAdapter.setFontSize(sizeSp)
                }
            }
        }

        serviceScope.launch {
            preferences.themeFlow.collectLatest { theme ->
                withContext(Dispatchers.Main) {
                    applyTheme(theme)
                }
            }
        }

        serviceScope.launch {
            preferences.touchLockedFlow.collectLatest { locked ->
                withContext(Dispatchers.Main) {
                    setTouchPassthrough(locked)
                }
            }
        }
    }

    private fun observeMediaState() {
        // Observe Current Track
        serviceScope.launch {
            MediaStateRepository.currentSong.collectLatest { song ->
                withContext(Dispatchers.Main) {
                    onSongChanged(song)
                }
            }
        }

        // Observe Real-time Playback Timestamp
        serviceScope.launch {
            MediaStateRepository.currentPositionMs.collectLatest { positionMs ->
                withContext(Dispatchers.Main) {
                    syncLyricsToPosition(positionMs)
                }
            }
        }
    }

    private fun onSongChanged(song: SongInfo?) {
        val binding = floatingBinding ?: return

        if (song == null) {
            binding.tvTrackTitle.text = getString(R.string.no_song_playing)
            binding.tvTrackArtist.text = getString(R.string.waiting_for_yt_music)
            binding.rvLyrics.visibility = View.GONE
            binding.layoutLoading.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.tvEmptyMessage.text = getString(R.string.waiting_for_yt_music)
            currentLyricsData = null
            lyricsAdapter.submitLines(emptyList())
            return
        }

        binding.tvTrackTitle.text = song.displayTitle
        binding.tvTrackArtist.text = song.displayArtist
        binding.layoutEmpty.visibility = View.GONE
        binding.layoutLoading.visibility = View.VISIBLE
        binding.tvLoadingStatus.text = getString(R.string.searching_lyrics, song.displayTitle)
        binding.rvLyrics.visibility = View.GONE

        lyricsFetchJob?.cancel()
        lyricsFetchJob = serviceScope.launch {
            val result = repository.getLyrics(song)
            withContext(Dispatchers.Main) {
                binding.layoutLoading.visibility = View.GONE
                result.onSuccess { lyrics ->
                    currentLyricsData = lyrics
                    binding.rvLyrics.visibility = View.VISIBLE
                    binding.layoutEmpty.visibility = View.GONE
                    lyricsAdapter.submitLines(lyrics.lines)
                }.onFailure { _ ->
                    currentLyricsData = null
                    binding.rvLyrics.visibility = View.GONE
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.tvEmptyMessage.text = getString(R.string.lyrics_not_found)
                }
            }
        }
    }

    private fun syncLyricsToPosition(positionMs: Long) {
        val data = currentLyricsData ?: return
        if (!data.isSynced || data.lines.isEmpty()) return

        val activeIndex = LrcParser.findActiveIndex(data.lines, positionMs)
        val changed = lyricsAdapter.setActiveIndex(activeIndex)
        if (changed && activeIndex >= 0) {
            floatingBinding?.rvLyrics?.smoothScrollToPosition(activeIndex)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        try {
            if (isMinimized) {
                bubbleBinding?.root?.let { windowManager.removeView(it) }
            } else {
                floatingBinding?.root?.let { windowManager.removeView(it) }
            }
            if (isPinAttached && pinBinding != null) {
                windowManager.removeView(pinBinding!!.root)
                isPinAttached = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay views", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dpToPx(dp: Int, metrics: DisplayMetrics): Int {
        return (dp * metrics.density).toInt()
    }

    companion object {
        private const val TAG = "FloatingLyricsService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_TOGGLE_LOCK = "com.flyre.lyrics.action.TOGGLE_LOCK"
        const val ACTION_CLOSE = "com.flyre.lyrics.action.CLOSE"

        fun start(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java)
            context.stopService(intent)
        }
    }
}
