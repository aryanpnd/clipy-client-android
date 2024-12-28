package com.example.clipy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class SavedImagesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ImageAdapter
    private lateinit var messageTextView: TextView

    // Permission request launcher for external storage
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                loadImages()  // If permission granted, load images
            } else {
                Toast.makeText(this, "Permission denied, unable to access images", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_images)

        recyclerView = findViewById(R.id.recyclerView)
        messageTextView = findViewById(R.id.messageTextView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Check permissions and load images
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped Storage (Android 10 and above), permission needed for public directories
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                loadImages()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // For Android versions below Q (Android 9 and below), use READ_EXTERNAL_STORAGE
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                loadImages()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun loadImages() {
        val images = getSavedImages()
        if (images.isEmpty()) {
            showMessage("No images saved so far")
        } else {
            adapter = ImageAdapter(images, this)
            recyclerView.adapter = adapter
        }
    }

    private fun getSavedImages(): List<File> {
        val imageList = mutableListOf<File>()

        // Access the 'clipy' folder in external storage (internal_storage)
        val clipyDir = File("/storage/emulated/0/Pictures/clipy")  // Update with correct folder path
        if (clipyDir.exists()) {
            clipyDir.listFiles { file -> file.extension == "png" }?.let {
                imageList.addAll(it)
            }
        }

        return imageList
    }

    private fun showMessage(message: String) {
        messageTextView.text = message
        messageTextView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        Toast.makeText(this, "No images saved so far", Toast.LENGTH_SHORT).show()
    }

    // Refresh images after deletion
    fun refreshImages() {
        loadImages()
    }
}
