package com.example.agents

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.domain.model.AsrHypothesis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Agent 02 — Audio Intelligence & Agent 03 — Speech Recognition
 * Combines real-time Android SpeechRecognizer streaming hypotheses with an in-memory
 * AudioRecord VAD & short WAV snippet fallback (never persisted to disk).
 */
class AudioAndSpeechController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioSpeechController"
        private const val SAMPLE_RATE = 16000
        private const val VAD_RMS_THRESHOLD = 420.0
        private const val MAX_SNIPPET_MS = 5500L
        private const val SILENCE_END_MS = 1200L
    }

    enum class EngineMode {
        DEVICE_SPEECH_RECOGNIZER,
        HYBRID_AUDIO_SNIPPET
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var audioRecordJob: Job? = null
    private var isListeningActive = false
    private var sessionStartMs = 0L

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _isSpeechDetected = MutableStateFlow(false)
    val isSpeechDetected: StateFlow<Boolean> = _isSpeechDetected.asStateFlow()

    private val _engineMode = MutableStateFlow(EngineMode.DEVICE_SPEECH_RECOGNIZER)
    val engineMode: StateFlow<EngineMode> = _engineMode.asStateFlow()

    private val _diagnosticMessage = MutableStateFlow<String?>(null)
    val diagnosticMessage: StateFlow<String?> = _diagnosticMessage.asStateFlow()

    private val _hypotheses = MutableSharedFlow<AsrHypothesis>(extraBufferCapacity = 32)
    val hypotheses: SharedFlow<AsrHypothesis> = _hypotheses.asSharedFlow()

    private val _audioSnippets = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
    val audioSnippets: SharedFlow<ByteArray> = _audioSnippets.asSharedFlow()

    fun isDeviceSpeechAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(preferCloudAudioMode: Boolean = false) {
        if (isListeningActive) return
        isListeningActive = true
        sessionStartMs = System.currentTimeMillis()
        _diagnosticMessage.value = null

        val useDeviceAsr = !preferCloudAudioMode && isDeviceSpeechAvailable()
        if (useDeviceAsr) {
            _engineMode.value = EngineMode.DEVICE_SPEECH_RECOGNIZER
            startDeviceSpeechRecognizer()
        } else {
            _engineMode.value = EngineMode.HYBRID_AUDIO_SNIPPET
            startInMemoryAudioVadLoop()
        }
    }

    fun stopListening() {
        isListeningActive = false
        _isSpeechDetected.value = false
        _rmsLevel.value = 0f
        audioRecordJob?.cancel()
        audioRecordJob = null
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying SpeechRecognizer", e)
            } finally {
                speechRecognizer = null
            }
        }
    }

    private fun startDeviceSpeechRecognizer() {
        mainHandler.post {
            if (!isListeningActive) return@post
            try {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _diagnosticMessage.value = null
                    }

                    override fun onBeginningOfSpeech() {
                        _isSpeechDetected.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _rmsLevel.value = normalized
                        _isSpeechDetected.value = rmsdB > 1.5f
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _isSpeechDetected.value = false
                    }

                    override fun onError(error: Int) {
                        if (!isListeningActive) return
                        Log.w(TAG, "SpeechRecognizer error code=$error")
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                // Long pause / silence is tolerated, restart cleanly
                                scheduleRestartRecognizer()
                            }
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                scheduleRestartRecognizer(400L)
                            }
                            else -> {
                                // Fallback seamlessly to in-memory VAD audio snippet mode
                                _diagnosticMessage.value =
                                    "تم التحويل إلى نمط الميكروفون الهجين للتحقق الصوتي"
                                _engineMode.value = EngineMode.HYBRID_AUDIO_SNIPPET
                                speechRecognizer?.destroy()
                                speechRecognizer = null
                                startInMemoryAudioVadLoop()
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        emitBundleResults(results, isFinal = true)
                        if (isListeningActive) scheduleRestartRecognizer(120L)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        emitBundleResults(partialResults, isFinal = false)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
                    putExtra(
                        RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES,
                        arrayListOf("ar-LY", "ar-TN", "ar-SA", "ar")
                    )
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer, falling back to AudioRecord", e)
                _engineMode.value = EngineMode.HYBRID_AUDIO_SNIPPET
                startInMemoryAudioVadLoop()
            }
        }
    }

    private fun scheduleRestartRecognizer(delayMs: Long = 180L) {
        scope.launch {
            delay(delayMs)
            if (isListeningActive && _engineMode.value == EngineMode.DEVICE_SPEECH_RECOGNIZER) {
                startDeviceSpeechRecognizer()
            }
        }
    }

    private fun emitBundleResults(bundle: Bundle?, isFinal: Boolean) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        val transcript = matches.firstOrNull()?.trim().orEmpty()
        if (transcript.isBlank()) return
        val scores = bundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val conf = scores?.firstOrNull()?.takeIf { it in 0f..1f } ?: if (isFinal) 0.78f else 0.62f
        val nowOffset = (System.currentTimeMillis() - sessionStartMs).coerceAtLeast(0L)
        val normalized = QaloonNormalizer.normalizePhrase(transcript)
        if (normalized.isNotEmpty()) {
            _hypotheses.tryEmit(
                AsrHypothesis(
                    rawTranscript = transcript,
                    normalizedWords = normalized,
                    confidence = conf,
                    isFinal = isFinal,
                    audioStartMs = (nowOffset - 1500L).coerceAtLeast(0L),
                    audioEndMs = nowOffset,
                    source = "android_speech_recognizer"
                )
            )
        }
    }

    /**
     * Real-time in-memory PCM capture with energy-based VAD.
     * Buffers only active speech phrases (< 5.5s) and discards immediately after emission.
     * Zero audio files are ever written to disk.
     */
    @SuppressLint("MissingPermission")
    private fun startInMemoryAudioVadLoop() {
        audioRecordJob?.cancel()
        audioRecordJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    _diagnosticMessage.value = "تعذر تهيئة الميكروفون على هذا الجهاز"
                    return@launch
                }

                recorder.startRecording()
                val frameBuffer = ShortArray(minBuf / 2)
                val pcmOut = ByteArrayOutputStream()
                var speechActive = false
                var speechStartTime = 0L
                var lastVoiceTime = 0L

                while (isActive && isListeningActive) {
                    val readCount = recorder.read(frameBuffer, 0, frameBuffer.size)
                    if (readCount <= 0) continue

                    var sumSq = 0.0
                    for (i in 0 until readCount) {
                        val s = frameBuffer[i].toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / readCount)
                    _rmsLevel.value = (rms / 4000.0).toFloat().coerceIn(0f, 1f)

                    val now = System.currentTimeMillis()
                    if (rms > VAD_RMS_THRESHOLD) {
                        if (!speechActive) {
                            speechActive = true
                            speechStartTime = now
                            pcmOut.reset()
                        }
                        lastVoiceTime = now
                        _isSpeechDetected.value = true
                        writeShortsLittleEndian(pcmOut, frameBuffer, readCount)
                    } else if (speechActive) {
                        // Keep trailing silence brief for clean word ending
                        writeShortsLittleEndian(pcmOut, frameBuffer, readCount)
                        val silenceDuration = now - lastVoiceTime
                        val totalDuration = now - speechStartTime
                        if (silenceDuration >= SILENCE_END_MS || totalDuration >= MAX_SNIPPET_MS) {
                            _isSpeechDetected.value = false
                            speechActive = false
                            val pcmBytes = pcmOut.toByteArray()
                            pcmOut.reset()
                            if (pcmBytes.size >= SAMPLE_RATE / 2) { // >= 0.25s of speech
                                _audioSnippets.tryEmit(wrapPcmAsWav(pcmBytes, SAMPLE_RATE))
                            }
                        }
                    } else {
                        _isSpeechDetected.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord VAD error", e)
                _diagnosticMessage.value = "تعذر القراءة من الميكروفون: ${e.localizedMessage ?: ""}"
            } finally {
                try {
                    recorder?.stop()
                } catch (_: Exception) {
                }
                recorder?.release()
            }
        }
    }

    private fun writeShortsLittleEndian(out: ByteArrayOutputStream, data: ShortArray, count: Int) {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val v = data[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v ushr 8) and 0xFF).toByte()
        }
        out.write(bytes)
    }

    private fun wrapPcmAsWav(pcmBytes: ByteArray, sampleRate: Int): ByteArray {
        val totalDataLen = pcmBytes.size + 36
        val byteRate = sampleRate * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalDataLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1.toShort()) // PCM
            putShort(1.toShort()) // Mono
            putInt(sampleRate)
            putInt(byteRate)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray())
            putInt(pcmBytes.size)
        }.array()
        return header + pcmBytes
    }
}
