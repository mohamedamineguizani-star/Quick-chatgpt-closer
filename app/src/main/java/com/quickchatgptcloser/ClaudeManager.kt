package com.quickchatgptcloser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ClaudeManager {

    private const val TAG = "ClaudeManager"
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private fun formatCookie(token: String): String {
        val trimmed = token.trim()
        return if (trimmed.startsWith("sessionKey=", ignoreCase = true)) {
            trimmed
        } else {
            "sessionKey=$trimmed"
        }
    }

    private fun getFirstOrgId(token: String): String? {
        val request = Request.Builder()
            .url("https://claude.ai/api/organizations")
            .header("Cookie", formatCookie(token))
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Accept", "application/json")
            .get()
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to get orgs: HTTP ${response.code} $body")
            return null
        }

        val array = JSONArray(body)
        if (array.length() > 0) {
            val firstOrg = array.getJSONObject(0)
            return firstOrg.optString("uuid")
        }
        return null
    }

    suspend fun testConnection(token: String): Result<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(Exception("Claude sessionKey cannot be empty"))
        }

        try {
            val orgId = getFirstOrgId(token)
                ?: return@withContext Result.failure(Exception("Invalid sessionKey or no Claude organization found. Copy fresh sessionKey cookie from claude.ai."))

            val request = Request.Builder()
                .url("https://claude.ai/api/organizations/$orgId/chat_conversations")
                .header("Cookie", formatCookie(token))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            if (code == 401 || code == 403) {
                return@withContext Result.failure(Exception("HTTP $code: Invalid or expired Claude sessionKey."))
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP $code: ${response.message}"))
            }

            val array = JSONArray(body)
            if (array.length() > 0) {
                val firstChat = array.getJSONObject(0)
                val title = firstChat.optString("name", firstChat.optString("summary", "Untitled Claude Chat"))
                return@withContext Result.success(title)
            } else {
                return@withContext Result.success("(No conversations found in Claude account)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Claude connection test failed: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }

    suspend fun deleteChats(token: String, count: Int = 1): Result<Int> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(Exception("No Claude sessionKey configured"))
        }

        try {
            val orgId = getFirstOrgId(token)
                ?: return@withContext Result.failure(Exception("Could not retrieve Claude organization"))

            val listRequest = Request.Builder()
                .url("https://claude.ai/api/organizations/$orgId/chat_conversations")
                .header("Cookie", formatCookie(token))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json")
                .get()
                .build()

            val listResponse = client.newCall(listRequest).execute()
            val listBody = listResponse.body?.string() ?: ""

            if (!listResponse.isSuccessful) {
                return@withContext Result.failure(Exception("Claude fetch failed: HTTP ${listResponse.code}"))
            }

            val array = JSONArray(listBody)
            if (array.length() == 0) {
                return@withContext Result.success(0)
            }

            var deletedCount = 0
            val limit = count.coerceIn(1, array.length())

            for (i in 0 until limit) {
                val target = array.getJSONObject(i)
                val convUuid = target.getString("uuid")
                val title = target.optString("name", "Untitled")

                val deleteRequest = Request.Builder()
                    .url("https://claude.ai/api/organizations/$orgId/chat_conversations/$convUuid")
                    .header("Cookie", formatCookie(token))
                    .header("Content-Type", "application/json")
                    .delete("{}".toRequestBody("application/json".toMediaType()))
                    .build()

                val deleteResponse = client.newCall(deleteRequest).execute()
                if (deleteResponse.isSuccessful) {
                    deletedCount++
                    Log.d(TAG, "Deleted Claude conversation #$i: '$title' ($convUuid)")
                }
            }

            return@withContext Result.success(deletedCount)
        } catch (e: Exception) {
            Log.e(TAG, "deleteChats error: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }
}
