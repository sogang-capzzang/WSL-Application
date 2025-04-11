package com.example.cosyvoice.util

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import java.io.*
import java.util.concurrent.TimeUnit

class TTSClient(private val serverUrl: String = "http://123.45.67.89:54321/inference_zero_shot") {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun requestTTS(
        inputText: String,
        person: String,
        onPcmReceived: (ByteArray?, Boolean, Long?) -> Unit
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

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("TTSClient", "TTS request failed: ${response.code}, ${response.message}")
                    launch(Dispatchers.Main) {
                        onPcmReceived(null, false, null)
                    }
                    return@coroutineScope
                }

                var isFirst = true
                var currentPcmData = ByteArrayOutputStream()
                var firstResponseTimestamp: Long? = null // 첫 번째 응답 시점

                response.body?.byteStream()?.use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (bytesRead > 0) {
                            currentPcmData.write(buffer, 0, bytesRead)
                            val pcmData = currentPcmData.toByteArray()
                            currentPcmData.reset() // 다음 청크를 위해 버퍼 초기화

                            if (pcmData.isNotEmpty()) {
                                Log.d("TTSClient", "Received PCM data: ${pcmData.size} bytes")

                                if (isFirst) {
                                    firstResponseTimestamp = System.currentTimeMillis()
                                    Log.d("TTSClient", "First PCM response timestamp: $firstResponseTimestamp")
                                }

                                launch(Dispatchers.Main) {
                                    onPcmReceived(pcmData, isFirst, if (isFirst) firstResponseTimestamp else null)
                                }
                                isFirst = false
                            }
                        }
                    }

                    // 스트리밍 종료 시 마지막 데이터 처리
                    val lastPcmData = currentPcmData.toByteArray()
                    if (lastPcmData.isNotEmpty()) {
                        Log.d("TTSClient", "Received last PCM data: ${lastPcmData.size} bytes")
                        launch(Dispatchers.Main) {
                            onPcmReceived(lastPcmData, isFirst, null)
                        }
                    }

                    launch(Dispatchers.Main) {
                        onPcmReceived(null, false, null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TTSClient", "Error requesting TTS: ${e.message}", e)
            launch(Dispatchers.Main) {
                onPcmReceived(null, false, null)
            }
        }
    }
}