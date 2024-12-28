package com.example.clipy

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.core.text.HtmlCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {

    private lateinit var toggleServiceButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var openSavedImagesButton: Button
    private lateinit var sendTextButton: Button
    private lateinit var qrScannerLauncher: ActivityResultLauncher<ScanOptions>
    private var isServiceRunning = false
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var helpButton: TextView
    private val PERMISSION_DENY_LIMIT = 3

    // Define permission request launcher
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d("Permission", "Notification permission granted")
            } else {
                val denyCount = sharedPreferences.getInt("notification_deny_count", 0) + 1
                sharedPreferences.edit().putInt("notification_deny_count", denyCount).apply()
                if (denyCount >= PERMISSION_DENY_LIMIT) {
                    navigateToNotificationSettings()
                } else {
                    showPermissionDeniedDialog()
                }
            }
        }

    private val updateUIReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.example.clipy.UPDATE_UI" -> {
                    isServiceRunning = intent.getBooleanExtra("isServiceRunning", false)
                    updateButtonUI()
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("ClipyPrefs", Context.MODE_PRIVATE)
        showFirstLaunchAlerts()

        val filter = IntentFilter().apply {
            addAction("com.example.clipy.UPDATE_UI")
            addAction("com.example.clipy.SERVICE_STOPPED")
        }
        registerReceiver(updateUIReceiver, filter)

        toggleServiceButton = findViewById(R.id.toggleServiceButton)
        statusTextView = findViewById(R.id.status)
        openSavedImagesButton = findViewById(R.id.openSavedImagesButton)
        sendTextButton = findViewById(R.id.sendTextButton)
        helpButton = findViewById(R.id.helpButton)

        openSavedImagesButton.setOnClickListener {
            val intent = Intent(this, SavedImagesActivity::class.java)
            startActivity(intent)
        }

        sendTextButton.setOnClickListener {
            if (!isServiceRunning) {
                Toast.makeText(this, "Service is not running", Toast.LENGTH_SHORT).show()
            }else{
                val intent = Intent(this, TextInputActivity::class.java)
                startActivity(intent)
            }
        }

        helpButton.setOnClickListener {
            showHelpDialog()
        }


        // Check if the service is already running
        isServiceRunning = isClipboardServiceRunning()

        // Update the button text based on the service state
        updateButtonUI()

        // Initialize QR Scanner launcher
        qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                val scannedCode = result.contents
                Log.d("Scanned Code:", scannedCode)
                startClipboardSync(scannedCode)
            } else {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
            }
        }

        toggleServiceButton.setOnClickListener {
            checkNotificationPermission {
                if (isServiceRunning) {
                    stopService(Intent(this, ClipboardService::class.java))
                    isServiceRunning = false
                    Toast.makeText(this, "Clipboard Sync Stopped", Toast.LENGTH_SHORT).show()
                } else {
                    // Show dialog for entering or scanning code
                    showCodeInputDialog()
                }
                updateButtonUI()
            }
        }

        USD.runOnlyFirstTime(this)
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
            toggleServiceButton.setBackgroundDrawable(resources.getDrawable(R.drawable.rounded_button_red))
            sendTextButton.setBackgroundDrawable(resources.getDrawable(R.drawable.rounded_button_green))
        } else {
            toggleServiceButton.setBackgroundDrawable(resources.getDrawable(R.drawable.rounded_button_primary))
            sendTextButton.setBackgroundDrawable(resources.getDrawable(R.drawable.rounded_button_disabled))
        }

        statusTextView.text = "Status: ${if (isServiceRunning) "Running" else "Not running"}"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showCodeInputDialog() {
        val options = arrayOf("Enter Code Manually", "Scan QR Code")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter or Scan Code")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> showManualCodeInput() // Enter code manually
                1 -> startQRScanner() // Scan QR code
            }
        }
        builder.show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showManualCodeInput() {
        val inputEditText = EditText(this)
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter Code")
        builder.setView(inputEditText)
        builder.setPositiveButton("OK") { _, _ ->
            val code = inputEditText.text.toString()
            startClipboardSync(code)
            Log.d("Code Entered:", code)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun startQRScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE) // Only QR codes
            setPrompt("Scan a QR Code")
            setBeepEnabled(true)
            setCameraId(0) // Use the rear camera
            setOrientationLocked(false)
        }
        qrScannerLauncher.launch(options)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startClipboardSync(code: String) {
        if (isValidCode(code)) {
            val intent = Intent(this, ClipboardService::class.java).apply {
                action = ClipboardService.ACTION_START_SYNC
                putExtra("connection_code", code)
            }
            startForegroundService(intent)
            isServiceRunning = true
            updateButtonUI()
        } else {
            Toast.makeText(this, "Invalid code format", Toast.LENGTH_LONG).show()
        }
    }

    private fun isValidCode(code: String): Boolean {
        // Regular expression for ws://<IP>:<PORT>/ws
        val regex = Regex("""^ws://\d{1,3}(\.\d{1,3}){3}:\d{1,5}/ws$""")
        return regex.matches(code)
    }

    private fun sendTextToService(text: String) {
        val intent = Intent(this, ClipboardService::class.java).apply {
            action = ClipboardService.ACTION_SEND_TEXT
            putExtra(ClipboardService.EXTRA_TEXT_CONTENT, "text:$text")
        }
        startService(intent)
    }

    private fun showFirstLaunchAlerts() {
        val launchCount = sharedPreferences.getInt("launch_count", 0)

        if (launchCount < 1) {
            showHelpDialog()
            sharedPreferences.edit().putInt("launch_count", launchCount + 1).apply()
        } else {
            checkNotificationPermission()
        }
    }

    private fun checkNotificationPermission(onGranted: (() -> Unit)? = null) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Log.d("Permission", "Notification permission already granted")
            onGranted?.invoke()
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("The app needs notification permissions to function properly. Please allow it.")
            .setPositiveButton("Try Again") { _, _ ->
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .setCancelable(false)
            .show()
    }

    private fun navigateToNotificationSettings() {
        Toast.makeText(
            this,
            "Please enable notification permissions from settings.",
            Toast.LENGTH_LONG
        ).show()
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivity(intent)
    }

    private fun showHelpDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
        builder.setTitle("Welcome to Clipy")

        // Use Html.fromHtml to make links clickable
        val messageView = TextView(this).apply {
            text = HtmlCompat.fromHtml(
                """
            1. Clipy enables seamless clipboard synchronization between your Android device and PC. To share text, select it and use the 'Share' option with this app, or use the send button from the notification. Ensure clipboard synchronization is active for proper functionality.<br><br>
            
            2. Launch Clipy on your PC and connect by either scanning the QR code displayed or manually entering the provided code.<br><br>
            
            3. Ensure your Android device and PC are connected to the same network to facilitate clipboard synchronization.<br><br>
            
            4. Grant all necessary permissions to ensure the app operates as intended.<br><br>
            
            5. Image sharing currently works reliably from PC to phone. However, sending images from phone to PC may encounter minor bugs and is being improved.<br><br>
            
            6. On devices such as Xiaomi, Redmi, Vivo, Oppo, and OnePlus, avoid clearing Clipy from recent apps to maintain uninterrupted functionality.<br><br>
            
            <b>Developer:</b> <a href="https://github.com/aryanpnd">Aryan</a><br>
            <b>Contribute:</b> <a href="https://github.com/clipy-client-android">Repository</a>
            """.trimIndent(),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            setMovementMethod(LinkMovementMethod.getInstance()) // Enable clickable links
            setTextAppearance(android.R.style.TextAppearance_Medium)
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.black))
            setPadding(32, 16, 32, 16)
        }

        builder.setView(messageView)

        // Customizing the positive button
        builder.setPositiveButton("Got it") { dialog, _ ->
            dialog.dismiss()
            checkNotificationPermission()
        }

        val dialog = builder.create()

        // Modify the positive button after creation to apply custom styles
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.apply {
                // Set the custom rounded background
                background = ContextCompat.getDrawable(context, R.drawable.rounded_button_primary)
                // Make the button take up the maximum width
                val params = this.layoutParams
                params.width = LinearLayout.LayoutParams.MATCH_PARENT
                this.layoutParams = params
                setTextColor(ContextCompat.getColor(context, R.color.white)) // Set text color to white
                textSize = 16f
            }
        }

        builder.setCancelable(false)
        dialog.show()
    }


    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver when the activity is destroyed to avoid memory leaks
        unregisterReceiver(updateUIReceiver)
    }
}
