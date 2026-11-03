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
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun formatBearer(token: String): String {
        val trimmed = token.trim()
        return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
            trimmed
        } else {
            "Bearer $trimmed"
        }
    }

    suspend fun testConnection(token: String): Result<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(Exception("Token cannot be empty"))
        }

        try {
            val request = Request.Builder()
                .url("https://chatgpt.com/backend-api/conversations?offset=0&limit=1")
                .header("Authorization", formatBearer(token))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            if (code == 401 || code == 403) {
                return@withContext Result.failure(Exception("HTTP $code: Invalid or expired token. Please log into chatgpt.com and refresh your token."))
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP $code: ${response.message}"))
            }

            val json = JSONObject(body)
            val items = json.optJSONArray("items")
            if (items != null && items.length() > 0) {
                val firstItem = items.getJSONObject(0)
                val title = firstItem.optString("title", "Untitled Chat")
                return@withContext Result.success(title)
            } else {
                return@withContext Result.success("(No conversations in account)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    suspend fun deleteLastChat(token: String): Result<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(Exception("No token configured"))
        }

        try {
            // 1. Fetch latest conversation
            val listRequest = Request.Builder()
                .url("https://chatgpt.com/backend-api/conversations?offset=0&limit=1")
                .header("Authorization", formatBearer(token))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .get()
                .build()

            val listResponse = client.newCall(listRequest).execute()
            val code = listResponse.code
            val listBody = listResponse.body?.string() ?: ""

            if (!listResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch chats: HTTP $code"))
            }

            val json = JSONObject(listBody)
            val items = json.optJSONArray("items")
            if (items == null || items.length() == 0) {
                return@withContext Result.success("No chats to delete")
            }

            val target = items.getJSONObject(0)
            val convId = target.getString("id")
            val title = target.optString("title", "Untitled Chat")

            // 2. Send official delete command
            val deletePayload = JSONObject().apply {
                put("is_visible", false)
            }.toString()

            val patchRequest = Request.Builder()
                .url("https://chatgpt.com/backend-api/conversation/$convId")
                .header("Authorization", formatBearer(token))
                .header("Content-Type", "application/json")
                .patch(deletePayload.toRequestBody("application/json".toMediaType()))
                .build()

            val patchResponse = client.newCall(patchRequest).execute()
            if (patchResponse.isSuccessful) {
                Log.d(TAG, "Successfully deleted conversation '$title' ($convId)")
                return@withContext Result.success(title)
            } else {
                return@withContext Result.failure(Exception("Delete failed: HTTP ${patchResponse.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteLastChat error: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }
}
