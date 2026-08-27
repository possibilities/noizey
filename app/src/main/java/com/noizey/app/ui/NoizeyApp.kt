package com.noizey.app.ui

import android.content.ComponentName
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.noizey.app.NoizeyApplication
import com.noizey.app.model.BuiltInPresets
import com.noizey.app.model.Preset
import com.noizey.app.playback.NoizePlaybackService
import com.noizey.app.playback.PlaybackStore

@Composable
fun NoizeyApp() {
    val context = LocalContext.current
    val application = context.applicationContext as NoizeyApplication
    val repository = application.preferencesRepository
    val playbackState by PlaybackStore.state.collectAsStateWithLifecycle()
    val controller = rememberNoizeyMediaController(context.applicationContext)

    var customPresets by remember(repository) {
        mutableStateOf(repository.loadCustomPresets())
    }
    var openSheet by remember { mutableStateOf<NoizeySheet?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetPendingDelete by remember { mutableStateOf<Preset?>(null) }
    var preferencesOpen by rememberSaveable { mutableStateOf(false) }
    var stayRunningWhenHeadphonesUnplugged by remember(repository) {
        mutableStateOf(repository.stayRunningWhenHeadphonesUnplugged())
    }

    BackHandler(enabled = preferencesOpen) {
        preferencesOpen = false
    }

    if (preferencesOpen) {
        PreferencesScreen(
            stayRunningWhenHeadphonesUnplugged = stayRunningWhenHeadphonesUnplugged,
            onStayRunningWhenHeadphonesUnpluggedChange = { enabled ->
                stayRunningWhenHeadphonesUnplugged = enabled
                repository.setStayRunningWhenHeadphonesUnplugged(enabled)
            },
            onBack = { preferencesOpen = false },
        )
        return
    }

    NoizeyScreen(
        state = playbackState,
        presets = BuiltInPresets.all + customPresets,
        controllerReady = controller != null,
        onPlayPause = {
            if (playbackState.isPlaying) controller?.pause() else controller?.play()
        },
        onStop = { controller?.stop() },
        onMasterVolumeChange = PlaybackStore::setMasterVolume,
        onPresetSelected = PlaybackStore::applyPreset,
        onPresetDeleteRequested = { presetPendingDelete = it },
        onSavePresetRequested = { showSaveDialog = true },
        onLayerVolumeChange = PlaybackStore::setLayerVolume,
        onLayerEnabledChange = PlaybackStore::setLayerEnabled,
        onLayerRemove = PlaybackStore::removeLayer,
        onAddSoundRequested = { openSheet = NoizeySheet.Sounds },
        onTimerRequested = { openSheet = NoizeySheet.Timer },
        onPreferencesRequested = {
            openSheet = null
            preferencesOpen = true
        },
        onInfoRequested = { openSheet = NoizeySheet.Info },
    )

    when (openSheet) {
        NoizeySheet.Sounds -> AddSoundSheet(
            activeLayerIds = playbackState.mix.layers.keys,
            onToggleSound = { id, added ->
                if (added) PlaybackStore.addLayer(id) else PlaybackStore.removeLayer(id)
            },
            onDismiss = { openSheet = null },
        )

        NoizeySheet.Timer -> TimerSheet(
            timer = playbackState.timer,
            onTimerSelected = {
                PlaybackStore.setTimer(it)
                openSheet = null
            },
            onTimerCleared = {
                PlaybackStore.clearTimer()
                openSheet = null
            },
            onDismiss = { openSheet = null },
        )

        NoizeySheet.Info -> InfoSheet(onDismiss = { openSheet = null })
        null -> Unit
    }

    if (showSaveDialog) {
        SavePresetDialog(
            onSave = { name ->
                val preset = repository.saveCustomPreset(name, playbackState.mix)
                PlaybackStore.markSavedPreset(preset)
                customPresets = repository.loadCustomPresets()
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }

    presetPendingDelete?.let { preset ->
        DeletePresetDialog(
            preset = preset,
            onDelete = {
                repository.deleteCustomPreset(preset.id)
                PlaybackStore.forgetPreset(preset.id)
                customPresets = repository.loadCustomPresets()
                presetPendingDelete = null
            },
            onDismiss = { presetPendingDelete = null },
        )
    }
}

private enum class NoizeySheet {
    Sounds,
    Timer,
    Info,
}

@Composable
private fun rememberNoizeyMediaController(context: Context): MediaController? {
    var controller by remember(context) { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        var disposed = false
        val sessionToken = SessionToken(
            context,
            ComponentName(context, NoizePlaybackService::class.java),
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                if (!disposed) {
                    controller = runCatching { controllerFuture.get() }.getOrNull()
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            disposed = true
            controller = null
            MediaController.releaseFuture(controllerFuture)
        }
    }

    return controller
}
