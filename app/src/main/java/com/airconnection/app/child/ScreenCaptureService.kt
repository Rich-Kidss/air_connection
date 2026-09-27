package com.airconnection.app.child

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.airconnection.app.R
import com.airconnection.app.network.SignalingClient
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "air_connection_capture_channel"
        private const val NOTIFICATION_ID = 1001

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_PARENT_EMAIL = "extra_parent_email"
        const val EXTRA_DEVICE_ID = "extra_device_id"

        var isRunning = false
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    private var parentEmail: String = ""
    private var deviceId: String = ""
    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 320

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        parentEmail = intent.getStringExtra(EXTRA_PARENT_EMAIL) ?: ""
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: ""

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)

            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

            initScreenDimensions()
            startCapture()
            isRunning = true
            Log.d(TAG, "Screen Capture Service Started Successfully!")
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    private fun initScreenDimensions() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels / 2 // Downscale for faster streaming
        screenHeight = metrics.heightPixels / 2
        screenDensity = metrics.densityDpi
    }

    private fun startCapture() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AirConnectionMirror",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image: Image? = reader.acquireLatestImage()
            if (image != null) {
                processAndSendFrame(image)
                image.close()
            }
        }, handler)
    }

    private fun processAndSendFrame(image: Image) {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
            val baos = ByteArrayOutputStream()
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 45, baos)
            val imageBytes = baos.toByteArray()
            val base64Frame = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            SignalingClient.sendScreenFrame(parentEmail, deviceId, base64Frame)
            bitmap.recycle()
            croppedBitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing screen frame", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Air Connection Screen Control",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Air Connection Active")
            .setContentText("Parental live mirror is streaming...")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        Log.d(TAG, "Screen Capture Service Stopped.")
    }
}
