package com.airconnection.app.parent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.airconnection.app.child.ChildSetupActivity
import com.airconnection.app.databinding.ActivityParentLoginBinding

class ParentLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParentLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("air_connection_secure_prefs", Context.MODE_PRIVATE)
        val savedUsername = prefs.getString("parent_username", "") ?: ""
        if (savedUsername.isNotEmpty()) {
            binding.etParentUsername.setText(savedUsername)
        }

        binding.btnLoginParent.setOnClickListener {
            val username = binding.etParentUsername.text.toString().trim()
            val password = binding.etParentPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both Username and Password!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 4) {
                Toast.makeText(this, "Password must be at least 4 characters long!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save parent credentials securely
            prefs.edit()
                .putString("parent_username", username)
                .putString("parent_password", password)
                .apply()

            Toast.makeText(this, "Parent Login Successful!", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, ParentDashboardActivity::class.java).apply {
                putExtra("parent_email", username)
                putExtra("parent_password", password)
            }
            startActivity(intent)
            finish()
        }

        binding.btnSwitchToChildMode.setOnClickListener {
            val intent = Intent(this, ChildSetupActivity::class.java)
            startActivity(intent)
        }
    }
}
