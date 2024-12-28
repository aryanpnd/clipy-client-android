package com.example.clipy

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.ProgressBar
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.InputStream

class SendingActivity : Activity() {
    private var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sending)

        progressBar = findViewById(R.id.progressBar)

        // Get the shared text
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        // Get the shared image as a Uri
        val sharedImageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

        // Start the sending process in the background
        Thread {
            var success = false
            if (isServiceRunning(ClipboardService::class.java)) {
                if (sharedText != null) {
                    success = sendTextToClipboardService(sharedText)
                } else if (sharedImageUri != null) {
                    success = sendImageToClipboardService(sharedImageUri)
                }
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
                        "Content sent successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@SendingActivity,
                        "Failed to send content",
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
        return try {
            val serviceIntent = Intent(
                this@SendingActivity,
                ClipboardService::class.java
            )
            serviceIntent.putExtra("sharedText", "text:$text") // Pass the shared text to the service
            startService(serviceIntent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Send the shared image to the ClipboardService
    private fun sendImageToClipboardService(imageUri: Uri): Boolean {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64Image = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            val intent = Intent(this, ClipboardService::class.java).apply {
                action = ClipboardService.ACTION_SEND_TEXT
                putExtra(ClipboardService.EXTRA_TEXT_CONTENT, "image:$base64Image")
            }
            startService(intent)

            true
        } catch (e: Exception) {
            Log.e("SendingActivity", "Failed to send image: ${e.message}")
            false
        }
    }


    // Helper to convert Bitmap to Base64
    private fun convertBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
}
