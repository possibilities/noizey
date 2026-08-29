package com.noizey.app.data

import com.noizey.app.model.LayerSetting
import com.noizey.app.model.MixConfig
import com.noizey.app.model.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupCodecTest {
    @Test
    fun roundTripPreservesMixPresetsAndPlaybackPreference() {
        val snapshot = SettingsSnapshot(
            mix = MixConfig(
                name = "Rain desk",
                masterVolume = 0.4321f,
                layers = linkedMapOf(
                    "pink" to LayerSetting(0.62f),
                    "soft_rain" to LayerSetting(0.28f, enabled = false),
                ),
                activePresetId = "custom_123",
            ),
            customPresets = listOf(
                Preset(
                    id = "custom_123",
                    name = "Rain desk",
                    note = "ignored",
                    layers = linkedMapOf(
                        "pink" to LayerSetting(0.62f),
                        "soft_rain" to LayerSetting(0.28f, enabled = false),
                    ),
                    builtIn = false,
                    createdAt = 123L,
                ),
            ),
            stayRunningWhenHeadphonesUnplugged = true,
        )

        val decoded = SettingsBackupCodec.decode(SettingsBackupCodec.encode(snapshot))

        assertEquals("Rain desk", decoded.mix.name)
        assertEquals(0.4321f, decoded.mix.masterVolume, 0.0001f)
        assertEquals(listOf("pink", "soft_rain"), decoded.mix.layers.keys.toList())
        assertFalse(decoded.mix.layers.getValue("soft_rain").enabled)
        assertEquals("custom_123", decoded.mix.activePresetId)
        assertEquals("custom_123", decoded.customPresets.single().id)
        assertEquals(123L, decoded.customPresets.single().createdAt)
        assertTrue(decoded.stayRunningWhenHeadphonesUnplugged)
    }

    @Test
    fun roundTripAllowsASilentMixAndNoCustomPresets() {
        val snapshot = SettingsSnapshot(
            mix = MixConfig("Silent", 0.5f, emptyMap(), activePresetId = null),
            customPresets = emptyList(),
            stayRunningWhenHeadphonesUnplugged = false,
        )

        val decoded = SettingsBackupCodec.decode(SettingsBackupCodec.encode(snapshot))

        assertTrue(decoded.mix.layers.isEmpty())
        assertTrue(decoded.customPresets.isEmpty())
        assertFalse(decoded.stayRunningWhenHeadphonesUnplugged)
    }

    @Test
    fun rejectsMalformedOrTamperedBackups() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.decode("not a backup")
        }

        val valid = SettingsBackupCodec.encode(
            SettingsSnapshot(
                mix = MixConfig("Brown", 0.5f, mapOf("brown" to LayerSetting(0.4f))),
                customPresets = emptyList(),
                stayRunningWhenHeadphonesUnplugged = false,
            ),
        )
        val tampered = valid.replace("presets.count=0", "presets.count=1001")
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.decode(tampered)
        }
    }
}
