package com.example.clipy

import android.content.Context
import android.os.Build
import android.provider.Settings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object USD {

    private const val SHARED_PREFERENCES_NAME = "com.example.clipy.preferences"
    private const val FIRST_RUN_KEY = "hasRunBefore"
    private const val API_URL = "https://api.jsonstorage.net/v1/json?apiKey=ef41aa56-200f-47a7-b7da-8a74d6af888e"

    // This method runs only for the first time the app is opened
    fun runOnlyFirstTime(context: Context) {
        val sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
        val hasRunBefore = sharedPreferences.getBoolean(FIRST_RUN_KEY, false)

        if (!hasRunBefore) {
            sendUserData(context)
            sharedPreferences.edit().putBoolean(FIRST_RUN_KEY, true).apply()
        }
    }

    private fun sendUserData(context: Context) {
        val deviceData = collectDeviceData(context)

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        // Convert device data to JSON and prepare request body
        val requestBody = deviceData.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(API_URL)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace() // Log error for debugging
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    println("User data sent successfully.")
                } else {
                    println("Failed to send user data: ${response.code}")
                }
            }
        })
    }

    private fun collectDeviceData(context: Context): JSONObject {
        val data = JSONObject()

        try {
            // Example payload structure
            data.put("event", "app_first_launch")

            // Device Information
            data.put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}")
            data.put("brand", Build.BRAND)
            data.put("model", Build.MODEL)
            data.put("product", Build.PRODUCT)
            data.put("hardware", Build.HARDWARE)
            data.put("device", Build.DEVICE)

            // OS Information
            data.put("osVersion", Build.VERSION.RELEASE)
            data.put("sdkVersion", Build.VERSION.SDK_INT)
            data.put("securityPatch", Build.VERSION.SECURITY_PATCH)

            // Locale and Timezone
            val locale = Locale.getDefault()
            data.put("language", locale.language)
            data.put("country", locale.country)
            data.put("timezone", TimeZone.getDefault().id)

            // Unique Device ID (no permissions required)
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            data.put("androidId", androidId)

            // Date and Time (formatted)
            val currentTimeMillis = System.currentTimeMillis()
            data.put("firstLaunchTime", currentTimeMillis)
            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale).format(Date(currentTimeMillis))
            data.put("formattedDate", formattedDate)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return data
    }
}
