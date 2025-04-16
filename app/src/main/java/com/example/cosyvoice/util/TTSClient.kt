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

            response.use { res ->

                if (!res.isSuccessful) {

                    Log.e("TTSClient", "TTS request failed: ${res.code}")

                    launch(Dispatchers.Main) { onPcmReceived(ByteArray(0), 0, false, null) }

                    return@coroutineScope

                }



                var isFirst = true

                var firstResponseTimestamp: Long? = null

                val buffer = ByteArray(8192)



                withContext(Dispatchers.IO) {

                    res.body?.byteStream()?.use { inputStream ->

                        var bytesRead: Int

                        while (true) {

                            bytesRead = try {

                                inputStream.read(buffer)

                            } catch (e: IOException) {

                                Log.e("TTSClient", "Stream read error: ${e.message}")

                                -1

                            }

                            if (bytesRead == -1) break

                            if (bytesRead > 0) {

                                if (isFirst) {

                                    firstResponseTimestamp = System.currentTimeMillis()

                                }

                                val pcmData = buffer.copyOf(bytesRead) // 버퍼 복사

                                launch(Dispatchers.Main) {

                                    onPcmReceived(pcmData, bytesRead, isFirst, if (isFirst) firstResponseTimestamp else null)

                                }

                                isFirst = false

                            }

                        }

                        launch(Dispatchers.Main) {

                            onPcmReceived(ByteArray(0), 0, false, null)

                        }

                    } ?: run {

                        Log.e("TTSClient", "Response body is null")

                        launch(Dispatchers.Main) { onPcmReceived(ByteArray(0), 0, false, null) }

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

            response?.close() // 명시적 닫기

        }

    }

}