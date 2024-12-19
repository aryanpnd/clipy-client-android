package com.example.clipy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import okhttp3.*

class ClipboardService : Service() {

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()

    companion object {
        const val CHANNEL_ID = "ClipboardServiceChannel"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        setupNotification()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupNotification()
        connectWebSocket() // Connect WebSocket to handle incoming data
        return START_STICKY
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupNotification() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Clipboard Service Channel",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            enableVibration(false)
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Clipboard Sync")
            .setContentText("Clipboard sync is running.")
            .setSmallIcon(R.drawable.ic_notification) // Ensure this is valid
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun connectWebSocket() {
        val request = Request.Builder().url("ws://192.168.255.229:8080").build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("START_SYNC") // Send a signal to start the clipboard sync process
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Handle incoming messages if needed (this part is removed for now)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                reconnectWebSocket()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // Handle WebSocket closure if needed
            }
        }

        client.newWebSocket(request, listener)
    }

    private fun reconnectWebSocket() {
        webSocket?.close(1000, "Reconnecting...")
        connectWebSocket()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        webSocket?.close(1000, "Service stopped")
        super.onDestroy()
    }
}
