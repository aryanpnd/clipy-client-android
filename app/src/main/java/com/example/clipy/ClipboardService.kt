package com.example.clipy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import okhttp3.*

class ClipboardService : Service() {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isPaused = false

    companion object {
        const val CHANNEL_ID = "ClipboardServiceChannel"
        const val ACTION_SEND_TEXT = "com.example.clipy.ACTION_SEND_TEXT"
        const val EXTRA_TEXT_CONTENT = "com.example.clipy.EXTRA_TEXT_CONTENT"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        private const val NOTIFICATION_ID = 1
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
            ACTION_SEND_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT_CONTENT)
                if (text != null) {
                    sendTextOverWebSocket(text)
                } else {
                    showToast("No text provided to send")
                }
            }
            ACTION_STOP -> {
                stopSelf()
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
        if (sharedText != null) {
            // Handle the shared text (send it to the server)
            sendSharedText(sharedText)
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

        // Create Notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Clipboard Sync")
            .setContentText("Syncing clipboard data...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppPendingIntent) // Open app on tap
            .addAction(
                R.drawable.ic_pause,
                if (isPaused) "Resume" else "Pause",
                pauseResumePendingIntent
            )
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true) // Make it a foreground notification
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun connectWebSocket() {
        val request = Request.Builder().url("ws://192.168.255.229:8080").build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected to server")
                this@ClipboardService.webSocket = webSocket
                if (!isPaused) {
                    webSocket.send("START_SYNC")
                    showToast("Connected to Server")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isPaused) {
                    Log.d("WebSocket", "Received message: $text")
                    updateClipboard(text)
                    showToast("Received from Server: $text")
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

        client.newWebSocket(request, listener)
    }

    private fun reconnectWebSocket() {
        webSocket?.close(1000, "Reconnecting...")
        webSocket = null
        connectWebSocket()
    }

    private fun updateClipboard(content: String) {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Clipboard Data", content)
        clipboardManager.setPrimaryClip(clip)
        Log.d("Clipboard", "Clipboard updated with: $content")
        showToast("Clipboard Updated: $content")
    }

    private fun sendTextOverWebSocket(text: String) {
        if (!isPaused && webSocket != null) {
            webSocket?.send(text)
            Log.d("WebSocket", "Sent text: $text")
            showToast("Text Sent to Server: $text")
        } else if (isPaused) {
            Log.d("WebSocket", "Cannot send text while paused")
            showToast("Cannot send text while paused")
        } else {
            Log.d("WebSocket", "WebSocket not connected")
            showToast("Failed to send text: WebSocket not connected")
        }
    }

    // Public method to allow external access
    fun sendSharedText(text: String) {
        sendTextOverWebSocket(text)  // Call the private method from within the service
    }

    private fun showToast(message: String) {
        // Ensure Toast is called on the main thread
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Service Stopped")
        webSocket = null
        showToast("Clipboard Service Stopped")
    }
}