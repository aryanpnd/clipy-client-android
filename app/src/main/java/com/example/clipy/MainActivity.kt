package com.example.clipy

import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var toggleServiceButton: Button
    private var isServiceRunning = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggleServiceButton = findViewById(R.id.toggleServiceButton)

        // Check if the service is already running
        isServiceRunning = isClipboardServiceRunning()

        // Update the button text based on the service state
        updateButtonText()

        toggleServiceButton.setOnClickListener {
            if (isServiceRunning) {
                stopService(Intent(this, ClipboardService::class.java))
                isServiceRunning = false
            } else {
                startForegroundService(Intent(this, ClipboardService::class.java))
                isServiceRunning = true
            }
            updateButtonText()
        }
    }

    private fun isClipboardServiceRunning(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in activityManager.getRunningServices(Int.MAX_VALUE)) {
            if (ClipboardService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun updateButtonText() {
        toggleServiceButton.text = if (isServiceRunning) {
            "Stop Clipboard Sync"
        } else {
            "Start Clipboard Sync"
        }
    }
}
