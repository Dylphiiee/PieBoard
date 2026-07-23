package com.dylphiiee.pieboard.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.dylphiiee.pieboard.R
import com.dylphiiee.pieboard.data.SoundRepository
import com.dylphiiee.pieboard.databinding.DismissTargetLayoutBinding
import com.dylphiiee.pieboard.databinding.FloatingButtonLayoutBinding
import com.dylphiiee.pieboard.databinding.FloatingPanelLayoutBinding
import com.dylphiiee.pieboard.ui.MainActivity
import com.dylphiiee.pieboard.util.Prefs
import com.dylphiiee.pieboard.util.SoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var repository: SoundRepository
    private lateinit var soundPlayer: SoundPlayer
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var buttonBinding: FloatingButtonLayoutBinding? = null
    private var panelBinding: FloatingPanelLayoutBinding? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelAdapter: FloatingSoundAdapter? = null
    private var panelVisible = false
    private var currentPanelTab: PanelTab = PanelTab.ALL

    private var dismissTargetBinding: DismissTargetLayoutBinding? = null
    private var dismissTargetParams: WindowManager.LayoutParams? = null
    private var isHoveringDismiss = false
    private val dismissBottomMarginPx by lazy { dpToPx(40) }
    private val dismissTargetSizePx by lazy { dpToPx(52) }
    private val dismissThresholdPx by lazy { dpToPx(40) }

    private enum class PanelTab { ALL, FAVORITES }

    // Single source of truth for button sizing so the on/off-screen window size,
    // the drawn icon size, and the panel-position math never disagree with each other.
    // Size is intentionally fixed (small) and not user-resizable.
    private val rootPaddingPx by lazy { dpToPx(4) }
    private fun iconSizePx(): Int = dpToPx(FIXED_ICON_SIZE_DP)
    private fun windowButtonSizePx(): Int = iconSizePx() + rootPaddingPx * 2

    private val panelWidthPx by lazy { dpToPx(145) }
    private val panelEstimatedHeightPx by lazy { dpToPx(270) }
    private val panelMarginPx by lazy { dpToPx(8) }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        repository = SoundRepository(this)
        soundPlayer = SoundPlayer(this, prefs)

        startForeground(NOTIF_ID, buildNotification())
        addFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeDismissTarget()
        removePanel()
        removeButton()
        soundPlayer.release()
        serviceScope.cancel()
    }

    // ---------------- Floating button ----------------

    private fun addFloatingButton() {
        val themedContext = ContextThemeWrapper(this, R.style.Theme_PieBoard)
        val binding = FloatingButtonLayoutBinding.inflate(LayoutInflater.from(themedContext))
        buttonBinding = binding

        applySizeToButtonViews(binding)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val windowSize = windowButtonSizePx()
        val params = WindowManager.LayoutParams(
            windowSize,
            windowSize,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.floatingButtonX
            y = prefs.floatingButtonY
        }
        buttonParams = params

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false
        val dragThresholdPx = dpToPx(8)

        val touchTarget = binding.ivFloatingButton
        touchTarget.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    isHoveringDismiss = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > dragThresholdPx || abs(dy) > dragThresholdPx) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(binding.root, params)
                    if (panelVisible) updatePanelPosition()
                    if (moved) {
                        if (dismissTargetBinding == null) showDismissTarget()
                        val buttonSize = windowButtonSizePx()
                        updateDismissHoverState(params.x + buttonSize / 2, params.y + buttonSize / 2)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved && isHoveringDismiss) {
                        // Dropped onto the X target: turn the floating button off entirely,
                        // without needing to open the app.
                        prefs.floatingEnabled = false
                        removeDismissTarget()
                        stopSelf()
                    } else {
                        prefs.floatingButtonX = params.x
                        prefs.floatingButtonY = params.y
                        removeDismissTarget()
                        if (!moved) {
                            togglePanel()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(binding.root, params)
    }

    // ---------------- Drag-to-dismiss target ----------------

    private fun showDismissTarget() {
        if (dismissTargetBinding != null) return
        val themedContext = ContextThemeWrapper(this, R.style.Theme_PieBoard)
        val binding = DismissTargetLayoutBinding.inflate(LayoutInflater.from(themedContext))
        dismissTargetBinding = binding

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dismissBottomMarginPx
        }
        dismissTargetParams = params

        try {
            windowManager.addView(binding.root, params)
        } catch (_: Exception) {
        }
    }

    private fun removeDismissTarget() {
        dismissTargetBinding?.let {
            try {
                windowManager.removeView(it.root)
            } catch (_: Exception) {
            }
        }
        dismissTargetBinding = null
        dismissTargetParams = null
        isHoveringDismiss = false
    }

    /** Highlights the X target once the button is dragged close enough on top of it. */
    private fun updateDismissHoverState(buttonCenterX: Int, buttonCenterY: Int) {
        val binding = dismissTargetBinding ?: return

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        val targetCenterX = metrics.widthPixels / 2
        val targetCenterY = metrics.heightPixels - dismissBottomMarginPx - dismissTargetSizePx / 2

        val dx = (buttonCenterX - targetCenterX).toDouble()
        val dy = (buttonCenterY - targetCenterY).toDouble()
        val distance = sqrt(dx * dx + dy * dy)
        val hovering = distance < dismissThresholdPx

        if (hovering != isHoveringDismiss) {
            isHoveringDismiss = hovering
            if (hovering) {
                binding.ivDismissTarget.setBackgroundResource(R.drawable.dismiss_target_bg_active)
                binding.root.animate().scaleX(1.25f).scaleY(1.25f).setDuration(120).start()
            } else {
                binding.ivDismissTarget.setBackgroundResource(R.drawable.dismiss_target_bg)
                binding.root.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
        }
    }

    private fun removeButton() {
        buttonBinding?.let {
            try {
                windowManager.removeView(it.root)
            } catch (_: Exception) {
            }
        }
        buttonBinding = null
    }

    /** Resizes the icon + its background circle to match the current size preference. */
    private fun applySizeToButtonViews(binding: FloatingButtonLayoutBinding) {
        val iconSize = iconSizePx()
        binding.ivFloatingButton.layoutParams = binding.ivFloatingButton.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        binding.ivFloatingIcon.layoutParams = binding.ivFloatingIcon.layoutParams.apply {
            width = iconSize
            height = iconSize
        }
        val iconPadding = (iconSize * 0.25f).toInt()
        binding.ivFloatingIcon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
    }

    // ---------------- Sound panel ----------------

    private fun togglePanel() {
        if (panelVisible) {
            removePanel()
        } else {
            showPanel()
        }
    }

    private fun showPanel() {
        if (panelBinding != null) return
        val themedContext = ContextThemeWrapper(this, R.style.Theme_PieBoard)
        val binding = FloatingPanelLayoutBinding.inflate(LayoutInflater.from(themedContext))
        panelBinding = binding

        panelAdapter = FloatingSoundAdapter(
            emptyList(),
            onClick = { sound -> soundPlayer.play(sound.filePath, prefs.loopEnabled) },
            onToggleFavorite = { sound ->
                serviceScope.launch {
                    repository.toggleFavorite(sound)
                    loadSoundsIntoPanel()
                }
            }
        )
        binding.rvPanelSounds.layoutManager = GridLayoutManager(themedContext, 3)
        binding.rvPanelSounds.adapter = panelAdapter

        updateLoopButtonState()
        binding.btnLoopToggle.setOnClickListener {
            prefs.loopEnabled = !prefs.loopEnabled
            soundPlayer.setLooping(prefs.loopEnabled)
            updateLoopButtonState()
        }

        binding.btnStop.setOnClickListener {
            soundPlayer.stop()
        }

        currentPanelTab = PanelTab.ALL
        updateTabStyles()
        binding.tvTabAll.setOnClickListener {
            if (currentPanelTab != PanelTab.ALL) {
                currentPanelTab = PanelTab.ALL
                updateTabStyles()
                loadSoundsIntoPanel()
            }
        }
        binding.tvTabFavorites.setOnClickListener {
            if (currentPanelTab != PanelTab.FAVORITES) {
                currentPanelTab = PanelTab.FAVORITES
                updateTabStyles()
                loadSoundsIntoPanel()
            }
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            panelWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        panelParams = params

        windowManager.addView(binding.root, params)
        panelVisible = true
        updatePanelPosition()
        loadSoundsIntoPanel()
    }

    private fun updateLoopButtonState() {
        val binding = panelBinding ?: return
        binding.btnLoopToggle.setBackgroundResource(
            if (prefs.loopEnabled) R.drawable.circle_accent_bg else R.drawable.circle_glass_bg
        )
    }

    private fun updateTabStyles() {
        val binding = panelBinding ?: return
        val white = androidx.core.content.ContextCompat.getColor(this, R.color.white)
        val secondary = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)
        if (currentPanelTab == PanelTab.ALL) {
            binding.tvTabAll.setBackgroundResource(R.drawable.tab_pill_active_bg)
            binding.tvTabAll.setTextColor(white)
            binding.tvTabFavorites.background = null
            binding.tvTabFavorites.setTextColor(secondary)
        } else {
            binding.tvTabFavorites.setBackgroundResource(R.drawable.tab_pill_active_bg)
            binding.tvTabFavorites.setTextColor(white)
            binding.tvTabAll.background = null
            binding.tvTabAll.setTextColor(secondary)
        }
    }

    private fun removePanel() {
        panelBinding?.let {
            try {
                windowManager.removeView(it.root)
            } catch (_: Exception) {
            }
        }
        panelBinding = null
        panelParams = null
        panelVisible = false
    }

    /**
     * Always places the panel directly beside the button (right side preferred, left as
     * fallback), never overlapping it, using the same size math as the button window itself
     * so the two never disagree about where the button actually is.
     */
    private fun updatePanelPosition() {
        val bParams = buttonParams ?: return
        val pParams = panelParams ?: return
        val binding = panelBinding ?: return

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val buttonSize = windowButtonSizePx()

        val rightX = bParams.x + buttonSize + panelMarginPx
        val leftX = bParams.x - panelWidthPx - panelMarginPx
        val fitsRight = rightX + panelWidthPx <= screenWidth - panelMarginPx
        val fitsLeft = leftX >= panelMarginPx

        // Prefer whichever side actually has room; if neither fits perfectly (very narrow
        // screen), fall back to the side with more space instead of clamping back onto
        // the button's own position.
        val targetX = when {
            fitsRight -> rightX
            fitsLeft -> leftX
            else -> {
                val spaceRight = screenWidth - (bParams.x + buttonSize)
                val spaceLeft = bParams.x
                if (spaceRight >= spaceLeft) {
                    screenWidth - panelWidthPx - panelMarginPx
                } else {
                    panelMarginPx
                }
            }
        }

        var targetY = bParams.y
        if (targetY + panelEstimatedHeightPx > screenHeight) {
            targetY = (screenHeight - panelEstimatedHeightPx).coerceAtLeast(panelMarginPx)
        }
        if (targetY < panelMarginPx) targetY = panelMarginPx

        pParams.x = targetX
        pParams.y = targetY
        try {
            windowManager.updateViewLayout(binding.root, pParams)
        } catch (_: Exception) {
        }
    }

    private fun loadSoundsIntoPanel() {
        serviceScope.launch {
            val sounds = if (currentPanelTab == PanelTab.FAVORITES) {
                repository.getFavoritesOnce()
            } else {
                repository.getAllOnce()
            }
            panelAdapter?.submitList(sounds)
            panelBinding?.let { binding ->
                binding.tvPanelEmpty.text = if (currentPanelTab == PanelTab.FAVORITES) {
                    getString(R.string.panel_favorite_empty)
                } else {
                    getString(R.string.empty_state)
                }
                binding.tvPanelEmpty.visibility = if (sounds.isEmpty()) View.VISIBLE else View.GONE
                binding.rvPanelSounds.visibility = if (sounds.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    // ---------------- Foreground notification ----------------

    private fun buildNotification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "pieboard_floating_channel"
        private const val FIXED_ICON_SIZE_DP = 44
    }
}
