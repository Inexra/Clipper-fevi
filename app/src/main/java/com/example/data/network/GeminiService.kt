package com.example.data.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getEffectiveApiKey(customKey: String?): String {
        return if (!customKey.isNullOrBlank()) {
            customKey.trim()
        } else {
            try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Exception) {
                ""
            }
        }
    }

    suspend fun generateContent(prompt: String, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No Gemini API key provided. Please configure your key in Settings or Secrets panel."))
        }

        try {
            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.7)
                    put("topP", 0.95)
                }
                put("generationConfig", generationConfig)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val url = "$BASE_URL?key=$apiKey"

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(httpRequest).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseString)
                    errJson.optJSONObject("error")?.optString("message") ?: "HTTP ${response.code}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseString"
                }
                return@withContext Result.failure(Exception("Gemini API Error: $errorMsg"))
            }

            val responseJson = JSONObject(responseString)
            val candidates = responseJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text")
                    return@withContext Result.success(text)
                }
            }

            Result.failure(Exception("No content returned in response"))
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini", e)
            Result.failure(e)
        }
    }

    suspend fun testApiKey(apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val result = generateContent("Respond with the single word: OK", apiKey)
        if (result.isSuccess) {
            Result.success(true)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("API validation failed"))
        }
    }
}
