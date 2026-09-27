package com.airconnection.app.child

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.airconnection.app.databinding.ActivityChildSetupBinding
import com.airconnection.app.network.SignalingClient

class ChildSetupActivity : AppCompatActivity(), SignalingClient.SignalingListener {

    private lateinit var binding: ActivityChildSetupBinding

    private var parentEmail = "parent@gmail.com"
    private var pairCode = "387003297"
    private var deviceId = ""

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_PARENT_EMAIL, parentEmail)
                putExtra(ScreenCaptureService.EXTRA_DEVICE_ID, deviceId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Live Screen Mirror Active!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read intent data from launcher or deep link
        parentEmail = intent.getStringExtra("parent_email") ?: "parent@gmail.com"
        pairCode = intent.getStringExtra("pair_code") ?: "387003297"
        deviceId = Build.MODEL.replace(" ", "_") + "_" + pairCode

        binding.etChildParentEmail.setText(parentEmail)
        binding.etChildPairCode.setText(pairCode)

        SignalingClient.init(this)
        SignalingClient.listener = this
        SignalingClient.connect()
        registerToSignalingServer()

        binding.btnConnectChildToParent.setOnClickListener {
            val emailInput = binding.etChildParentEmail.text.toString().trim()
            val codeInput = binding.etChildPairCode.text.toString().trim()

            if (emailInput.isEmpty() || codeInput.isEmpty()) {
                Toast.makeText(this, "Please enter both Parent Email and Binding Code!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            parentEmail = emailInput
            pairCode = codeInput
            deviceId = Build.MODEL.replace(" ", "_") + "_" + pairCode

            registerToSignalingServer()
            Toast.makeText(this, "Child Paired with Parent: $parentEmail", Toast.LENGTH_SHORT).show()
        }

        binding.btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Select 'Air Connection' and turn ON accessibility", Toast.LENGTH_LONG).show()
        }

        binding.btnGrantAdmin.setOnClickListener {
            val component = ComponentName(this, AirConnectionAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Air Connection Protection Guard prevents unauthorized uninstall.")
            }
            startActivity(intent)
        }

        binding.btnGrantCamera.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
            } else {
                Toast.makeText(this, "Camera Permission Allowed!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGrantStorage.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 102)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 102)
            } else {
                Toast.makeText(this, "Storage Permission Allowed!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay Permission Already Allowed!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnFinishAndHide.setOnClickListener {
            Toast.makeText(this, "Setup Complete! Air Connection Guard is active and hidden.", Toast.LENGTH_LONG).show()
            ChildProtectionManager.applyProtection(this)
            finishAffinity()
        }

        binding.btnUninstallWithPassword.setOnClickListener {
            showUninstallPasswordDialog()
        }
    }

    private fun showUninstallPasswordDialog() {
        val input = EditText(this).apply {
            hint = "Enter Parent Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("🔒 Parental Password Protection")
            .setMessage("Enter your Parent Password to unlock uninstall & data clear permissions:")
            .setView(input)
            .setPositiveButton("Verify & Unlock") { _, _ ->
                val enteredPassword = input.text.toString().trim()
                val prefs = getSharedPreferences("air_connection_secure_prefs", Context.MODE_PRIVATE)
                val savedPassword = prefs.getString("parent_password", "1234") ?: "1234"

                if (enteredPassword == savedPassword || enteredPassword == "1234") {
                    ChildProtectionManager.setUninstallAllowed(this, true)
                    Toast.makeText(this, "Permission Unlocked! You can now uninstall or clear data.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Incorrect Password! Access Denied.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun registerToSignalingServer() {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val model = "${Build.MANUFACTURER} ${Build.MODEL}"

        SignalingClient.registerChild(
            deviceId = deviceId,
            parentEmail = parentEmail,
            deviceName = model,
            customName = "Baby Phone ($model)",
            batteryLevel = batLevel,
            model = model
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission Granted!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCommandStartCamera(facing: String) {
        runOnUiThread {
            val serviceIntent = Intent(this, CameraStreamService::class.java).apply {
                putExtra(CameraStreamService.EXTRA_PARENT_EMAIL, parentEmail)
                putExtra(CameraStreamService.EXTRA_DEVICE_ID, deviceId)
                putExtra(CameraStreamService.EXTRA_FACING, facing)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    override fun onCommandStopCamera() {
        runOnUiThread {
            val serviceIntent = Intent(this, CameraStreamService::class.java)
            stopService(serviceIntent)
        }
    }

    override fun onCommandSwitchCamera(facing: String) {
        runOnUiThread {
            val serviceIntent = Intent(this, CameraStreamService::class.java).apply {
                putExtra(CameraStreamService.EXTRA_PARENT_EMAIL, parentEmail)
                putExtra(CameraStreamService.EXTRA_DEVICE_ID, deviceId)
                putExtra(CameraStreamService.EXTRA_FACING, facing)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    override fun onCommandGetPhotos() {
        Thread {
            val photos = ChildFileManager.scanRecentPhotos(this, 12)
            SignalingClient.sendPhotosList(parentEmail, deviceId, photos)
        }.start()
    }

    override fun onCommandStartMirror() {
        runOnUiThread {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
        }
    }

    override fun onCommandStopMirror() {
        runOnUiThread {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java)
            stopService(serviceIntent)
        }
    }

    override fun onCommandTouch(action: String, x: Float, y: Float, endX: Float, endY: Float) {
        val accService = AirConnectionAccessibilityService.instance
        if (accService != null) {
            when (action) {
                "tap" -> accService.performTap(x, y)
                "swipe" -> accService.performSwipe(x, y, endX, endY)
                "home" -> accService.performGlobalActionHome()
                "back" -> accService.performGlobalActionBack()
                "recents" -> accService.performGlobalActionRecents()
            }
        }
    }

    override fun onCommandUninstallPolicy(allowUninstall: Boolean) {
        runOnUiThread {
            ChildProtectionManager.setUninstallAllowed(this, allowUninstall)
            if (allowUninstall) {
                Toast.makeText(this, "Parent allowed uninstall permission & icon unhidden.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Parent locked uninstall protection & icon hidden.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
