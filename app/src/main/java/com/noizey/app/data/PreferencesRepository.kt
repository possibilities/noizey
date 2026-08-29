package com.noizey.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.noizey.app.model.BuiltInPresets
import com.noizey.app.model.MixCodec
import com.noizey.app.model.MixConfig
import com.noizey.app.model.Preset

class PreferencesRepository(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("noizey.preferences", Context.MODE_PRIVATE)

    fun loadMix(): MixConfig {
        val fallback = BuiltInPresets.default
        val encodedLayers = preferences.getString(KEY_LAYERS, null)
        val storedLayers = MixCodec.decode(encodedLayers)
        val layers = when {
            encodedLayers == null -> fallback.layers
            encodedLayers.isEmpty() -> emptyMap()
            storedLayers.isEmpty() -> fallback.layers
            else -> storedLayers
        }
        val activePresetId = if (encodedLayers == null) {
            fallback.id
        } else {
            preferences.getString(KEY_ACTIVE_PRESET, null)
        }
        return MixConfig(
            name = preferences.getString(KEY_MIX_NAME, fallback.name) ?: fallback.name,
            masterVolume = preferences.getFloat(KEY_MASTER, 0.38f),
            layers = layers,
            activePresetId = activePresetId,
        ).normalized()
    }

    fun saveMix(mix: MixConfig) {
        preferences.edit {
            putString(KEY_MIX_NAME, mix.name)
            putFloat(KEY_MASTER, mix.masterVolume)
            putString(KEY_LAYERS, MixCodec.encode(mix.layers))
            if (mix.activePresetId == null) remove(KEY_ACTIVE_PRESET)
            else putString(KEY_ACTIVE_PRESET, mix.activePresetId)
        }
    }

    fun stayRunningWhenHeadphonesUnplugged(): Boolean =
        preferences.getBoolean(KEY_STAY_RUNNING_WHEN_HEADPHONES_UNPLUGGED, false)

    fun setStayRunningWhenHeadphonesUnplugged(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_STAY_RUNNING_WHEN_HEADPHONES_UNPLUGGED, enabled)
        }
    }

    fun createSettingsBackup(): String = SettingsBackupCodec.encode(
        SettingsSnapshot(
            mix = loadMix(),
            customPresets = loadCustomPresets(),
            stayRunningWhenHeadphonesUnplugged = stayRunningWhenHeadphonesUnplugged(),
        ),
    )

    fun restoreSettingsBackup(contents: String) {
        val snapshot = SettingsBackupCodec.decode(contents)
        val previousPresetIds = preferences.getStringSet(KEY_CUSTOM_IDS, emptySet()).orEmpty()
        preferences.edit(commit = true) {
            previousPresetIds.forEach { id ->
                remove("preset.$id.name")
                remove("preset.$id.layers")
                remove("preset.$id.created")
            }
            putString(KEY_MIX_NAME, snapshot.mix.name)
            putFloat(KEY_MASTER, snapshot.mix.masterVolume)
            putString(KEY_LAYERS, MixCodec.encode(snapshot.mix.layers))
            snapshot.mix.activePresetId?.let { putString(KEY_ACTIVE_PRESET, it) }
                ?: remove(KEY_ACTIVE_PRESET)
            putBoolean(
                KEY_STAY_RUNNING_WHEN_HEADPHONES_UNPLUGGED,
                snapshot.stayRunningWhenHeadphonesUnplugged,
            )
            putStringSet(KEY_CUSTOM_IDS, snapshot.customPresets.map(Preset::id).toSet())
            snapshot.customPresets.forEach { preset ->
                putString("preset.${preset.id}.name", preset.name)
                putString("preset.${preset.id}.layers", MixCodec.encode(preset.layers))
                putLong("preset.${preset.id}.created", preset.createdAt)
            }
        }
    }

    fun loadCustomPresets(): List<Preset> = preferences
        .getStringSet(KEY_CUSTOM_IDS, emptySet())
        .orEmpty()
        .mapNotNull { id ->
            val name = preferences.getString("preset.$id.name", null) ?: return@mapNotNull null
            val layers = MixCodec.decode(preferences.getString("preset.$id.layers", null))
            if (layers.isEmpty()) return@mapNotNull null
            Preset(
                id = id,
                name = name,
                note = describeLayers(layers.keys.toList()),
                layers = layers,
                builtIn = false,
                createdAt = preferences.getLong("preset.$id.created", 0L),
            )
        }
        .sortedBy(Preset::createdAt)

    fun saveCustomPreset(name: String, mix: MixConfig): Preset {
        val now = System.currentTimeMillis()
        val id = "custom_$now"
        val ids = preferences.getStringSet(KEY_CUSTOM_IDS, emptySet()).orEmpty().toMutableSet()
        ids += id
        preferences.edit {
            putStringSet(KEY_CUSTOM_IDS, ids)
            putString("preset.$id.name", name.trim())
            putString("preset.$id.layers", MixCodec.encode(mix.layers))
            putLong("preset.$id.created", now)
        }
        return Preset(
            id = id,
            name = name.trim(),
            note = describeLayers(mix.layers.keys.toList()),
            layers = mix.layers,
            builtIn = false,
            createdAt = now,
        )
    }

    fun deleteCustomPreset(id: String) {
        val ids = preferences.getStringSet(KEY_CUSTOM_IDS, emptySet()).orEmpty().toMutableSet()
        ids -= id
        preferences.edit {
            putStringSet(KEY_CUSTOM_IDS, ids)
            remove("preset.$id.name")
            remove("preset.$id.layers")
            remove("preset.$id.created")
        }
    }

    private fun describeLayers(ids: List<String>): String {
        val names = ids.take(3).map { id ->
            com.noizey.app.model.SoundCatalog.byId[id]?.name?.substringBefore(" noise") ?: id
        }
        return names.joinToString(" · ") + if (ids.size > 3) " +${ids.size - 3}" else ""
    }

    private companion object {
        const val KEY_MASTER = "mix.master"
        const val KEY_LAYERS = "mix.layers"
        const val KEY_MIX_NAME = "mix.name"
        const val KEY_ACTIVE_PRESET = "mix.preset"
        const val KEY_CUSTOM_IDS = "presets.custom.ids"
        const val KEY_STAY_RUNNING_WHEN_HEADPHONES_UNPLUGGED =
            "playback.stay_running_when_headphones_unplugged"
    }
}
