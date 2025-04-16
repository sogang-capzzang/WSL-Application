package com.example.cosyvoice.util

import android.content.Context
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class AudioUtils(private val context: Context) {
    private var audioTrack: AudioTrack? = null
    private val isPlaying = AtomicBoolean(false)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioThread = HandlerThread("AudioThread").apply { start() }
    private val audioHandler = Handler(audioThread.looper)

    fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.abandonAudioFocusRequest(AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build())
        } else {
            audioManager.abandonAudioFocus(null)
        }
    }

    fun playPcmDataStream(pcmData: ByteArray, size: Int, isFirst: Boolean, timestamp: Long?) {
        audioHandler.post {
            if (size == 0) {
                audioTrack?.flush()
                audioTrack?.stop()
                abandonAudioFocus()
                isPlaying.set(false)
                Log.d("AudioUtils", "Stream ended, AudioTrack stopped")
                return@post
            }

            try {
                val sampleRate = 24000 // 무음 지속 시 테스트: val sampleRate = 16000 또는 44100
                val channelConfig = AudioFormat.CHANNEL_OUT_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT

                if (isFirst || audioTrack == null) {
                    audioTrack?.stop()
                    audioTrack?.release()
                    audioTrack = null

                    val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                    val bufferSize = minBufferSize * 8
                    audioTrack = AudioTrack(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(audioFormat)
                            .setChannelMask(channelConfig)
                            .build(),
                        bufferSize,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                    )

                    if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                        Log.e("AudioUtils", "AudioTrack 초기화 실패: state=${audioTrack?.state}")
                        audioTrack?.release()
                        audioTrack = null
                        return@post
                    }

                    requestAudioFocus()
                    audioTrack?.play()
                    // 재생 상태 확인
                    repeat(5) {
                        if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) return@repeat
                        Thread.sleep(50) // 50ms 대기
                    }
                    if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        Log.e("AudioUtils", "AudioTrack 재생 시작 실패: playState=${audioTrack?.playState}")
                        audioTrack?.release()
                        audioTrack = null
                        return@post
                    }
                    isPlaying.set(true)
                    Log.d("AudioUtils", "AudioTrack initialized: sampleRate=$sampleRate, bufferSize=$bufferSize")
                }

                // 데이터 쓰기
                var written = audioTrack?.write(pcmData, 0, size, AudioTrack.WRITE_BLOCKING)
                if (written == size) {
                    Log.d("AudioUtils", "PCM data written: $size bytes")
                } else {
                    Log.w("AudioUtils", "데이터 쓰기 불완전: written=$written, expected=$size, 재시도")
                    // 재시도
                    written = audioTrack?.write(pcmData, 0, size, AudioTrack.WRITE_BLOCKING)
                    if (written != size) {
                        Log.e("AudioUtils", "데이터 쓰기 실패: written=$written, expected=$size")
                        audioTrack?.stop()
                        audioTrack?.release()
                        audioTrack = null
                        isPlaying.set(false)
                        return@post
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioUtils", "Error playing PCM: ${e.message ?: "Unknown error"}", e)
                abandonAudioFocus()
                isPlaying.set(false)
                audioTrack?.release()
                audioTrack = null
            }
        }
    }

    fun release() {
        audioHandler.post {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            isPlaying.set(false)
            abandonAudioFocus()
            Log.d("AudioUtils", "AudioTrack released")
        }
        audioHandler.removeCallbacksAndMessages(null)
        audioThread.quitSafely()
    }
}