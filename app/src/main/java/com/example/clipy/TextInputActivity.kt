package com.example.clipy

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class TextInputActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_input)

        val editText = findViewById<EditText>(R.id.textInput)
        val sendButton = findViewById<Button>(R.id.sendButton)
        val pasteButton = findViewById<Button>(R.id.pasteButton)  // Reference to the new button

        sendButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotEmpty()) {
                val intent = Intent(this, ClipboardService::class.java).apply {
                    action = ClipboardService.ACTION_SEND_TEXT
                    putExtra(ClipboardService.EXTRA_TEXT_CONTENT, "text:$text")
                }
                startService(intent)
                finish()
            }
        }

        // Handle the "Paste from Clipboard" button click
        pasteButton.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text
                editText.setText(text)
            }
        }
    }
}
