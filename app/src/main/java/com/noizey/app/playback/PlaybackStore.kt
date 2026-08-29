package com.noizey.app.playback

import com.noizey.app.data.PreferencesRepository
import com.noizey.app.model.LayerSetting
import com.noizey.app.model.MixConfig
import com.noizey.app.model.Preset
import com.noizey.app.model.SoundCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.ceil
import kotlin.math.max

data class SleepTimer(
    val durationMinutes: Int,
    val endsAtEpochMillis: Long? = null,
    val remainingMillis: Long = durationMinutes * 60_000L,
)

data class PlaybackUiState(
    val mix: MixConfig,
    val isPlaying: Boolean = false,
    val timer: SleepTimer? = null,
)

object PlaybackStore {
    private lateinit var repository: PreferencesRepository
    private val listeners = CopyOnWriteArraySet<(PlaybackUiState) -> Unit>()
    private val _state = MutableStateFlow(
        PlaybackUiState(
            MixConfig("Pure brown", 0.38f, mapOf("brown" to LayerSetting(0.72f)), "pure_brown"),
        ),
    )
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    fun initialize(repository: PreferencesRepository) {
        this.repository = repository
        _state.value = PlaybackUiState(repository.loadMix())
    }

    fun addListener(listener: (PlaybackUiState) -> Unit) {
        listeners += listener
        listener(_state.value)
    }

    fun removeListener(listener: (PlaybackUiState) -> Unit) {
        listeners -= listener
    }

    fun setMasterVolume(volume: Float) = updateMix(
        _state.value.mix.copy(masterVolume = volume.coerceIn(0f, 1f)),
    )

    fun setLayerVolume(id: String, volume: Float) {
        val current = _state.value.mix
        val layer = current.layers[id] ?: return
        val layers = LinkedHashMap(current.layers)
        layers[id] = layer.copy(volume = volume.coerceIn(0f, 1f))
        updateMix(current.copy(name = "Custom mix", layers = layers, activePresetId = null))
    }

    fun setLayerEnabled(id: String, enabled: Boolean) {
        val current = _state.value.mix
        val layer = current.layers[id] ?: return
        val layers = LinkedHashMap(current.layers)
        layers[id] = layer.copy(enabled = enabled)
        updateMix(current.copy(name = "Custom mix", layers = layers, activePresetId = null))
    }

    fun addLayer(id: String) {
        val definition = SoundCatalog.byId[id] ?: return
        val current = _state.value.mix
        if (current.layers.containsKey(id)) return
        val layers = LinkedHashMap(current.layers)
        layers[id] = LayerSetting(definition.defaultVolume)
        updateMix(current.copy(name = "Custom mix", layers = layers, activePresetId = null))
    }

    fun removeLayer(id: String) {
        val current = _state.value.mix
        if (!current.layers.containsKey(id)) return
        val layers = LinkedHashMap(current.layers)
        layers.remove(id)
        updateMix(current.copy(name = "Custom mix", layers = layers, activePresetId = null))
    }

    fun applyPreset(preset: Preset) {
        val current = _state.value.mix
        updateMix(
            current.copy(
                name = preset.name,
                layers = LinkedHashMap(preset.layers),
                activePresetId = preset.id,
            ),
        )
    }

    fun markSavedPreset(preset: Preset) {
        val current = _state.value.mix
        updateMix(current.copy(name = preset.name, activePresetId = preset.id))
    }

    fun forgetPreset(id: String) {
        val current = _state.value.mix
        if (current.activePresetId == id) {
            updateMix(current.copy(name = "Custom mix", activePresetId = null))
        }
    }

    fun reloadFromRepository() {
        publish(_state.value.copy(mix = repository.loadMix()))
    }

    fun setPlaying(playing: Boolean) {
        val current = _state.value
        var timer = current.timer
        val now = System.currentTimeMillis()
        if (playing && timer != null && timer.endsAtEpochMillis == null) {
            timer = timer.copy(endsAtEpochMillis = now + timer.remainingMillis)
        } else if (!playing && timer != null && timer.endsAtEpochMillis != null) {
            val end = timer.endsAtEpochMillis ?: now
            timer = timer.copy(
                endsAtEpochMillis = null,
                remainingMillis = max(0L, end - now),
            )
        }
        publish(current.copy(isPlaying = playing, timer = timer))
    }

    fun setTimer(minutes: Int?) {
        val current = _state.value
        val timer = minutes?.let {
            SleepTimer(
                durationMinutes = it,
                endsAtEpochMillis = if (current.isPlaying) System.currentTimeMillis() + it * 60_000L else null,
            )
        }
        publish(current.copy(timer = timer))
    }

    fun tickTimer(now: Long = System.currentTimeMillis()): Long? {
        val current = _state.value
        val timer = current.timer ?: return null
        val end = timer.endsAtEpochMillis ?: return timer.remainingMillis
        val remaining = max(0L, end - now)
        if (remaining / 1_000L != timer.remainingMillis / 1_000L) {
            publish(current.copy(timer = timer.copy(remainingMillis = remaining)))
        }
        return remaining
    }

    fun clearTimer() {
        val current = _state.value
        if (current.timer != null) publish(current.copy(timer = null))
    }

    fun restoreRunningTimer(endsAtEpochMillis: Long, now: Long = System.currentTimeMillis()) {
        val remaining = max(0L, endsAtEpochMillis - now)
        if (remaining == 0L) {
            clearTimer()
            return
        }
        val minutes = ceil(remaining / 60_000.0).toInt().coerceAtLeast(1)
        publish(
            _state.value.copy(
                timer = SleepTimer(
                    durationMinutes = minutes,
                    endsAtEpochMillis = endsAtEpochMillis,
                    remainingMillis = remaining,
                ),
            ),
        )
    }

    private fun updateMix(mix: MixConfig) {
        val normalized = mix.normalized()
        repository.saveMix(normalized)
        publish(_state.value.copy(mix = normalized))
    }

    private fun publish(value: PlaybackUiState) {
        _state.value = value
        listeners.forEach { it(value) }
    }
}
