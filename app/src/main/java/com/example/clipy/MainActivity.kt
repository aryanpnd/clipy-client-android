package com.example.clipy

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var toggleServiceButton: Button
    private lateinit var sendButton: Button
    private lateinit var textArea: EditText
    private lateinit var statusTextView: TextView
    private var isServiceRunning = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleServiceButton = findViewById(R.id.toggleServiceButton)
        sendButton = findViewById(R.id.send_button)
        textArea = findViewById(R.id.text_area)
        statusTextView = findViewById(R.id.status)

        // Check if the service is already running
        isServiceRunning = isClipboardServiceRunning()

        // Update the button text based on the service state
        updateButtonUI()

        toggleServiceButton.setOnClickListener {
            if (isServiceRunning) {
                stopService(Intent(this, ClipboardService::class.java))
                isServiceRunning = false
                Toast.makeText(this, "Clipboard Sync Stopped", Toast.LENGTH_SHORT).show()
            } else {
                startForegroundService(Intent(this, ClipboardService::class.java))
                isServiceRunning = true
                Toast.makeText(this, "Clipboard Sync Started", Toast.LENGTH_SHORT).show()
            }
            updateButtonUI()
        }

        sendButton.setOnClickListener {
            val text = textArea.text.toString()
            if (text.isNotBlank()) {
                sendTextToService(text)
                textArea.text.clear()
                Toast.makeText(this, "Text Sent", Toast.LENGTH_SHORT).show()
            } else {
                textArea.error = "Text cannot be empty"
                Toast.makeText(this, "Please enter some text to send", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isClipboardServiceRunning(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (ClipboardService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun updateButtonUI() {
        toggleServiceButton.text = if (isServiceRunning) {
            "Stop Clipboard Sync"
        } else {
            "Start Clipboard Sync"
        }

        if (isServiceRunning) {
            toggleServiceButton.setBackgroundColor(resources.getColor(R.color.red))
            sendButton.isEnabled = true
        } else {
            toggleServiceButton.setBackgroundColor(resources.getColor(R.color.primary))
            sendButton.isEnabled = false
        }

        statusTextView.text = "Status: ${if (isServiceRunning) "Running" else "Stopped"}"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedText(intent)
    }

    private fun handleSharedText(intent: Intent) {
        Toast.makeText(this, "inside shared text", Toast.LENGTH_SHORT).show()

        // Check if the intent has shared text
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            sharedText?.let {
                // Check if ClipboardService is running
                if (isClipboardServiceRunning()) {
                    sendTextToService(it)
                    Toast.makeText(this, "Text shared successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Clipboard service not running", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    private fun sendTextToService(text: String) {
        val intent = Intent(this, ClipboardService::class.java).apply {
            action = ClipboardService.ACTION_SEND_TEXT
            putExtra(ClipboardService.EXTRA_TEXT_CONTENT, text)
        }
        startService(intent)
    }
}
