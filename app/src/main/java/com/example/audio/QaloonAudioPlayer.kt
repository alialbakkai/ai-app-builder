package com.example.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.domain.model.QaloonReciter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Streams authentic Riwayat Qaloon 'an Nafi' reference recitations (e.g., Sheikh Ali Al-Hudhaify)
 * without storing audio on disk.
 */
class QaloonAudioPlayer {

    companion object {
        private const val TAG = "QaloonAudioPlayer"
    }

    sealed interface PlaybackState {
        data object Idle : PlaybackState
        data class Loading(val surahNo: Int, val reciterName: String) : PlaybackState
        data class Playing(val surahNo: Int, val reciterName: String) : PlaybackState
        data class Error(val message: String) : PlaybackState
    }

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    fun playSurah(surahNo: Int, reciter: QaloonReciter) {
        stop()
        val url = reciter.buildSurahAudioUrl(surahNo)
        _playbackState.value = PlaybackState.Loading(surahNo, reciter.nameAr)

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { mp ->
                    mp.start()
                    _playbackState.value = PlaybackState.Playing(surahNo, reciter.nameAr)
                }
                setOnCompletionListener {
                    _playbackState.value = PlaybackState.Idle
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    _playbackState.value =
                        PlaybackState.Error("تعذر تشغيل التلاوة المرجعية، تحقق من الاتصال بالإنترنت.")
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stream Qaloon audio", e)
            _playbackState.value = PlaybackState.Error("تعذر تشغيل التلاوة المرجعية.")
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _playbackState.value = PlaybackState.Idle
    }
}
