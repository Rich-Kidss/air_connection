package com.airconnection.app.parent

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.airconnection.app.databinding.ActivityLiveScreenMirrorBinding
import com.airconnection.app.network.SignalingClient

class LiveScreenMirrorActivity : AppCompatActivity(), SignalingClient.SignalingListener {

    private lateinit var binding: ActivityLiveScreenMirrorBinding
    private var targetDeviceId = ""
    private var childName = "Child Phone"
    private var parentEmail = ""

    private var startX = 0f
    private var startY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveScreenMirrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetDeviceId = intent.getStringExtra("device_id") ?: ""
        childName = intent.getStringExtra("child_name") ?: "Child Phone"
        parentEmail = intent.getStringExtra("parent_email") ?: ""

        binding.tvMirrorChildName.text = "$childName - Live Screen"
        binding.btnBackHeader.setOnClickListener { finish() }

        SignalingClient.listener = this
        SignalingClient.startMirroring(targetDeviceId)

        setupTouchControls()
        setupNavButtons()
    }

    private fun setupNavButtons() {
        binding.btnNavBack.setOnClickListener {
            SignalingClient.sendTouchEvent(targetDeviceId, "back", 0f, 0f)
        }
        binding.btnNavHome.setOnClickListener {
            SignalingClient.sendTouchEvent(targetDeviceId, "home", 0f, 0f)
        }
        binding.btnNavRecents.setOnClickListener {
            SignalingClient.sendTouchEvent(targetDeviceId, "recents", 0f, 0f)
        }
    }

    private fun setupTouchControls() {
        binding.ivScreenMirror.setOnTouchListener { v, event ->
            val viewWidth = v.width.toFloat()
            val viewHeight = v.height.toFloat()
            if (viewWidth <= 0 || viewHeight <= 0) return@setOnTouchListener false

            val normX = event.x / viewWidth
            val normY = event.y / viewHeight

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = normX
                    startY = normY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dist = Math.hypot((normX - startX).toDouble(), (normY - startY).toDouble())
                    if (dist < 0.05) {
                        // Tap gesture
                        SignalingClient.sendTouchEvent(targetDeviceId, "tap", normX, normY)
                    } else {
                        // Swipe gesture
                        SignalingClient.sendTouchEvent(targetDeviceId, "swipe", startX, startY, normX, normY)
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    override fun onMirrorFrameReceived(deviceId: String, frameBase64: String) {
        if (deviceId == targetDeviceId) {
            try {
                val decodedBytes = Base64.decode(frameBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                runOnUiThread {
                    binding.loadingPlaceholder.visibility = View.GONE
                    binding.ivScreenMirror.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SignalingClient.sendTouchEvent(targetDeviceId, "stop_mirror", 0f, 0f)
    }
}
