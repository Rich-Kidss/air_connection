package com.airconnection.app.parent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.airconnection.app.child.ChildSetupActivity
import com.airconnection.app.databinding.ActivityChildPairingBinding
import kotlin.random.Random

import com.airconnection.app.AppConfig

class ChildPairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChildPairingBinding
    private var parentEmail = "parent@gmail.com"
    private var pairCode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        parentEmail = intent.getStringExtra("parent_email") ?: "parent@gmail.com"

        // Generate 9-digit binding code
        pairCode = String.format("%03d %03d %03d", Random.nextInt(100, 999), Random.nextInt(100, 999), Random.nextInt(100, 999))
        val rawCode = pairCode.replace(" ", "")
        val pairUrl = "${AppConfig.childDownloadUrl}?code=$rawCode&email=$parentEmail"

        binding.tvGeneratedCode.text = pairCode
        binding.tvGeneratedUrl.text = pairUrl

        binding.btnCopyLink.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Air Connection Child Setup Link", pairUrl)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Child setup link copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        binding.btnShareLink.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Air Connection Child Setup Link")
                putExtra(Intent.EXTRA_TEXT, "Install Air Connection Child App and pair with Parent:\nLink: $pairUrl\nBinding Code: $rawCode\nParent User: $parentEmail")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Child Setup Link"))
        }

        binding.btnOpenChildSetup.setOnClickListener {
            val intent = Intent(this, ChildSetupActivity::class.java).apply {
                putExtra("parent_email", parentEmail)
                putExtra("pair_code", rawCode)
            }
            startActivity(intent)
        }
    }
}

