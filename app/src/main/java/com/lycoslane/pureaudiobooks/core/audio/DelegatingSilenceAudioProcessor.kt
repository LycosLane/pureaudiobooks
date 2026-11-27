package com.lycoslane.pureaudiobooks.core.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

@UnstableApi
class DelegatingSilenceAudioProcessor : AudioProcessor {

    private val tiers = arrayOf(
        SilenceSkippingAudioProcessor(1_600_000L, 0.35f, 1_600_000L, 10, 1024),
        SilenceSkippingAudioProcessor(850_000L, 0.20f, 850_000L, 5, 1024),
        SilenceSkippingAudioProcessor(400_000L, 0.10f, 400_000L, 2, 1280)
    )

    @Volatile
    var activeTier: Int = -1
        private set

    private var currentTier: Int = -1
    private var silenceSupported: Boolean = false
    private var inputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var passthroughBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var reusableBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded: Boolean = false

    fun setMode(enabled: Boolean, intensity: Int) {
        activeTier = if (enabled) intensity.coerceIn(0, 2) else -1
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputFormat = inputAudioFormat
        if (inputAudioFormat == AudioProcessor.AudioFormat.NOT_SET) {
            silenceSupported = false
            return AudioProcessor.AudioFormat.NOT_SET
        }
        var allSupported = true
        for (tier in tiers) {
            tier.setEnabled(true)
            val configured = try {
                tier.configure(inputAudioFormat)
            } catch (_: AudioProcessor.UnhandledAudioFormatException) {
                AudioProcessor.AudioFormat.NOT_SET
            }
            if (configured == AudioProcessor.AudioFormat.NOT_SET) {
                allSupported = false
            } else {
                tier.flush()
                if (!tier.isActive) {
                    allSupported = false
                }
            }
        }
        silenceSupported = allSupported
        currentTier = if (silenceSupported) activeTier else -1
        return inputAudioFormat
    }

    override fun isActive(): Boolean = inputFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val targetTier = if (silenceSupported) activeTier else -1
        if (targetTier != currentTier) {
            if (currentTier in 0..2) tiers[currentTier].flush()
            if (targetTier in 0..2) tiers[targetTier].flush()
            currentTier = targetTier
        }

        if (currentTier in 0..2) {
            tiers[currentTier].queueInput(inputBuffer)
        } else {
            val remaining = inputBuffer.remaining()
            if (remaining > 0) {
                if (reusableBuffer.capacity() < remaining) {
                    reusableBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
                } else {
                    reusableBuffer.clear()
                }
                reusableBuffer.put(inputBuffer)
                reusableBuffer.flip()
                passthroughBuffer = reusableBuffer
            }
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
        if (currentTier in 0..2) tiers[currentTier].queueEndOfStream()
    }

    override fun getOutput(): ByteBuffer {
        return if (currentTier in 0..2) {
            tiers[currentTier].getOutput()
        } else {
            val out = passthroughBuffer
            passthroughBuffer = AudioProcessor.EMPTY_BUFFER
            out
        }
    }

    override fun isEnded(): Boolean {
        return inputEnded && if (currentTier in 0..2) {
            tiers[currentTier].isEnded
        } else {
            !passthroughBuffer.hasRemaining()
        }
    }

    override fun flush() {
        inputEnded = false
        passthroughBuffer = AudioProcessor.EMPTY_BUFFER
        if (reusableBuffer !== AudioProcessor.EMPTY_BUFFER) {
            reusableBuffer.clear()
        }
        currentTier = if (silenceSupported) activeTier else -1
        for (tier in tiers) tier.flush()
    }

    override fun reset() {
        flush()
        silenceSupported = false
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        reusableBuffer = AudioProcessor.EMPTY_BUFFER
        for (tier in tiers) tier.reset()
    }
}
