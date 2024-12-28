package com.example.clipy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.app.NotificationCompat
import okhttp3.*
import java.io.File
import java.io.OutputStream

class ClipboardService : Service() {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isPaused = false
    private var connectionCode: String? = null

    companion object {
        const val ACTION_SEND_TEXT = "com.example.clipy.ACTION_SEND_TEXT"
        const val EXTRA_TEXT_CONTENT = "com.example.clipy.EXTRA_TEXT_CONTENT"
        const val ACTION_START_SYNC = "com.example.clipy.ACTION_START_SYNC"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        private const val NOTIFICATION_ID = 1
        const val BROADCAST_UPDATE_UI = "com.example.clipy.UPDATE_UI"
    }

    private fun broadcastServiceStatus(isRunning: Boolean) {
        val intent = Intent(BROADCAST_UPDATE_UI).apply {
            putExtra("isServiceRunning", isRunning)
        }
        sendBroadcast(intent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        setupNotification()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateNotification() {
        setupNotification()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SYNC -> {
                val passedConnectionCode = intent.getStringExtra("connection_code")
                if (passedConnectionCode != null) {
                    this.connectionCode = passedConnectionCode
                    connectWebSocket()
                    broadcastServiceStatus(true)
                } else {
                    showToast("No connection code provided")
                }
            }
            ACTION_SEND_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT_CONTENT)
                if (text != null) {
                    sendTextOverWebSocket(text)
                }
            }

            ACTION_STOP -> {
                broadcastServiceStatus(false)
                stopSelf()
                closeClipboardService()
                showToast("Clipboard Sync Stopped")
            }

            ACTION_PAUSE -> {
                isPaused = true
                updateNotification()
                showToast("Clipboard Sync Paused")
            }

            ACTION_RESUME -> {
                isPaused = false
                updateNotification()
                showToast("Clipboard Sync Resumed")
            }

            else -> {
                if (webSocket == null) {
                    connectWebSocket()
                }
            }
        }

        // Retrieve shared text from the intent if it exists
        val sharedText = intent?.getStringExtra("sharedText")
        val sharedImage = intent?.getStringExtra("SharedImage")
        if (sharedText != null) {
            sendSharedText(sharedText)
        }
        if (sharedImage != null) {
            sendSharedText(sharedImage)
            Log.d("Share image from sending activity intent:", sharedImage)
        }

        return START_NOT_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupNotification() {
        val channelId = "clipboard_sync_channel"
        val channelName = "Clipboard Sync Service"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Notification Channel
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Clipboard Sync Notifications"
        }
        notificationManager.createNotificationChannel(channel)

        // Intent to open the app
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent for Stop Action
        val stopIntent = Intent(this, ClipboardService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent for Pause/Resume Action
        val pauseResumeIntent = Intent(this, ClipboardService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this,
            2,
            pauseResumeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent for Send Action (opens TextInputActivity)
        val sendIntent = Intent(this, TextInputActivity::class.java)
        val sendPendingIntent = PendingIntent.getActivity(
            this,
            3,
            sendIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create Notification
        val contentText = if (isPaused) "Clipboard Sync Paused" else "Syncing clipboard data..."
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Clipboard Sync")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_pause,
                if (isPaused) "Resume" else "Pause",
                pauseResumePendingIntent
            )
            .addAction(R.drawable.ic_send, "Send", sendPendingIntent)  // Add Send button
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun connectWebSocket() {

        val request = connectionCode?.let { Request.Builder().url(it).build() }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected to Computer")
                this@ClipboardService.webSocket = webSocket
                if (!isPaused) {
                    showToast("Connected to Computer")

                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isPaused) {
                    Log.d("WebSocket", "Received message: $text")
                    // remove the "text:" prefix from the text
                    updateClipboard(text)
                } else {
                    Log.d("WebSocket", "Message received but ignored due to pause: $text")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Connection error: ${t.message}")
                if (!isPaused) {
                    reconnectWebSocket()
                    showToast("WebSocket Connection Failed")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Connection closed: $reason")
                if (!isPaused) {
                    showToast("WebSocket Connection Closed")
                }
            }
        }

        if (request != null) {
            client.newWebSocket(request, listener)
        }
    }

    // Retry to connect to the server if the connection is lost
    private var retryCount = 0
    private val maxRetries = 3
    private fun reconnectWebSocket() {
        if (retryCount < maxRetries) {
            retryCount++
            showToast("Reconnecting... (Attempt $retryCount/$maxRetries)")
            webSocket?.close(1000, "Reconnecting...")
            webSocket = null
            mainHandler.postDelayed({
                connectWebSocket()
            }, 2000) // Retry after 2 seconds
        } else {
            showToast("Max retries reached. Unable to reconnect.")
            closeClipboardService()//            showRetryAlert()
        }
    }

    private fun showRetryAlert() {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(applicationContext)
                .setTitle("Connection Failed")
                .setMessage("Unable to connect to the Computer. Would you like to keep trying or cancel?")
                .setPositiveButton("Retry") { _, _ ->
                    retryCount = 0 // Reset the retry count
                    reconnectWebSocket()
                }
                .setNegativeButton("Cancel") { _, _ ->
                    Toast.makeText(this, "Reconnection attempts canceled.", Toast.LENGTH_SHORT)
                        .show()
                    retryCount = 0 // Reset the retry count
                }
                .setCancelable(false)
                .show()
        }
    }


    private fun updateClipboard(content: String) {
        val clipboardManager =
            getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager

        when {
            // Handle text content
            content.startsWith("text:") -> {
                val text = content.removePrefix("text:")
                val clip = android.content.ClipData.newPlainText("Clipboard Data", text)
                clipboardManager.setPrimaryClip(clip)
                Log.d("Clipboard", "Clipboard updated with text: $text")
                showToast("Text copied to clipboard")
            }

            content.startsWith("image:") -> {
                val base64Image = content.removePrefix("image:")
                try {
                    // Decode Base64 image
                    val imageBytes =
                        android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT)
                    saveImageToClipyFolder(imageBytes)

//                    val file = saveImageToClipyFolder(imageBytes)
                    // Copy file path to clipboard
//                    val clip = android.content.ClipData.newPlainText("Clipboard Image Path", file.absolutePath)
//                    clipboardManager.setPrimaryClip(clip)
//                    Log.d("Clipboard", "Clipboard updated with image path: ${file.absolutePath}")
                    showToast("Image saved to gallery")
                } catch (e: Exception) {
                    Log.e("Clipboard", "Failed to decode and save image: ${e.message}")
                    showToast("Failed to save image")
                }
            }

            else -> {
                Log.w("Clipboard", "Unknown content type received: $content")
                showToast("Unsupported content type")
            }
        }
    }

    private fun sendTextOverWebSocket(text: String) {
        if (!isPaused && webSocket != null) {
            if (text.startsWith("text:")) {
                webSocket?.send(text)
                Log.d("WebSocket", "Sent text: $text")
            } else if (text.startsWith("image:")) {
                webSocket?.send(text)
                Log.d("WebSocket", "Sent image: $text")
            }
        } else if (isPaused) {
            Log.d("WebSocket", "Cannot send text while paused")
            showToast("Cannot send. Sync is paused")
        } else {
            Log.d("WebSocket", "WebSocket not connected")
            showToast("Failed to send : WebSocket not connected")
        }
    }

    // Public method to allow external access
    fun sendSharedText(text: String) {
        sendTextOverWebSocket(text)  // Call the private method from within the service
    }

    private fun showToast(message: String) {
        // Use a handler tied to the main looper to ensure the toast is shown on the main thread
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToClipyFolder(imageBytes: ByteArray): File {
        val contentResolver = contentResolver

        // Create the image content values (to insert into MediaStore)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "image_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/clipy"
            ) // Save in the "clipy" folder inside "Pictures"
        }

        // Insert the image into MediaStore (external storage)
        val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        imageUri?.let { uri ->
            try {
                // Open an output stream to write the image data
                val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                outputStream?.use { stream ->
                    stream.write(imageBytes)  // Write the image bytes to the file
                }

                Log.d("Clipboard", "Image saved to: $uri")

                // Notify the media scanner to make the image visible in the gallery
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(uri.path),
                    arrayOf("image/png"),
                    null
                )
            } catch (e: Exception) {
                Log.e("Clipboard", "Error saving image: $e")
            }
        }
        return File(imageUri?.path ?: "") // Return the file path if successful
    }


    private fun closeClipboardService() {
        broadcastServiceStatus(false)
        webSocket?.close(1000, "Service Stopped")
        webSocket = null
        stopService(Intent(this, ClipboardService::class.java))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        broadcastServiceStatus(false)
        webSocket?.close(1000, "Service Stopped")
        webSocket = null
        showToast("Clipboard Service Stopped")
    }
}