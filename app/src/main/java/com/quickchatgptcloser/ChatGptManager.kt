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
            // 1. Fetch recent conversation list (limit 1 to get the most recent conversation)
            val listRequest = Request.Builder()
                .url("https://chatgpt.com/backend-api/conversations?offset=0&limit=1")
                .header("Authorization", bearer)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .get()
                .build()

            val listResponse = client.newCall(listRequest).execute()
            val listBody = listResponse.body?.string() ?: return@withContext false
            val json = JSONObject(listBody)
            val items = json.optJSONArray("items") ?: return@withContext false

            if (items.length() > 0) {
                val latestId = items.getJSONObject(0).getString("id")
                Log.d(TAG, "Found latest conversation ID: $latestId. Deleting...")

                // 2. Hide / Delete conversation from account
                val deletePayload = JSONObject().apply {
                    put("is_visible", false)
                }.toString()

                val patchRequest = Request.Builder()
                    .url("https://chatgpt.com/backend-api/conversation/$latestId")
                    .header("Authorization", bearer)
                    .header("Content-Type", "application/json")
                    .patch(deletePayload.toRequestBody("application/json".toMediaType()))
                    .build()

                val patchResponse = client.newCall(patchRequest).execute()
                Log.d(TAG, "Delete response code: ${patchResponse.code}")
                return@withContext patchResponse.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting chat: ${e.message}", e)
        }
        return@withContext false
    }
}
