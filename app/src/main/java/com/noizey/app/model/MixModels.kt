package com.noizey.app.model

import java.util.Locale

data class LayerSetting(
    val volume: Float,
    val enabled: Boolean = true,
) {
    fun normalized() = copy(volume = if (volume.isFinite()) volume.coerceIn(0f, 1f) else 0f)
}

data class MixConfig(
    val name: String,
    val masterVolume: Float,
    val layers: Map<String, LayerSetting>,
    val activePresetId: String? = null,
) {
    fun normalized(): MixConfig = copy(
        masterVolume = if (masterVolume.isFinite()) masterVolume.coerceIn(0f, 1f) else 0.38f,
        layers = layers
            .filterKeys(SoundCatalog.byId::containsKey)
            .mapValues { it.value.normalized() },
    )
}

data class Preset(
    val id: String,
    val name: String,
    val note: String,
    val layers: Map<String, LayerSetting>,
    val builtIn: Boolean = true,
    val createdAt: Long = 0L,
)

object MixCodec {
    fun encode(layers: Map<String, LayerSetting>): String = layers.entries.joinToString("|") { (id, layer) ->
        "$id,${String.format(Locale.US, "%.4f", layer.volume.coerceIn(0f, 1f))},${if (layer.enabled) 1 else 0}"
    }

    fun decode(value: String?): LinkedHashMap<String, LayerSetting> {
        val decoded = linkedMapOf<String, LayerSetting>()
        if (value.isNullOrBlank()) return decoded
        value.split('|').forEach { entry ->
            val parts = entry.split(',')
            if (parts.size != 3 || !SoundCatalog.byId.containsKey(parts[0])) return@forEach
            val rawVolume = parts[1].toFloatOrNull() ?: return@forEach
            if (!rawVolume.isFinite()) return@forEach
            val volume = rawVolume.coerceIn(0f, 1f)
            decoded[parts[0]] = LayerSetting(volume, parts[2] != "0")
        }
        return decoded
    }
}

object BuiltInPresets {
    private fun layers(vararg entries: Pair<String, Float>) = linkedMapOf(
        *entries.map { (id, volume) -> id to LayerSetting(volume) }.toTypedArray(),
    )

    val all = listOf(
        Preset("pure_brown", "Pure brown", "One deep layer", layers("brown" to 0.72f)),
        Preset("deep_sleep", "Deep sleep", "Brown · soft rain", layers("brown" to 0.64f, "soft_rain" to 0.28f)),
        Preset("quiet_focus", "Quiet focus", "Pink · stream", layers("pink" to 0.62f, "stream" to 0.16f)),
        Preset("cabin_storm", "Cabin storm", "Rain · brown · thunder", layers("brown" to 0.28f, "heavy_rain" to 0.52f, "distant_thunder" to 0.20f, "fireplace" to 0.12f)),
        Preset("ocean_night", "Ocean night", "Waves · brown · wind", layers("brown" to 0.18f, "ocean" to 0.58f, "wind_trees" to 0.12f)),
        Preset("forest_after_dark", "Forest after dark", "Night · stream · wind", layers("pink" to 0.12f, "forest_night" to 0.54f, "stream" to 0.16f, "wind_trees" to 0.10f)),
        Preset("airplane", "Airplane calm", "Brown · cabin · white", layers("brown" to 0.48f, "cabin_hum" to 0.44f, "white" to 0.12f)),
        Preset("warm_fire", "Warm fire", "Fire · brown · night", layers("fireplace" to 0.56f, "brown" to 0.22f, "forest_night" to 0.10f)),
    )

    val default = all.first()
}
