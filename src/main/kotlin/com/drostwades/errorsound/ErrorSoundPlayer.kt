package com.drostwades.errorsound

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.Toolkit
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.concurrent.thread
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl

object ErrorSoundPlayer {

    private const val SAMPLE_RATE = 44_100f
    private const val DEFAULT_TONE_HZ = 880.0
    private const val DEFAULT_TONE_SECONDS = 1.0
    private const val DEBOUNCE_MS = 250L  // last-resort guard; AlertEventGate owns real throttling

    private val log = Logger.getInstance(ErrorSoundPlayer::class.java)
    private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("ErrorSoundPlayer", 1)
    private val previewLock = Any()
    private var previewToken: Long = 0
    private var previewClip: Clip? = null
    private var previewThread: Thread? = null
    private val lastPlayTime = java.util.concurrent.atomic.AtomicLong(0L)

    fun play(settings: AlertSettings.State, errorKind: ErrorKind = ErrorKind.GENERIC) {
        val now = System.currentTimeMillis()
        val prev = lastPlayTime.get()
        if (now - prev < DEBOUNCE_MS) return
        if (!lastPlayTime.compareAndSet(prev, now)) return  // another caller won the race
        executor.submit {
            runCatching {
                if (!isErrorKindEnabled(settings, errorKind)) {
                    return@runCatching
                }
                when (settings.soundSource) {
                    AlertSettings.SoundSource.CUSTOM.name -> playCustom(settings)
                    else -> playBuiltIn(settings, errorKind)
                }
            }.onFailure {
                log.warn("Failed to play error sound", it)
                runCatching { Toolkit.getDefaultToolkit().beep() }
            }
        }
    }

    fun previewBuiltIn(soundId: String, volumePercent: Int, durationSeconds: Int) {
        val sound = BuiltInSounds.findByIdOrDefault(soundId)
        val bytes = readResourceBytes(sound.resourcePath) ?: return
        startPreview(bytes, volumePercent, durationSeconds)
    }

    fun previewCustom(customPath: String, volumePercent: Int, durationSeconds: Int) {
        val file = File(customPath)
        if (!file.exists() || !file.isFile) {
            return
        }
        startPreview(file.readBytes(), volumePercent, durationSeconds)
    }

    fun stopPreview() {
        synchronized(previewLock) {
            previewToken += 1
            stopPreviewLocked()
        }
    }

    private fun playCustom(settings: AlertSettings.State) {
        val customPath = settings.customSoundPath.trim()
        if (customPath.isEmpty()) {
            playGeneratedTone(settings)
            return
        }

        val file = File(customPath)
        if (!file.exists() || !file.isFile) {
            log.warn("Custom sound file not found: $customPath")
            playGeneratedTone(settings)
            return
        }

        val audioBytes = file.readBytes()
        playClipLooping(audioBytes, settings)
    }

    private fun playBuiltIn(settings: AlertSettings.State, errorKind: ErrorKind) {
        val selectedSoundId = resolveBuiltInSoundId(settings, errorKind)
        if (selectedSoundId == BuiltInSounds.CUSTOM_FILE_ID) {
            playCustom(settings)
            return
        }
        val selected = BuiltInSounds.findByIdOrDefault(selectedSoundId)
        val resourceBytes = readResourceBytes(selected.resourcePath)
        if (resourceBytes != null) {
            runCatching { playClipLooping(resourceBytes, settings) }
                .onSuccess { return }
                .onFailure { log.warn("Built-in sound failed, falling back to generated tone: ${selected.resourcePath}", it) }
        } else {
            log.warn("Built-in sound resource not found: ${selected.resourcePath}")
        }

        playGeneratedTone(settings)
    }

    private fun playGeneratedTone(settings: AlertSettings.State) {
        val wavBytes = generateToneWavBytes(
            durationSeconds = DEFAULT_TONE_SECONDS,
            frequencyHz = DEFAULT_TONE_HZ,
            sampleRate = SAMPLE_RATE,
        )

        playClipLooping(wavBytes, settings)
    }

    private fun readResourceBytes(resourcePath: String): ByteArray? {
        return javaClass.getResourceAsStream(resourcePath)?.use { input -> input.readBytes() }
    }

    private fun isErrorKindEnabled(settings: AlertSettings.State, errorKind: ErrorKind): Boolean {
        if (settings.useGlobalBuiltInSound) {
            return errorKind != ErrorKind.NONE
        }
        return when (errorKind) {
            ErrorKind.CONFIGURATION -> settings.configurationSoundEnabled
            ErrorKind.COMPILATION -> settings.compilationSoundEnabled
            ErrorKind.TEST_FAILURE -> settings.testFailureSoundEnabled
            ErrorKind.NETWORK -> settings.networkSoundEnabled
            ErrorKind.EXCEPTION -> settings.exceptionSoundEnabled
            ErrorKind.GENERIC -> settings.genericSoundEnabled
            ErrorKind.NONE -> false
        }
    }

    private fun resolveBuiltInSoundId(settings: AlertSettings.State, errorKind: ErrorKind): String {
        if (settings.useGlobalBuiltInSound) {
            return settings.builtInSoundId
        }

        return when (errorKind) {
            ErrorKind.CONFIGURATION -> settings.configurationSoundId
            ErrorKind.COMPILATION -> settings.compilationSoundId
            ErrorKind.TEST_FAILURE -> settings.testFailureSoundId
            ErrorKind.NETWORK -> settings.networkSoundId
            ErrorKind.EXCEPTION -> settings.exceptionSoundId
            ErrorKind.GENERIC -> settings.genericSoundId
            ErrorKind.NONE -> settings.genericSoundId
        }
    }

    private fun playClipLooping(audioBytes: ByteArray, settings: AlertSettings.State) {
        val input = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioBytes))
        val clip = AudioSystem.getClip()
        try {
            clip.open(input)
            applyVolumeIfSupported(clip, settings.volumePercent)

            val totalDurationMillis = (settings.alertDurationSeconds.coerceIn(1, 10) * 1_000L)
            val deadlineNanos = System.nanoTime() + (totalDurationMillis * 1_000_000L)
            val clipLengthMillis = (clip.microsecondLength / 1_000L).coerceAtLeast(1L)

            while (System.nanoTime() < deadlineNanos) {
                clip.framePosition = 0
                clip.start()

                val remainingMillis = ((deadlineNanos - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
                if (remainingMillis <= 0L) {
                    break
                }

                Thread.sleep(minOf(clipLengthMillis, remainingMillis))
                clip.stop()
                clip.flush()
            }
        } finally {
            runCatching { input.close() }
            runCatching { clip.close() }
        }
    }

    private fun applyVolumeIfSupported(clip: Clip, volumePercent: Int) {
        val bounded = volumePercent.coerceIn(0, 100)
        if (!clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            return
        }
        val gainControl = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl

        val min = gainControl.minimum
        val max = gainControl.maximum
        val linear = bounded / 100.0f
        val dB = if (linear <= 0f) min else (20f * kotlin.math.log10(linear.toDouble()).toFloat()).coerceIn(min, max)
        gainControl.value = dB
    }

    private fun startPreview(audioBytes: ByteArray, volumePercent: Int, durationSeconds: Int) {
        val safeSeconds = durationSeconds.coerceIn(1, 10)
        val token = synchronized(previewLock) {
            previewToken += 1
            stopPreviewLocked()
            previewToken
        }

        val t = thread(start = true, isDaemon = true, name = "ErrorSoundPreview") {
            runCatching {
                playPreviewLoop(audioBytes, volumePercent, safeSeconds, token)
            }.onFailure {
                log.warn("Failed to preview sound", it)
            }
        }

        synchronized(previewLock) {
            if (token == previewToken) {
                previewThread = t
            } else {
                t.interrupt()
            }
        }
    }

    private fun playPreviewLoop(audioBytes: ByteArray, volumePercent: Int, durationSeconds: Int, token: Long) {
        val input = AudioSystem.getAudioInputStream(ByteArrayInputStream(audioBytes))
        val clip = AudioSystem.getClip()
        try {
            clip.open(input)
            applyVolumeIfSupported(clip, volumePercent)

            synchronized(previewLock) {
                if (token != previewToken) {
                    return
                }
                previewClip = clip
            }

            val totalDurationMillis = durationSeconds * 1_000L
            val deadlineNanos = System.nanoTime() + (totalDurationMillis * 1_000_000L)
            val clipLengthMillis = (clip.microsecondLength / 1_000L).coerceAtLeast(1L)

            while (System.nanoTime() < deadlineNanos && isPreviewActive(token)) {
                clip.framePosition = 0
                clip.start()
                val remainingMillis = ((deadlineNanos - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
                if (remainingMillis <= 0L) {
                    break
                }
                try {
                    Thread.sleep(minOf(clipLengthMillis, remainingMillis))
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                clip.stop()
                clip.flush()
            }
        } finally {
            runCatching { input.close() }
            runCatching { clip.close() }
            synchronized(previewLock) {
                if (token == previewToken) {
                    previewClip = null
                    previewThread = null
                }
            }
        }
    }

    private fun isPreviewActive(token: Long): Boolean {
        return synchronized(previewLock) { token == previewToken }
    }

    private fun stopPreviewLocked() {
        previewThread?.interrupt()
        previewThread = null
        previewClip?.let { clip ->
            runCatching { clip.stop() }
            runCatching { clip.flush() }
            runCatching { clip.close() }
        }
        previewClip = null
    }

    private fun generateToneWavBytes(
        durationSeconds: Double,
        frequencyHz: Double,
        sampleRate: Float,
    ): ByteArray {
        val totalSamples = (durationSeconds * sampleRate).toInt()
        val format = AudioFormat(sampleRate, 16, 1, true, false)
        val pcm = ByteArray(totalSamples * 2)

        for (sampleIndex in 0 until totalSamples) {
            val t = sampleIndex / sampleRate
            val sample = (kotlin.math.sin(2 * Math.PI * frequencyHz * t) * Short.MAX_VALUE * 0.4).toInt().toShort()
            pcm[sampleIndex * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[sampleIndex * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        val pcmInput = ByteArrayInputStream(pcm)
        val audioInput = AudioInputStream(pcmInput, format, totalSamples.toLong())

        ByteArrayOutputStream().use { out ->
            AudioSystem.write(audioInput, javax.sound.sampled.AudioFileFormat.Type.WAVE, out)
            return out.toByteArray()
        }
    }

}
