package com.airconnection.app.child

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.airconnection.app.R
import com.airconnection.app.network.SignalingClient
import java.io.ByteArrayOutputStream

@Suppress("DEPRECATION")
class CameraStreamService : Service(), Camera.PreviewCallback {

    companion object {
        private const val TAG = "CameraStreamService"
        private const val CHANNEL_ID = "air_connection_camera_channel"
        private const val NOTIFICATION_ID = 1002

        const val EXTRA_PARENT_EMAIL = "extra_parent_email"
        const val EXTRA_DEVICE_ID = "extra_device_id"
        const val EXTRA_FACING = "extra_facing"

        var isRunning = false
    }

    private var camera: Camera? = null
    private var parentEmail: String = ""
    private var deviceId: String = ""
    private var isFrontFacing = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        parentEmail = intent.getStringExtra(EXTRA_PARENT_EMAIL) ?: ""
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: ""
        val facingStr = intent.getStringExtra(EXTRA_FACING) ?: "back"
        isFrontFacing = facingStr.lowercase() == "front"

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        startCameraHardware()
        isRunning = true
        return START_STICKY
    }

    private fun startCameraHardware() {
        stopCameraHardware()
        try {
            val cameraId = getCameraId(isFrontFacing)
            camera = Camera.open(cameraId)
            val params = camera?.parameters
            params?.setPreviewSize(640, 480)
            params?.previewFormat = ImageFormat.NV21
            camera?.parameters = params

            val dummySurfaceTexture = android.graphics.SurfaceTexture(10)
            camera?.setPreviewTexture(dummySurfaceTexture)
            camera?.setPreviewCallback(this)
            camera?.startPreview()
            Log.d(TAG, "Camera Started Successfully! Facing: ${if (isFrontFacing) "Front" else "Back"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera hardware", e)
        }
    }

    private fun getCameraId(front: Boolean): Int {
        val numberOfCameras = Camera.getNumberOfCameras()
        val info = Camera.CameraInfo()
        for (i in 0 until numberOfCameras) {
            Camera.getCameraInfo(i, info)
            if (front && info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return i
            if (!front && info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i
        }
        return 0
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        if (data == null || camera == null) return

        try {
            val parameters = camera.parameters
            val width = parameters.previewSize.width
            val height = parameters.previewSize.height

            val yuvImage = YuvImage(data, ImageFormat.NV21, width, height, null)
            val baos = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 40, baos)
            val imageBytes = baos.toByteArray()

            var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            val matrix = Matrix()
            matrix.postRotate(if (isFrontFacing) 270f else 90f)
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            val outBaos = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, 40, outBaos)
            val base64Frame = Base64.encodeToString(outBaos.toByteArray(), Base64.NO_WRAP)

            SignalingClient.sendCameraFrame(parentEmail, deviceId, base64Frame)

            bitmap.recycle()
            rotated.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing camera frame", e)
        }
    }

    fun switchCameraFacing(front: Boolean) {
        isFrontFacing = front
        startCameraHardware()
    }

    private fun stopCameraHardware() {
        try {
            camera?.setPreviewCallback(null)
            camera?.stopPreview()
            camera?.release()
            camera = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Air Connection Camera Control",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Air Connection Camera Service")
            .setContentText("Parental live camera stream active...")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopCameraHardware()
        Log.d(TAG, "Camera Stream Service Stopped.")
    }
}
