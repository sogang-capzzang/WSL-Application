import android.content.Context
import android.media.*
import android.os.Build
import android.util.Log
import java.io.ByteArrayOutputStream

class AudioUtils(private val context: Context) {
    private var audioTrack: AudioTrack? = null
    private var focusRequest: AudioFocusRequest? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val initialBuffer = ByteArrayOutputStream()
    private val INITIAL_BUFFER_SIZE = 8192 * 2

    fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audioManager.requestAudioFocus(focusRequest!!)
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
            focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                focusRequest = null
            }
        } else {
            audioManager.abandonAudioFocus(null)
        }
    }

    fun playPcmDataStream(pcmData: ByteArray, size: Int, isFirst: Boolean, timestamp: Long? = null) {
        if (size == 0) {
            finalizePlayback()
            return
        }

        try {
            val sampleRate = 24000
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            // Collect data in buffer
            initialBuffer.write(pcmData, 0, size)
            Log.d("AudioUtils", "Buffered: $size bytes, total: ${initialBuffer.size()} bytes")

            if (initialBuffer.size() < INITIAL_BUFFER_SIZE && !isFirst) {
                return
            }

            if (audioTrack == null || isFirst) {
                audioTrack?.release()
                audioTrack = null

                val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = minBufferSize * 8

                audioTrack = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
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
                requestAudioFocus()
                audioTrack?.play()
                Log.d("AudioUtils", "AudioTrack initialized: sampleRate=$sampleRate")
            }

            if (initialBuffer.size() > 0) {
                val bufferedData = initialBuffer.toByteArray()
                audioTrack?.write(bufferedData, 0, bufferedData.size, AudioTrack.WRITE_BLOCKING)
                Log.d("AudioUtils", "PCM data written: ${bufferedData.size} bytes")
                initialBuffer.reset()
            }

        } catch (e: Exception) {
            Log.e("AudioUtils", "Error playing PCM: ${e.message}", e)
            finalizePlayback()
        }
    }

    private fun finalizePlayback() {
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.flush() // 버퍼에 남은 것 재생
                    Thread.sleep(50) // 살짝 기다리기
                    track.stop()
                }
                track.release()
            }
        } catch (e: Exception) {
            Log.e("AudioUtils", "Error finalizing playback: ${e.message}", e)
        } finally {
            audioTrack = null
            initialBuffer.reset()
            abandonAudioFocus()
            Log.d("AudioUtils", "Playback finalized and resources released")
        }
    }

    fun release() {
        finalizePlayback()
    }
}
