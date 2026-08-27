package com.noizey.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.PowerManager
import android.os.Process
import com.noizey.app.model.MixConfig
import com.noizey.app.model.SoundCatalog
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.tanh

class NoiseEngine(context: Context) {
    private val appContext = context.applicationContext
    private val sampleRate = 48_000
    private val sounds = SoundCatalog.all
    private val generators = sounds.map { GeneratorFactory.create(it.kind, sampleRate) }
    private val currentGains = FloatArray(sounds.size)
    private val targetGains = FloatArray(sounds.size)
    private val frame = FloatArray(2)
    private val gainSmoothing = (1.0 - exp(-1.0 / (sampleRate * 0.16))).toFloat()
    private val masterSmoothing = (1.0 - exp(-1.0 / (sampleRate * 0.08))).toFloat()

    @Volatile
    private var mix = MixConfig("", 0.38f, emptyMap())

    @Volatile
    private var fadeMultiplier = 1f

    @Volatile
    private var running = false

    private var audioThread: Thread? = null
    private var currentMaster = 0f
    private var wakeLock: PowerManager.WakeLock? = null

    fun updateMix(value: MixConfig) {
        mix = value.normalized()
    }

    fun setFadeMultiplier(value: Float) {
        fadeMultiplier = value.coerceIn(0f, 1f)
    }

    @Synchronized
    fun start() {
        if (running) return
        currentMaster = 0f
        running = true
        acquireWakeLock()
        audioThread = Thread(::audioLoop, "NoizeyAudio").also { thread ->
            thread.priority = Thread.MAX_PRIORITY
            thread.start()
        }
    }

    @Synchronized
    fun stop() {
        if (!running && audioThread == null) return
        running = false
        val thread = audioThread
        audioThread = null
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(1_000L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        releaseWakeLock()
    }

    fun restart() {
        stop()
        start()
    }

    private fun audioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_FLOAT
        val minimumBytes = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        val bufferFrames = 1_024
        val output = FloatArray(bufferFrames * 2)
        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(max(minimumBytes, bufferFrames * 2 * Float.SIZE_BYTES * 2))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                .build()
        } catch (_: Throwable) {
            releaseWakeLock()
            running = false
            return
        }

        try {
            audioTrack.play()
            while (running) {
                prepareTargets()
                render(output, bufferFrames)
                val written = audioTrack.write(output, 0, output.size, AudioTrack.WRITE_BLOCKING)
                if (written < 0) break
            }
        } catch (_: Throwable) {
            // A route can disappear between writes. The next explicit play recreates AudioTrack.
        } finally {
            try {
                audioTrack.pause()
                audioTrack.flush()
                audioTrack.release()
            } catch (_: Throwable) {
                // The track may already be invalid after a route failure.
            }
            releaseWakeLock()
            running = false
        }
    }

    private fun prepareTargets() {
        val snapshot = mix
        sounds.forEachIndexed { index, sound ->
            val layer = snapshot.layers[sound.id]
            targetGains[index] = if (layer?.enabled == true) layer.volume else 0f
        }
    }

    private fun render(output: FloatArray, frameCount: Int) {
        val targetMaster = mix.masterVolume * fadeMultiplier
        var outputIndex = 0
        repeat(frameCount) {
            var left = 0f
            var right = 0f
            generators.forEachIndexed { index, generator ->
                var gain = currentGains[index]
                gain += (targetGains[index] - gain) * gainSmoothing
                currentGains[index] = gain
                if (gain > 0.00005f || targetGains[index] > 0f) {
                    generator.generate(frame)
                    left += frame[0] * gain
                    right += frame[1] * gain
                }
            }
            currentMaster += (targetMaster - currentMaster) * masterSmoothing
            output[outputIndex++] = (tanh(left.toDouble() * 0.82) * currentMaster).toFloat()
            output[outputIndex++] = (tanh(right.toDouble() * 0.82) * currentMaster).toFloat()
        }
    }

    @SuppressLint("WakelockTimeout") // Deliberately held for the user-visible foreground playback session.
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "${appContext.packageName}:noise-playback",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock
        wakeLock = null
        if (lock?.isHeld == true) lock.release()
    }
}
