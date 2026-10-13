package com.example.multivideo

import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private var slots = 8
    private var uris: List<Uri> = emptyList()
    private val players = mutableListOf<ExoPlayer>()
    private val views = mutableListOf<PlayerView>()

    private var paused = false
    private var muted = true
    private var uiHidden = false

    private lateinit var grid: LinearLayout
    private lateinit var controls: View
    private lateinit var hint: TextView
    private lateinit var btnPause: Button
    private lateinit var btnMute: Button
    private lateinit var btnMode: Button

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { result ->
            if (result.isNullOrEmpty()) return@registerForActivityResult
            if (result.size > slots) {
                Toast.makeText(this, "Using the first $slots videos", Toast.LENGTH_SHORT).show()
            }
            uris = result.take(slots)
            rebuild()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }
        hideSystemBars()

        grid = findViewById(R.id.grid)
        controls = findViewById(R.id.controls)
        hint = findViewById(R.id.hint)
        btnPause = findViewById(R.id.btnPause)
        btnMute = findViewById(R.id.btnMute)
        btnMode = findViewById(R.id.btnMode)

        findViewById<Button>(R.id.btnPick).setOnClickListener {
            picker.launch(arrayOf("video/*"))
        }
        btnMode.setOnClickListener { toggleMode() }
        btnPause.setOnClickListener { togglePause() }
        findViewById<Button>(R.id.btnReset).setOnClickListener { resetAll() }
        btnMute.setOnClickListener { toggleMute() }
        findViewById<Button>(R.id.btnHide).setOnClickListener { setUiHidden(true) }

        rebuild()
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    // Any tap while the UI is hidden brings it back.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (uiHidden) {
            if (ev.action == MotionEvent.ACTION_UP) setUiHidden(false)
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun currentResizeMode(): Int =
        if (uiHidden) AspectRatioFrameLayout.RESIZE_MODE_ZOOM   // fill each cell, no gaps
        else AspectRatioFrameLayout.RESIZE_MODE_FIT             // show whole video

    private fun setUiHidden(hidden: Boolean) {
        uiHidden = hidden
        controls.visibility = if (hidden) View.GONE else View.VISIBLE
        hint.visibility = if (!hidden && uris.isEmpty()) View.VISIBLE else View.GONE
        views.forEach { it.resizeMode = currentResizeMode() }
    }

    private fun rebuild() {
        releaseAll()
        grid.removeAllViews()

        val cols = if (slots == 8) 4 else 2
        var index = 0
        repeat(2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }
            repeat(cols) {
                val pv = PlayerView(this).apply {
                    useController = false
                    setShutterBackgroundColor(Color.BLACK)
                    resizeMode = currentResizeMode()
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
                    )
                }
                row.addView(pv)
                views.add(pv)

                val uri = uris.getOrNull(index)
                if (uri != null) {
                    val player = newPlayer(uri)
                    pv.player = player
                    players.add(player)
                }
                index++
            }
            grid.addView(row)
        }

        hint.visibility = if (uris.isEmpty() && !uiHidden) View.VISIBLE else View.GONE
        btnMode.text = "Layout: $slots"
    }

    private fun newPlayer(uri: Uri): ExoPlayer {
        // Small buffers: these are local files, so we don't need to read far ahead.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 4000, 500, 1000)
            .build()
        val renderers = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
        return ExoPlayer.Builder(this, renderers)
            .setLoadControl(loadControl)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE   // loop forever
                volume = if (muted) 0f else 1f
                setMediaItem(MediaItem.fromUri(uri))
                playWhenReady = !paused
                prepare()
            }
    }

    private fun togglePause() {
        paused = !paused
        players.forEach { it.playWhenReady = !paused }
        btnPause.text = if (paused) "Play" else "Pause"
    }

    private fun resetAll() {
        players.forEach {
            it.playWhenReady = false
            it.seekTo(0)
        }
        if (!paused) players.forEach { it.playWhenReady = true }
    }

    private fun toggleMute() {
        muted = !muted
        players.forEach { it.volume = if (muted) 0f else 1f }
        btnMute.text = if (muted) "Unmute" else "Mute"
    }

    private fun toggleMode() {
        slots = if (slots == 8) 4 else 8
        uris = uris.take(slots)
        rebuild()
    }

    private fun releaseAll() {
        players.forEach { it.release() }
        players.clear()
        views.clear()
    }

    override fun onStart() {
        super.onStart()
        if (!paused) players.forEach { it.playWhenReady = true }
    }

    override fun onStop() {
        super.onStop()
        players.forEach { it.playWhenReady = false }
    }

    override fun onDestroy() {
        releaseAll()
        super.onDestroy()
    }
}
