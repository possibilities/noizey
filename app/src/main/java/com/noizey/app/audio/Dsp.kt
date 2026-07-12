package com.noizey.app.audio

import com.noizey.app.model.GeneratorKind
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

internal interface StereoGenerator {
    fun generate(output: FloatArray)
}

private class FastRandom(seed: Int) {
    private var state = if (seed == 0) 0x6D2B79F5 else seed

    fun unit(): Float {
        var value = state
        value = value xor (value shl 13)
        value = value xor (value ushr 17)
        value = value xor (value shl 5)
        state = value
        return (value ushr 8) / 16_777_216f
    }

    fun bipolar(): Float = unit() * 2f - 1f
    fun range(min: Float, max: Float): Float = min + (max - min) * unit()
}

private class WhiteGenerator : StereoGenerator {
    private val left = FastRandom(0x13579BDF)
    private val right = FastRandom(0x2468ACE)

    override fun generate(output: FloatArray) {
        output[0] = left.bipolar() * 0.58f
        output[1] = right.bipolar() * 0.58f
    }
}

private class BrownGenerator : StereoGenerator {
    private val left = FastRandom(0x0BADC0DE)
    private val right = FastRandom(0x51A7E123)
    private var leftValue = 0f
    private var rightValue = 0f

    override fun generate(output: FloatArray) {
        leftValue = (leftValue * 0.9965f + left.bipolar() * 0.032f).coerceIn(-0.42f, 0.42f)
        rightValue = (rightValue * 0.9965f + right.bipolar() * 0.032f).coerceIn(-0.42f, 0.42f)
        output[0] = leftValue * 1.72f
        output[1] = rightValue * 1.72f
    }
}

private class PinkChannel(seed: Int) {
    private val random = FastRandom(seed)
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var b3 = 0f
    private var b4 = 0f
    private var b5 = 0f
    private var b6 = 0f

    fun next(): Float {
        val white = random.bipolar()
        b0 = 0.99886f * b0 + white * 0.0555179f
        b1 = 0.99332f * b1 + white * 0.0750759f
        b2 = 0.96900f * b2 + white * 0.1538520f
        b3 = 0.86650f * b3 + white * 0.3104856f
        b4 = 0.55000f * b4 + white * 0.5329522f
        b5 = -0.7616f * b5 - white * 0.0168980f
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
        b6 = white * 0.115926f
        return (pink * 0.105f).coerceIn(-0.9f, 0.9f)
    }
}

private class PinkGenerator : StereoGenerator {
    private val left = PinkChannel(0x10293847)
    private val right = PinkChannel(0x56473829)

    override fun generate(output: FloatArray) {
        output[0] = left.next()
        output[1] = right.next()
    }
}

private class DifferenceChannel(seed: Int, private val order: Int) {
    private val random = FastRandom(seed)
    private var previous = 0f
    private var previousDifference = 0f

    fun next(): Float {
        val value = random.bipolar()
        val difference = value - previous
        previous = value
        if (order == 1) return difference * 0.34f
        val second = difference - previousDifference
        previousDifference = difference
        return second * 0.19f
    }
}

private class DifferenceGenerator(order: Int) : StereoGenerator {
    private val left = DifferenceChannel(0x1122AA55, order)
    private val right = DifferenceChannel(0x55AA2211, order)

    override fun generate(output: FloatArray) {
        output[0] = left.next()
        output[1] = right.next()
    }
}

private class GrayChannel(seed: Int) {
    private val random = FastRandom(seed)
    private var brown = 0f
    private var previous = 0f

    fun next(): Float {
        val white = random.bipolar()
        brown = brown * 0.997f + white * 0.026f
        val blue = white - previous
        previous = white
        return (white * 0.24f + brown * 0.82f + blue * 0.10f).coerceIn(-0.85f, 0.85f)
    }
}

private class GrayGenerator : StereoGenerator {
    private val left = GrayChannel(0x22FF11AA)
    private val right = GrayChannel(0x33EE44BB)
    override fun generate(output: FloatArray) {
        output[0] = left.next()
        output[1] = right.next()
    }
}

private class GreenChannel(seed: Int) {
    private val random = FastRandom(seed)
    private var fast = 0f
    private var slow = 0f

    fun next(): Float {
        val white = random.bipolar()
        fast += (white - fast) * 0.075f
        slow += (white - slow) * 0.0018f
        return ((fast - slow) * 2.7f + slow * 0.7f).coerceIn(-0.86f, 0.86f)
    }
}

private class GreenGenerator : StereoGenerator {
    private val left = GreenChannel(0x5555AAAA)
    private val right = GreenChannel(0x77773333)
    override fun generate(output: FloatArray) {
        output[0] = left.next()
        output[1] = right.next()
    }
}

private class FanGenerator(
    private val sampleRate: Int,
    private val cabin: Boolean,
) : StereoGenerator {
    private val left = FastRandom(if (cabin) 0x10203040 else 0x40302010)
    private val right = FastRandom(if (cabin) 0x50607080 else 0x80706050.toInt())
    private var airLeft = 0f
    private var airRight = 0f
    private var phase = 0.0

    override fun generate(output: FloatArray) {
        val smoothing = if (cabin) 0.009f else 0.018f
        airLeft += (left.bipolar() - airLeft) * smoothing
        airRight += (right.bipolar() - airRight) * smoothing
        val baseFrequency = if (cabin) 57.0 else 72.0
        phase += 2.0 * PI * baseFrequency / sampleRate
        if (phase > 2.0 * PI) phase -= 2.0 * PI
        val motor = sin(phase).toFloat() * if (cabin) 0.11f else 0.07f
        val harmonic = sin(phase * 2.0).toFloat() * 0.035f
        val gain = if (cabin) 2.9f else 3.4f
        output[0] = (airLeft * gain + motor + harmonic).coerceIn(-0.9f, 0.9f)
        output[1] = (airRight * gain + motor * 0.96f + harmonic).coerceIn(-0.9f, 0.9f)
    }
}

private class RainGenerator(
    private val sampleRate: Int,
    private val density: Float,
    private val onWindow: Boolean,
) : StereoGenerator {
    private val left = FastRandom(0x19770214)
    private val right = FastRandom(0x20010909)
    private var lowLeft = 0f
    private var lowRight = 0f
    private var dropLeft = 0f
    private var dropRight = 0f
    private var dropPhaseLeft = 0.0
    private var dropPhaseRight = 0.0
    private var dropFrequencyLeft = 1800.0
    private var dropFrequencyRight = 2200.0

    override fun generate(output: FloatArray) {
        val whiteLeft = left.bipolar()
        val whiteRight = right.bipolar()
        lowLeft += (whiteLeft - lowLeft) * 0.035f
        lowRight += (whiteRight - lowRight) * 0.035f
        val rainGain = 0.20f + density * 0.22f
        var outLeft = (whiteLeft - lowLeft * 0.65f) * rainGain
        var outRight = (whiteRight - lowRight * 0.65f) * rainGain

        val chance = if (onWindow) 0.0016f else 0.00035f * density
        if (left.unit() < chance) {
            dropLeft = left.range(0.18f, if (onWindow) 0.72f else 0.38f)
            dropFrequencyLeft = left.range(900f, 3100f).toDouble()
        }
        if (right.unit() < chance) {
            dropRight = right.range(0.18f, if (onWindow) 0.72f else 0.38f)
            dropFrequencyRight = right.range(900f, 3100f).toDouble()
        }
        dropPhaseLeft += 2.0 * PI * dropFrequencyLeft / sampleRate
        dropPhaseRight += 2.0 * PI * dropFrequencyRight / sampleRate
        if (dropPhaseLeft > 2.0 * PI) dropPhaseLeft -= 2.0 * PI
        if (dropPhaseRight > 2.0 * PI) dropPhaseRight -= 2.0 * PI
        outLeft += sin(dropPhaseLeft).toFloat() * dropLeft
        outRight += sin(dropPhaseRight).toFloat() * dropRight
        dropLeft *= if (onWindow) 0.9962f else 0.992f
        dropRight *= if (onWindow) 0.9962f else 0.992f
        output[0] = outLeft.coerceIn(-0.95f, 0.95f)
        output[1] = outRight.coerceIn(-0.95f, 0.95f)
    }
}

private class ThunderGenerator(private val sampleRate: Int) : StereoGenerator {
    private val random = FastRandom(0x0DDC0FFE)
    private var countdown = sampleRate * 2
    private var age = -1
    private var lowLeft = 0f
    private var lowRight = 0f
    private var phase = 0.0

    override fun generate(output: FloatArray) {
        if (age < 0 && --countdown <= 0) {
            age = 0
            countdown = (sampleRate * random.range(12f, 30f)).toInt()
        }
        if (age < 0) {
            output[0] = 0f
            output[1] = 0f
            return
        }
        val seconds = age.toDouble() / sampleRate
        val envelope = (1.0 - exp(-seconds / 0.22)) * exp(-seconds / 3.4)
        lowLeft += (random.bipolar() - lowLeft) * 0.0035f
        lowRight += (random.bipolar() - lowRight) * 0.0031f
        phase += 2.0 * PI * (34.0 + 4.0 * sin(seconds * 1.7)) / sampleRate
        if (phase > 2.0 * PI) phase -= 2.0 * PI
        val roll = sin(phase).toFloat() * 0.32f + lowLeft * 2.8f
        val rollRight = sin(phase * 1.013).toFloat() * 0.30f + lowRight * 2.8f
        output[0] = (roll * envelope).toFloat().coerceIn(-0.9f, 0.9f)
        output[1] = (rollRight * envelope).toFloat().coerceIn(-0.9f, 0.9f)
        age++
        if (seconds > 8.0) age = -1
    }
}

private class OceanGenerator(private val sampleRate: Int) : StereoGenerator {
    private val left = FastRandom(0x0CEA0123)
    private val right = FastRandom(0x0CEA0456)
    private var washLeft = 0f
    private var washRight = 0f
    private var phase = 0.0

    override fun generate(output: FloatArray) {
        washLeft += (left.bipolar() - washLeft) * 0.018f
        washRight += (right.bipolar() - washRight) * 0.018f
        phase += 2.0 * PI * 0.085 / sampleRate
        if (phase > 2.0 * PI) phase -= 2.0 * PI
        val wave = (0.16 + 0.84 * ((sin(phase) + 1.0) * 0.5).let { it * it }).toFloat()
        val waveRight = (0.16 + 0.84 * ((sin(phase + 0.34) + 1.0) * 0.5).let { it * it }).toFloat()
        output[0] = (washLeft * 3.6f * wave).coerceIn(-0.9f, 0.9f)
        output[1] = (washRight * 3.6f * waveRight).coerceIn(-0.9f, 0.9f)
    }
}

private class StreamGenerator(private val sampleRate: Int, private val waterfall: Boolean) : StereoGenerator {
    private val left = FastRandom(if (waterfall) 0x7A7EFA11 else 0x57EA0123)
    private val right = FastRandom(if (waterfall) 0x7A7EFA22 else 0x57EA0456)
    private var fastLeft = 0f
    private var fastRight = 0f
    private var slowLeft = 0f
    private var slowRight = 0f
    private var bubbleLeft = 0f
    private var bubbleRight = 0f
    private var bubblePhaseLeft = 0.0
    private var bubblePhaseRight = 0.0

    override fun generate(output: FloatArray) {
        val whiteLeft = left.bipolar()
        val whiteRight = right.bipolar()
        fastLeft += (whiteLeft - fastLeft) * if (waterfall) 0.12f else 0.075f
        fastRight += (whiteRight - fastRight) * if (waterfall) 0.12f else 0.075f
        slowLeft += (whiteLeft - slowLeft) * 0.005f
        slowRight += (whiteRight - slowRight) * 0.005f
        val baseGain = if (waterfall) 2.25f else 2.7f
        var outLeft = (fastLeft - slowLeft) * baseGain + whiteLeft * if (waterfall) 0.18f else 0.07f
        var outRight = (fastRight - slowRight) * baseGain + whiteRight * if (waterfall) 0.18f else 0.07f
        if (!waterfall) {
            if (left.unit() < 0.00045f) bubbleLeft = left.range(0.12f, 0.36f)
            if (right.unit() < 0.00045f) bubbleRight = right.range(0.12f, 0.36f)
            bubblePhaseLeft += 2.0 * PI * 720.0 / sampleRate
            bubblePhaseRight += 2.0 * PI * 810.0 / sampleRate
            if (bubblePhaseLeft > 2.0 * PI) bubblePhaseLeft -= 2.0 * PI
            if (bubblePhaseRight > 2.0 * PI) bubblePhaseRight -= 2.0 * PI
            outLeft += sin(bubblePhaseLeft).toFloat() * bubbleLeft
            outRight += sin(bubblePhaseRight).toFloat() * bubbleRight
            bubbleLeft *= 0.997f
            bubbleRight *= 0.997f
        }
        output[0] = outLeft.coerceIn(-0.92f, 0.92f)
        output[1] = outRight.coerceIn(-0.92f, 0.92f)
    }
}

private class WindGenerator(private val sampleRate: Int) : StereoGenerator {
    private val left = FastRandom(0x711D0123)
    private val right = FastRandom(0x711D0456)
    private var airLeft = 0f
    private var airRight = 0f
    private var gustLeft = 0f
    private var gustRight = 0f
    private var phase = 0.0

    override fun generate(output: FloatArray) {
        airLeft += (left.bipolar() - airLeft) * 0.011f
        airRight += (right.bipolar() - airRight) * 0.011f
        gustLeft += (left.bipolar() - gustLeft) * 0.00022f
        gustRight += (right.bipolar() - gustRight) * 0.00022f
        phase += 2.0 * PI * 0.035 / sampleRate
        if (phase > 2.0 * PI) phase -= 2.0 * PI
        val movement = (0.48 + 0.34 * sin(phase) + gustLeft * 1.4).toFloat().coerceIn(0.10f, 0.95f)
        val movementRight = (0.48 + 0.34 * sin(phase + 0.55) + gustRight * 1.4).toFloat().coerceIn(0.10f, 0.95f)
        output[0] = (airLeft * 4.2f * movement).coerceIn(-0.88f, 0.88f)
        output[1] = (airRight * 4.2f * movementRight).coerceIn(-0.88f, 0.88f)
    }
}

private class ForestGenerator(private val sampleRate: Int) : StereoGenerator {
    private val left = FastRandom(0xF0AE5711.toInt())
    private val right = FastRandom(0xF0AE5722.toInt())
    private var airLeft = 0f
    private var airRight = 0f
    private var chirpFrames = 0
    private var chirpCountdown = sampleRate / 2
    private var chirpPhase = 0.0
    private var chirpPan = 0f

    override fun generate(output: FloatArray) {
        airLeft += (left.bipolar() - airLeft) * 0.006f
        airRight += (right.bipolar() - airRight) * 0.006f
        var insect = 0f
        if (chirpFrames > 0) {
            val pulse = if ((chirpFrames / (sampleRate / 70).coerceAtLeast(1)) % 2 == 0) 1f else 0f
            chirpPhase += 2.0 * PI * 4100.0 / sampleRate
            if (chirpPhase > 2.0 * PI) chirpPhase -= 2.0 * PI
            insect = sin(chirpPhase).toFloat() * 0.16f * pulse
            chirpFrames--
        } else if (--chirpCountdown <= 0) {
            chirpFrames = (sampleRate * left.range(0.12f, 0.42f)).toInt()
            chirpCountdown = (sampleRate * left.range(0.35f, 2.2f)).toInt()
            chirpPan = left.range(-0.75f, 0.75f)
        }
        output[0] = (airLeft * 1.35f + insect * (1f - chirpPan) * 0.5f).coerceIn(-0.75f, 0.75f)
        output[1] = (airRight * 1.35f + insect * (1f + chirpPan) * 0.5f).coerceIn(-0.75f, 0.75f)
    }
}

private class FireGenerator : StereoGenerator {
    private val left = FastRandom(0xF1AE0123.toInt())
    private val right = FastRandom(0xF1AE0456.toInt())
    private var bedLeft = 0f
    private var bedRight = 0f
    private var crackleLeft = 0f
    private var crackleRight = 0f

    override fun generate(output: FloatArray) {
        bedLeft += (left.bipolar() - bedLeft) * 0.005f
        bedRight += (right.bipolar() - bedRight) * 0.005f
        if (left.unit() < 0.0012f) crackleLeft += left.range(0.18f, 0.85f) * if (left.unit() > 0.5f) 1f else -1f
        if (right.unit() < 0.0012f) crackleRight += right.range(0.18f, 0.85f) * if (right.unit() > 0.5f) 1f else -1f
        crackleLeft *= 0.972f
        crackleRight *= 0.972f
        output[0] = (bedLeft * 1.8f + crackleLeft).coerceIn(-0.92f, 0.92f)
        output[1] = (bedRight * 1.8f + crackleRight).coerceIn(-0.92f, 0.92f)
    }
}

internal object GeneratorFactory {
    fun create(kind: GeneratorKind, sampleRate: Int): StereoGenerator = when (kind) {
        GeneratorKind.WHITE -> WhiteGenerator()
        GeneratorKind.PINK -> PinkGenerator()
        GeneratorKind.BROWN -> BrownGenerator()
        GeneratorKind.GRAY -> GrayGenerator()
        GeneratorKind.GREEN -> GreenGenerator()
        GeneratorKind.BLUE -> DifferenceGenerator(1)
        GeneratorKind.VIOLET -> DifferenceGenerator(2)
        GeneratorKind.DEEP_FAN -> FanGenerator(sampleRate, cabin = false)
        GeneratorKind.CABIN_HUM -> FanGenerator(sampleRate, cabin = true)
        GeneratorKind.SOFT_RAIN -> RainGenerator(sampleRate, density = 0.48f, onWindow = false)
        GeneratorKind.RAIN_WINDOW -> RainGenerator(sampleRate, density = 0.58f, onWindow = true)
        GeneratorKind.HEAVY_RAIN -> RainGenerator(sampleRate, density = 1f, onWindow = false)
        GeneratorKind.DISTANT_THUNDER -> ThunderGenerator(sampleRate)
        GeneratorKind.OCEAN -> OceanGenerator(sampleRate)
        GeneratorKind.STREAM -> StreamGenerator(sampleRate, waterfall = false)
        GeneratorKind.WATERFALL -> StreamGenerator(sampleRate, waterfall = true)
        GeneratorKind.WIND_TREES -> WindGenerator(sampleRate)
        GeneratorKind.FOREST_NIGHT -> ForestGenerator(sampleRate)
        GeneratorKind.FIREPLACE -> FireGenerator()
    }
}
