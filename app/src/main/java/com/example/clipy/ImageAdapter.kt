package com.example.clipy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class ImageAdapter(private val images: List<File>, private val context: Context) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageFile = images[position]
        holder.bind(imageFile)
    }

    override fun getItemCount() = images.size

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val shareButton: Button = itemView.findViewById(R.id.openFolderButton)
        private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)

        fun bind(imageFile: File) {
            // Load the image into the ImageView
            imageView.setImageURI(Uri.fromFile(imageFile))

            // Set up share button
            shareButton.setOnClickListener {
                shareImage(context,imageFile)
            }

            // Set up delete button with confirmation
            deleteButton.setOnClickListener {
                showDeleteConfirmationDialog(imageFile)
            }
        }

        // Method to share the image
        private fun shareImage(context: Context, imageFile: File) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider", // Authority must match the one in the manifest
                imageFile
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "image/png"
            }

            // Grant permission to external apps to access the shared file
            context.grantUriPermission(
                "com.example.clipy", uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
        }


        private fun showDeleteConfirmationDialog(imageFile: File) {
            AlertDialog.Builder(context)
                .setTitle("Delete Image")
                .setMessage("Are you sure you want to delete this image?")
                .setPositiveButton("Delete") { _, _ ->
                    deleteImage(imageFile)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun deleteImage(imageFile: File) {
            if (imageFile.exists()) {
                val deleted = imageFile.delete()
                if (deleted) {
                    // Refresh the list of images after deletion
                    (context as SavedImagesActivity).refreshImages()
                    Toast.makeText(context, "Image deleted successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to delete the image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
