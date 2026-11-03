package com.quickchatgptcloser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

object ClaudeManager {

    private const val TAG = "ClaudeManager"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun formatCookie(token: String): String {
        val trimmed = token.trim()
        return if (trimmed.startsWith("sessionKey=", ignoreCase = true)) {
            trimmed
        } else {
            "sessionKey=$trimmed"
        }
    }

    private fun formatBearer(token: String): String {
        val trimmed = token.trim()
        return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
            trimmed
        } else {
            "Bearer $trimmed"
        }
    }

    private fun applyAuthHeaders(builder: Request.Builder, token: String) {
        val trimmed = token.trim()

        // Standard browser & CORS spoofing headers
        builder.header("User-Agent", USER_AGENT)
        builder.header("Accept", "application/json, text/plain, */*")
        builder.header("Accept-Language", "en-US,en;q=0.9")
        builder.header("Referer", "https://claude.ai/")
        builder.header("Origin", "https://claude.ai")
        builder.header("Sec-Fetch-Dest", "empty")
        builder.header("Sec-Fetch-Mode", "cors")
        builder.header("Sec-Fetch-Site", "same-origin")

        // 1. Cookie Header (for sessionKey)
        if (trimmed.contains("sessionKey") || trimmed.startsWith("sk-ant-sid")) {
            builder.header("Cookie", formatCookie(trimmed))
        }

        // 2. Authorization Header (for Bearer or session token)
        builder.header("Authorization", formatBearer(trimmed))

        // 3. API key header (if standard Anthropic API key is provided)
        if (trimmed.startsWith("sk-ant-api")) {
            builder.header("x-api-key", trimmed)
            builder.header("anthropic-version", "2023-06-01")
        }
    }

    private fun getFirstOrgId(token: String): String? {
        val reqBuilder = Request.Builder()
            .url("https://claude.ai/api/organizations")
            .get()
        applyAuthHeaders(reqBuilder, token)

        val response = client.newCall(reqBuilder.build()).execute()
        val body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to get Claude organizations: HTTP ${response.code} $body")
            return null
        }

        val array = JSONArray(body)
        if (array.length() > 0) {
            return array.getJSONObject(0).optString("uuid")
        }
        return null
    }

    suspend fun testConnection(token: String): Result<String> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(Exception("Claude token cannot be empty"))
        }

        try {
            val orgId = getFirstOrgId(token)
                ?: return@withContext Result.failure(Exception("Could not connect to Claude: Invalid token or no organization found. Ensure you copied your sessionKey or Bearer token from claude.ai."))

            val reqBuilder = Request.Builder()
                .url("https://claude.ai/api/organizations/$orgId/chat_conversations?limit=1")
                .get()
            applyAuthHeaders(reqBuilder, token)

            val response = client.newCall(reqBuilder.build()).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            if (code == 401 || code == 403) {
                return@withContext Result.failure(Exception("HTTP $code: Invalid or expired Claude token."))
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

    /**
     * Deletes the most recent conversations (1 for single chat, or multiple for full history wipe).
     */
    suspend fun deleteChats(token: String, count: Int = 1): Result<Int> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(Exception("No Claude token configured"))
        }

        try {
            val orgId = getFirstOrgId(token)
                ?: return@withContext Result.failure(Exception("Could not retrieve Claude organization"))

            val fetchCount = count.coerceIn(1, 15)
            val listBuilder = Request.Builder()
                .url("https://claude.ai/api/organizations/$orgId/chat_conversations?limit=$fetchCount")
                .get()
            applyAuthHeaders(listBuilder, token)

            val listResponse = client.newCall(listBuilder.build()).execute()
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

                // Official Claude DELETE endpoint - no body required
                val delBuilder = Request.Builder()
                    .url("https://claude.ai/api/organizations/$orgId/chat_conversations/$convUuid")
                    .delete()
                applyAuthHeaders(delBuilder, token)

                val deleteResponse = client.newCall(delBuilder.build()).execute()
                if (deleteResponse.isSuccessful || deleteResponse.code == 200 || deleteResponse.code == 204) {
                    deletedCount++
                    Log.d(TAG, "Purged Claude conversation #$i: '$title' ($convUuid)")
                } else {
                    Log.w(TAG, "Failed deleting Claude conversation #$i ($convUuid): HTTP ${deleteResponse.code}")
                }
            }

            return@withContext Result.success(deletedCount)
        } catch (e: Exception) {
            Log.e(TAG, "deleteChats error: ${e.message}", e)
            return@withContext Result.failure(e)
        }
    }
}
