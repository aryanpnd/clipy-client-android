package com.example.clipy

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.Toast

class SendingActivity : Activity() {
    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sending)

        progressBar = findViewById(R.id.progressBar)

        // Get the shared text
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

        // Start the sending process in the background
        Thread {
            var success = false
            if (isServiceRunning(ClipboardService::class.java)) {
                // If the ClipboardService is running, send the text to the service
                success = sendTextToClipboardService(sharedText)
            } else {
                // If the service is not running, show a toast
                runOnUiThread {
                    Toast.makeText(
                        this@SendingActivity,
                        "Clipboard service is not running",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            // After sending, update the UI and close the activity
            runOnUiThread {
                if (success) {
                    Toast.makeText(
                        this@SendingActivity,
                        "Text sent successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                finish() // Close the SendingActivity
            }
        }.start()
    }

    // Check if the service is running
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    // Send the shared text to the ClipboardService
    private fun sendTextToClipboardService(text: String?): Boolean {
        try {
            val serviceIntent = Intent(
                this@SendingActivity,
                ClipboardService::class.java
            )
            serviceIntent.putExtra("sharedText", text) // Pass the shared text to the service
            startService(serviceIntent)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}