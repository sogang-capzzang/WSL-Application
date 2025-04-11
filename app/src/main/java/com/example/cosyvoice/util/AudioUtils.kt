package com.example.cosyvoice.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

class AudioUtils(private val context: Context) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var totalSamplesWritten = 0L // 쓰여진 총 샘플 수 (16-bit 샘플 기준)

    // PCM 데이터를 재생 (16-bit PCM, Mono, 22.05kHz)
    fun playPcmDataStream(pcmData: ByteArray, isFirst: Boolean) {
        try {
            // PCM 데이터 형식 고정 (CosyVoice2의 target_sr에 맞춤)
            val sampleRate = 22050 // CosyVoice2의 target_sr
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            // AudioTrack 초기화 (첫 데이터에서만)
            if (isFirst || audioTrack == null) {
                // 이전 AudioTrack이 있으면 해제
                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = null

                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    channelConfig,
                    audioFormat
                ) * 4 // 버퍼 크기 4배로 증가 (딜레이 대비)

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
                audioTrack?.play()
                isPlaying = true
                totalSamplesWritten = 0L
                Log.d("AudioUtils", "AudioTrack 초기화 및 재생 시작: sampleRate=$sampleRate, channels=1, bitsPerSample=16")
            } else {
                // 이전 데이터 재생이 끝날 때까지 대기
                while (isPlaying && audioTrack != null) {
                    val playbackPosition = audioTrack!!.playbackHeadPosition.toLong()
                    if (playbackPosition >= totalSamplesWritten) {
                        break // 이전 데이터 재생이 끝남
                    }
                    Thread.sleep(10) // 10ms 대기 후 재확인
                }
                Log.d("AudioUtils", "이전 데이터 재생 완료, 다음 데이터 재생 시작")
            }

            // PCM 데이터 끝의 침묵 제거
            var lastNonZeroIndex = pcmData.size - 1
            while (lastNonZeroIndex >= 0 && pcmData[lastNonZeroIndex] == 0.toByte()) {
                lastNonZeroIndex--
            }
            val trimmedPcmData = if (lastNonZeroIndex >= 0) {
                pcmData.copyOfRange(0, lastNonZeroIndex + 1)
            } else {
                pcmData
            }

            // AudioTrack에 데이터 쓰기
            var offset = 0
            val bufferSize = 8192 // 8KB 버퍼
            while (offset < trimmedPcmData.size) {
                val bytesToWrite = minOf(bufferSize, trimmedPcmData.size - offset)
                audioTrack?.write(trimmedPcmData, offset, bytesToWrite, AudioTrack.WRITE_BLOCKING)
                offset += bytesToWrite
            }

            // 쓰여진 샘플 수 업데이트 (16-bit 샘플 기준, Mono이므로 2바이트당 1샘플)
            totalSamplesWritten += (trimmedPcmData.size / 2).toLong()
            Log.d("AudioUtils", "PCM 데이터 재생 완료, 총 샘플 수: $totalSamplesWritten")
        } catch (e: Exception) {
            Log.e("AudioUtils", "PCM 데이터 재생 중 오류: ${e.message}", e)
        }
    }

    fun release() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        isPlaying = false
        totalSamplesWritten = 0L
        Log.d("AudioUtils", "AudioTrack 리소스 해제")
    }
}