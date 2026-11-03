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
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
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
                return@withContext Result.failure(Exception("HTTP $code: Invalid or expired token. Open chatgpt.com/api/auth/session to copy fresh accessToken."))
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
                return@withContext Result.success("(No conversations found in account)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Deletes the most recent conversations (1 for single chat, or multiple for full history wipe).
     */
    suspend fun deleteChats(token: String, count: Int = 1): Result<Int> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(Exception("No token configured"))
        }

        try {
            val fetchCount = count.coerceIn(1, 15)
            val listRequest = Request.Builder()
                .url("https://chatgpt.com/backend-api/conversations?offset=0&limit=$fetchCount")
                .header("Authorization", formatBearer(token))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .get()
                .build()

            val listResponse = client.newCall(listRequest).execute()
            val listBody = listResponse.body?.string() ?: ""

            if (!listResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Fetch failed: HTTP ${listResponse.code}"))
            }

            val json = JSONObject(listBody)
            val items = json.optJSONArray("items")
            if (items == null || items.length() == 0) {
                return@withContext Result.success(0)
            }

            var deletedCount = 0
            val deletePayload = JSONObject().apply {
                put("is_visible", false)
            }.toString()

            for (i in 0 until items.length()) {
                val target = items.getJSONObject(i)
                val convId = target.getString("id")
                val title = target.optString("title", "Untitled")

                val patchRequest = Request.Builder()
                    .url("https://chatgpt.com/backend-api/conversation/$convId")
                    .header("Authorization", formatBearer(token))
                    .header("Content-Type", "application/json")
                    .patch(deletePayload.toRequestBody("application/json".toMediaType()))
                    .build()

                val patchResponse = client.newCall(patchRequest).execute()
                if (patchResponse.isSuccessful) {
                    deletedCount++
                    Log.d(TAG, "Deleted conversation #$i: '$title' ($convId)")
                }
            }

            return@withContext Result.success(deletedCount)
        } catch (e: Exception) {
            Log.e(TAG, "deleteChats error: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }
}
