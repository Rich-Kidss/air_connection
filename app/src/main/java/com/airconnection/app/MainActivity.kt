package com.airconnection.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.airconnection.app.child.ChildSetupActivity
import com.airconnection.app.parent.ParentLoginActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Signaling Server & Supabase
        com.airconnection.app.network.SignalingClient.init(this)
        com.airconnection.app.network.SupabaseManager.init()

        if (!handleDeepLink(intent)) {
            val loginIntent = Intent(this, ParentLoginActivity::class.java)
            startActivity(loginIntent)
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleDeepLink(it) }
    }

    private fun handleDeepLink(intent: Intent): Boolean {
        val data: Uri? = intent.data
        if (data != null && (data.scheme == "airconnection" || data.scheme == "https") && data.host == "pair") {
            val code = data.getQueryParameter("code") ?: ""
            val email = data.getQueryParameter("email") ?: ""

            Toast.makeText(this, "Child Setup Link Opened! Code: $code", Toast.LENGTH_LONG).show()

            val childIntent = Intent(this, ChildSetupActivity::class.java).apply {
                putExtra("parent_email", email)
                putExtra("pair_code", code)
            }
            startActivity(childIntent)
            finish()
            return true
        }
        return false
    }
}
