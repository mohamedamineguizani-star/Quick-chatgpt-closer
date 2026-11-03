package com.quickchatgptcloser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ChatGptManager {

    private const val TAG = "ChatGptManager"
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun deleteLastChat(accessToken: String): Boolean = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            Log.d(TAG, "No access token provided. Skipping remote chat deletion.")
            return@withContext false
        }

        val bearer = if (accessToken.startsWith("Bearer ", ignoreCase = true)) {
            accessToken
        } else {
            "Bearer $accessToken"
        }

        try {
            // 1. Fetch recent conversation list (limit 2 to catch current active chat)
            val listRequest = Request.Builder()
                .url("https://chatgpt.com/backend-api/conversations?offset=0&limit=2")
                .header("Authorization", bearer)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .get()
                .build()

            val listResponse = client.newCall(listRequest).execute()
            val listBody = listResponse.body?.string() ?: return@withContext false
            val json = JSONObject(listBody)
            val items = json.optJSONArray("items") ?: return@withContext false

            var success = false
            for (i in 0 until items.length()) {
                val convId = items.getJSONObject(i).getString("id")
                Log.d(TAG, "Deleting conversation ID: $convId")

                val deletePayload = JSONObject().apply {
                    put("is_visible", false)
                }.toString()

                val patchRequest = Request.Builder()
                    .url("https://chatgpt.com/backend-api/conversation/$convId")
                    .header("Authorization", bearer)
                    .header("Content-Type", "application/json")
                    .patch(deletePayload.toRequestBody("application/json".toMediaType()))
                    .build()

                val patchResponse = client.newCall(patchRequest).execute()
                if (patchResponse.isSuccessful) {
                    success = true
                }
            }
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting chat via backend: ${e.message}", e)
        }
        return@withContext false
    }
}
