package com.example.cosyvoice.util

import android.util.Log
import com.example.cosyvoice.BuildConfig
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.math.min

class TTSClient {
    private val serverUrl = BuildConfig.COSYVOICE2_SERVER_URL
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun requestTTS(
        inputText: String,
        person: String,
        onPcmReceived: (ByteArray, Int, Boolean, Long?) -> Unit
    ) = coroutineScope {
        var response: Response? = null
        val chunkSize = 3200
        val tempBuffer = mutableListOf<ByteArray>()
        var tempBufferSize = 0
        var isFirst = true
        var firstResponseTimestamp: Long? = null

        try {
            val requestBody = FormBody.Builder()
                .add("tts_text", inputText)
                .add("person", person)
                .build()

            val request = Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build()

            response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                Log.e("TTSClient", "TTS request failed: ${response.code}")
                withContext(Dispatchers.IO) {
                    onPcmReceived(ByteArray(0), 0, false, null)
                }
                return@coroutineScope
            }

            val buffer = ByteArray(8192)
            withContext(Dispatchers.IO) {
                response.body?.byteStream()?.use { inputStream ->
                    var bytesRead: Int
                    while (true) {
                        bytesRead = try {
                            inputStream.read(buffer)
                        } catch (e: IOException) {
                            Log.e("TTSClient", "Stream read error: ${e.message ?: "Unknown IOException"}")
                            -1
                        }
                        if (bytesRead == -1) break
                        if (bytesRead > 0) {
                            if (isFirst) {
                                firstResponseTimestamp = System.currentTimeMillis()
                            }
                            tempBuffer.add(buffer.copyOf(bytesRead))
                            tempBufferSize += bytesRead
                            Log.d("TTSClient", "Received PCM: $bytesRead bytes, total: $tempBufferSize")

                            // 고정 크기 청크로 데이터 전송
                            while (tempBufferSize >= chunkSize) {
                                val chunk = ByteArray(chunkSize)
                                var filled = 0
                                while (filled < chunkSize && tempBuffer.isNotEmpty()) {
                                    val current = tempBuffer[0]
                                    val toCopy = min(chunkSize - filled, current.size)
                                    System.arraycopy(current, 0, chunk, filled, toCopy)
                                    filled += toCopy
                                    if (toCopy < current.size) {
                                        val remaining = current.copyOfRange(toCopy, current.size)
                                        tempBuffer[0] = remaining
                                    } else {
                                        tempBuffer.removeAt(0)
                                    }
                                    tempBufferSize -= toCopy
                                }
                                onPcmReceived(chunk, chunkSize, isFirst, if (isFirst) firstResponseTimestamp else null)
                                isFirst = false
                            }
                        }
                    }
                    if (tempBufferSize > 0) {
                        val finalChunk = ByteArray(tempBufferSize)
                        var filled = 0
                        while (tempBuffer.isNotEmpty()) {
                            val current = tempBuffer[0]
                            System.arraycopy(current, 0, finalChunk, filled, current.size)
                            filled += current.size
                            tempBuffer.removeAt(0)
                        }
                        onPcmReceived(finalChunk, tempBufferSize, isFirst, if (isFirst) firstResponseTimestamp else null)
                        tempBufferSize = 0
                    }
                    onPcmReceived(ByteArray(0), 0, false, null)
                } ?: run {
                    Log.e("TTSClient", "Response body is null")
                    onPcmReceived(ByteArray(0), 0, false, null)
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.e("TTSClient", "Network timeout: ${e.message ?: "No message"}", e)
            withContext(Dispatchers.IO) {
                onPcmReceived(ByteArray(0), 0, false, null)
            }
        } catch (e: IOException) {
            Log.e("TTSClient", "Network error: ${e.message ?: "Unknown IOException"}", e)
            withContext(Dispatchers.IO) {
                onPcmReceived(ByteArray(0), 0, false, null)
            }
        } catch (e: Exception) {
            Log.e("TTSClient", "Unexpected error: ${e.message ?: "Unknown error"}", e)
            withContext(Dispatchers.IO) {
                onPcmReceived(ByteArray(0), 0, false, null)
            }
        } finally {
            withContext(Dispatchers.IO) {
                response?.close()
            }
        }
    }
}