package com.example.cosyvoice.util

import android.util.Log
import com.example.cosyvoice.BuildConfig
import kotlinx.coroutines.*
import okhttp3.*
import java.io.*
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class TTSClient {
    private val serverUrl = BuildConfig.COSYVOICE2_SERVER_URL
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private var currentCall: Call? = null

    suspend fun requestTTS(
        inputText: String,
        person: String,
        onPcmReceived: (ByteArray, Int, Boolean, Long?) -> Unit
    ) = coroutineScope {
        try {
            val requestBody = FormBody.Builder()
                .add("tts_text", inputText)
                .add("person", person)
                .build()

            val request = Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build()

            currentCall = client.newCall(request)
            currentCall?.execute()?.use { response ->
                if (!response.isSuccessful) {
                    Log.e("TTSClient", "TTS request failed: ${response.code}")
                    launch(Dispatchers.Main) { onPcmReceived(ByteArray(0), 0, false, null) }
                    return@coroutineScope
                }

                var isFirst = true

                var firstResponseTimestamp: Long? = null
                val buffer = ByteArray(8192)

                response.body?.byteStream()?.use { inputStream ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (bytesRead > 0) {
                            if (isFirst) {
                                firstResponseTimestamp = System.currentTimeMillis()
                            }
                            // 덮어씌움 방지
                            withContext(Dispatchers.IO) {
                                onPcmReceived(buffer, bytesRead, isFirst, if (isFirst) firstResponseTimestamp else null)
                            }
                            isFirst = false
                        }
                    }
                    launch(Dispatchers.Main) {
                        onPcmReceived(ByteArray(0), 0, false, null)
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e("TTSClient", "Network timeout: ${e.message}", e)
            launch(Dispatchers.Main) { onPcmReceived(ByteArray(0), 0, false, null) }
        } catch (e: IOException) {
            Log.e("TTSClient", "Network error: ${e.message}", e)
            launch(Dispatchers.Main) { onPcmReceived(ByteArray(0), 0, false, null) }
        } catch (e: Exception) {
            Log.e("TTSClient", "Unexpected error: ${e.message}", e)
            launch(Dispatchers.Main) { onPcmReceived(ByteArray(0), 0, false, null) }
        } finally {
            currentCall = null
        }
    }
}