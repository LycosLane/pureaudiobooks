package com.lycoslane.pureaudiobooks.core.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class DelegatingSilenceAudioProcessorTest {

    @Test
    fun sixteenBitPcmActivatesAndProcessesAudio() {
        val processor = DelegatingSilenceAudioProcessor()
        processor.setMode(enabled = true, intensity = 1)
        val format16 = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)

        val configured = processor.configure(format16)
        assertEquals(format16, configured)
        assertTrue(processor.isActive)
        processor.flush()

        val input = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder())
        while (input.hasRemaining()) {
            input.putShort(2000)
        }
        input.flip()

        processor.queueInput(input)
        processor.queueEndOfStream()

        var outputBytes = 0
        while (!processor.isEnded) {
            val out = processor.getOutput()
            if (!out.hasRemaining()) break
            outputBytes += out.remaining()
        }
        assertTrue("Expected processed 16-bit PCM output", outputBytes > 0)
    }

    @Test
    fun unsupportedHighBitDepthFallsBackToPassthroughWithoutDroppingAudio() {
        val processor = DelegatingSilenceAudioProcessor()
        processor.setMode(enabled = true, intensity = 2)
        val format24 = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_24BIT)

        val configured = processor.configure(format24)
        assertEquals(format24, configured)
        assertTrue(processor.isActive)
        processor.flush()

        val payload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val input = ByteBuffer.allocateDirect(payload.size).order(ByteOrder.nativeOrder())
        input.put(payload)
        input.flip()

        processor.queueInput(input)
        processor.queueEndOfStream()

        val out = processor.getOutput()
        assertEquals(payload.size, out.remaining())
        val actual = ByteArray(out.remaining())
        out.get(actual)
        assertEquals(payload.toList(), actual.toList())
        assertTrue(processor.isEnded)
    }

    @Test
    fun survivesCrossFormatTrackTransitionsWithoutDroppingAudio() {
        val processor = DelegatingSilenceAudioProcessor()
        processor.setMode(enabled = true, intensity = 1)

        // Track 1: 44.1 kHz stereo
        val fmt1 = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
        assertEquals(fmt1, processor.configure(fmt1))
        processor.flush()

        val pcm1 = ByteBuffer.allocateDirect(4096 * 4).order(ByteOrder.nativeOrder())
        repeat(4096) { i ->
            val v: Short = if (i % 2 == 0) 8000 else -8000
            pcm1.putShort(v).putShort(v)
        }
        pcm1.flip()
        processor.queueInput(pcm1)
        processor.queueEndOfStream()
        var outBytes1 = 0
        while (true) {
            val out = processor.getOutput()
            if (!out.hasRemaining()) break
            outBytes1 += out.remaining()
        }
        assertTrue("Expected non-empty output on Track 1", outBytes1 > 0)
        assertTrue(processor.isEnded)

        // Track 2 transition: 48 kHz mono
        processor.flush()
        val fmt2 = AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT)
        assertEquals(fmt2, processor.configure(fmt2))
        processor.flush()

        val pcm2 = ByteBuffer.allocateDirect(4096 * 2).order(ByteOrder.nativeOrder())
        repeat(4096) { i ->
            val v: Short = if (i % 2 == 0) 9000 else -9000
            pcm2.putShort(v)
        }
        pcm2.flip()
        processor.queueInput(pcm2)
        processor.queueEndOfStream()
        var outBytes2 = 0
        while (true) {
            val out = processor.getOutput()
            if (!out.hasRemaining()) break
            outBytes2 += out.remaining()
        }
        assertTrue("Expected non-empty output on Track 2 after format switch", outBytes2 > 0)
    }
}
