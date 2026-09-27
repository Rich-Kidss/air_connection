package com.airconnection.app.parent

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.airconnection.app.databinding.ActivityLiveCameraBinding
import com.airconnection.app.network.SignalingClient

class LiveCameraActivity : AppCompatActivity(), SignalingClient.SignalingListener {

    private lateinit var binding: ActivityLiveCameraBinding
    private var targetDeviceId = ""
    private var childName = "Child Phone"
    private var currentFacing = "back"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetDeviceId = intent.getStringExtra("device_id") ?: ""
        childName = intent.getStringExtra("child_name") ?: "Child Phone"

        binding.tvCameraChildName.text = "$childName - Live Camera"
        binding.btnBackCamera.setOnClickListener { finish() }

        SignalingClient.listener = this
        SignalingClient.startCamera(targetDeviceId, currentFacing)

        binding.btnSwitchFront.setOnClickListener {
            currentFacing = "front"
            binding.loadingCameraPlaceholder.visibility = View.VISIBLE
            SignalingClient.switchCamera(targetDeviceId, "front")
        }

        binding.btnSwitchBack.setOnClickListener {
            currentFacing = "back"
            binding.loadingCameraPlaceholder.visibility = View.VISIBLE
            SignalingClient.switchCamera(targetDeviceId, "back")
        }
    }

    override fun onCameraFrameReceived(deviceId: String, frameBase64: String) {
        if (deviceId == targetDeviceId) {
            try {
                val decodedBytes = Base64.decode(frameBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                runOnUiThread {
                    binding.loadingCameraPlaceholder.visibility = View.GONE
                    binding.ivCameraStream.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SignalingClient.stopCamera(targetDeviceId)
    }
}
