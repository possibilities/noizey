package com.noizey.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixCodecTest {
    @Test
    fun roundTripPreservesOrderVolumeAndMuteState() {
        val original = linkedMapOf(
            "brown" to LayerSetting(0.7234f, enabled = true),
            "soft_rain" to LayerSetting(0.2812f, enabled = false),
            "fireplace" to LayerSetting(0.1f, enabled = true),
        )

        val decoded = MixCodec.decode(MixCodec.encode(original))

        assertEquals(original.keys.toList(), decoded.keys.toList())
        assertEquals(0.7234f, decoded.getValue("brown").volume, 0.0001f)
        assertFalse(decoded.getValue("soft_rain").enabled)
        assertTrue(decoded.getValue("fireplace").enabled)
    }

    @Test
    fun decodeIgnoresUnknownAndMalformedLayers() {
        val decoded = MixCodec.decode("brown,0.5,1|not_real,0.7,1|pink,nope,0|white,NaN,1|bad")

        assertEquals(setOf("brown"), decoded.keys)
        assertEquals(0.5f, decoded.getValue("brown").volume, 0.0001f)
    }

    @Test
    fun normalizationClampsUnsafeLevels() {
        val normalized = MixConfig(
            name = "test",
            masterVolume = 3f,
            layers = mapOf("white" to LayerSetting(-2f)),
        ).normalized()

        assertEquals(1f, normalized.masterVolume, 0f)
        assertEquals(0f, normalized.layers.getValue("white").volume, 0f)
    }

    @Test
    fun everyFactoryPresetReferencesKnownSounds() {
        assertTrue(BuiltInPresets.all.isNotEmpty())
        BuiltInPresets.all.forEach { preset ->
            assertTrue("${preset.name} must contain at least one sound", preset.layers.isNotEmpty())
            assertTrue(
                "${preset.name} referenced an unknown sound",
                preset.layers.keys.all(SoundCatalog.byId::containsKey),
            )
        }
    }
}
