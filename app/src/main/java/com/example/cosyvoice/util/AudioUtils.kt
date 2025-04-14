import android.content.Context
import android.media.*
import android.os.Build
import android.util.Log

class AudioUtils(private val context: Context) {
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
        if (size == 0) {
            audioTrack?.flush()
            audioTrack?.stop()
            abandonAudioFocus()
            Log.d("AudioUtils", "Stream ended, AudioTrack stopped")
            return
        }

        try {
            val sampleRate = 22050
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
                requestAudioFocus()
                audioTrack?.play()
                isPlaying = true
                Log.d("AudioUtils", "AudioTrack initialized: sampleRate=$sampleRate")
            }

            audioTrack?.write(pcmData, 0, size, AudioTrack.WRITE_BLOCKING)
            Log.d("AudioUtils", "PCM data written: $size bytes")
        } catch (e: Exception) {
            Log.e("AudioUtils", "Error playing PCM: ${e.message}", e)
            abandonAudioFocus()
        }
    }

    fun release() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        isPlaying = false
        abandonAudioFocus()
        Log.d("AudioUtils", "AudioTrack released")
    }
}