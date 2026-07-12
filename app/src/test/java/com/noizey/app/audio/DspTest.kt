package com.noizey.app.audio

import com.noizey.app.model.GeneratorKind
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DspTest {
    @Test
    fun everyGeneratorProducesFiniteBoundedAudio() {
        GeneratorKind.entries.forEach { kind ->
            val generator = GeneratorFactory.create(kind, SAMPLE_RATE)
            val frame = FloatArray(2)
            var energy = 0.0
            var finite = true
            var maximum = 0f
            repeat(160_000) {
                generator.generate(frame)
                frame.forEach { sample ->
                    finite = finite && sample.isFinite()
                    maximum = maxOf(maximum, abs(sample))
                    energy += sample * sample
                }
            }
            assertTrue("$kind emitted a non-finite sample", finite)
            assertTrue("$kind exceeded its safety bound: $maximum", maximum <= 1.001f)
            assertTrue("$kind was unexpectedly silent", energy > 0.001)
        }
    }

    @Test
    fun brownNoiseMovesMoreSlowlyThanWhiteNoise() {
        val brownDelta = meanDelta(GeneratorKind.BROWN)
        val whiteDelta = meanDelta(GeneratorKind.WHITE)

        assertTrue("expected brown=$brownDelta to be smoother than white=$whiteDelta", brownDelta < whiteDelta * 0.35)
    }

    private fun meanDelta(kind: GeneratorKind): Double {
        val generator = GeneratorFactory.create(kind, SAMPLE_RATE)
        val frame = FloatArray(2)
        var previous = 0f
        var total = 0.0
        repeat(25_000) { index ->
            generator.generate(frame)
            if (index > 1_000) total += abs(frame[0] - previous)
            previous = frame[0]
        }
        return total / 24_000.0
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
