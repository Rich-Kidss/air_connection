package com.airconnection.app.child

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AirConnectionAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Air Connection Anti-Uninstall Protection Activated!", Toast.LENGTH_LONG).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val isAllowed = ChildProtectionManager.isUninstallAllowed(context)
        return if (isAllowed) {
            "Uninstall permission granted by parent."
        } else {
            "SECURITY GUARD ACTIVE! This app is protected by Parental Lock. You cannot remove or clear data without Parent Password."
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Air Connection Protection Deactivated.", Toast.LENGTH_SHORT).show()
    }
}
