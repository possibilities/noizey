package com.noizey.app.data

import com.noizey.app.model.BuiltInPresets
import com.noizey.app.model.MixCodec
import com.noizey.app.model.MixConfig
import com.noizey.app.model.Preset
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

internal data class SettingsSnapshot(
    val mix: MixConfig,
    val customPresets: List<Preset>,
    val stayRunningWhenHeadphonesUnplugged: Boolean,
)

internal object SettingsBackupCodec {
    private const val HEADER = "NOIZEY_SETTINGS_V1"
    private const val MAX_BACKUP_LENGTH = 1_000_000
    private const val MAX_PRESETS = 1_000
    private val idPattern = Regex("[A-Za-z0-9._-]{1,128}")
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(snapshot: SettingsSnapshot): String {
        val mix = snapshot.mix.normalized()
        val fields = linkedMapOf(
            "mix.name" to encodeText(mix.name),
            "mix.master" to String.format(Locale.US, "%.4f", mix.masterVolume),
            "mix.layers" to encodeText(MixCodec.encode(mix.layers)),
            "mix.activePreset" to encodeText(mix.activePresetId.orEmpty()),
            "playback.stayRunningWhenHeadphonesUnplugged" to
                snapshot.stayRunningWhenHeadphonesUnplugged.toString(),
            "presets.count" to snapshot.customPresets.size.toString(),
        )
        snapshot.customPresets.forEachIndexed { index, preset ->
            fields["preset.$index.id"] = encodeText(preset.id)
            fields["preset.$index.name"] = encodeText(preset.name)
            fields["preset.$index.layers"] = encodeText(MixCodec.encode(preset.layers))
            fields["preset.$index.createdAt"] = preset.createdAt.toString()
        }
        return buildString {
            appendLine(HEADER)
            fields.forEach { (key, value) -> appendLine("$key=$value") }
        }
    }

    fun decode(contents: String): SettingsSnapshot {
        require(contents.length <= MAX_BACKUP_LENGTH) { "Backup is too large" }
        val lines = contents.lineSequence().toList()
        require(lines.firstOrNull() == HEADER) { "Not a Noizey settings backup" }
        val fields = linkedMapOf<String, String>()
        lines.drop(1).filter(String::isNotBlank).forEach { line ->
            val separator = line.indexOf('=')
            require(separator > 0) { "Malformed backup field" }
            val key = line.substring(0, separator)
            require(fields.put(key, line.substring(separator + 1)) == null) {
                "Duplicate backup field"
            }
        }

        val presetCount = fields.required("presets.count").toIntOrNull()
        require(presetCount != null && presetCount in 0..MAX_PRESETS) { "Invalid preset count" }
        val presets = List(presetCount) { index ->
            val id = decodeText(fields.required("preset.$index.id"))
            val name = decodeName(fields.required("preset.$index.name"))
            val layers = decodeLayers(decodeText(fields.required("preset.$index.layers")))
            val createdAt = fields.required("preset.$index.createdAt").toLongOrNull()
            require(idPattern.matches(id) && id.startsWith("custom_")) { "Invalid preset id" }
            require(layers.isNotEmpty()) { "A custom preset has no sounds" }
            require(createdAt != null && createdAt >= 0L) { "Invalid preset date" }
            Preset(
                id = id,
                name = name,
                note = "",
                layers = layers,
                builtIn = false,
                createdAt = createdAt,
            )
        }
        require(presets.map(Preset::id).distinct().size == presets.size) { "Duplicate preset id" }

        val activePresetId = decodeText(fields.required("mix.activePreset")).ifEmpty { null }
        val validPresetIds = BuiltInPresets.all.map(Preset::id).toSet() + presets.map(Preset::id)
        require(activePresetId == null || activePresetId in validPresetIds) {
            "Unknown active preset"
        }
        val masterVolume = fields.required("mix.master").toFloatOrNull()
        require(masterVolume != null && masterVolume.isFinite() && masterVolume in 0f..1f) {
            "Invalid master volume"
        }
        val stayRunning = when (
            val value = fields.required("playback.stayRunningWhenHeadphonesUnplugged")
        ) {
            "true" -> true
            "false" -> false
            else -> error("Invalid playback preference: $value")
        }

        return SettingsSnapshot(
            mix = MixConfig(
                name = decodeName(fields.required("mix.name")),
                masterVolume = masterVolume,
                layers = decodeLayers(decodeText(fields.required("mix.layers"))),
                activePresetId = activePresetId,
            ),
            customPresets = presets,
            stayRunningWhenHeadphonesUnplugged = stayRunning,
        )
    }

    private fun decodeLayers(value: String) = MixCodec.decode(value).also { decoded ->
        require(value.isEmpty() || decoded.isNotEmpty()) { "Invalid sound layers" }
        require(MixCodec.encode(decoded) == value) { "Malformed sound layers" }
    }

    private fun decodeName(value: String): String = decodeText(value).also { name ->
        require(name.isNotBlank() && name.length <= 120) { "Invalid name" }
    }

    private fun encodeText(value: String): String =
        encoder.encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String =
        String(decoder.decode(value), StandardCharsets.UTF_8)

    private fun Map<String, String>.required(key: String): String =
        requireNotNull(this[key]) { "Missing backup field: $key" }
}
