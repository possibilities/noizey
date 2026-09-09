package com.noizey.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noizey.app.model.GeneratorKind
import com.noizey.app.model.LayerSetting
import com.noizey.app.model.Preset
import com.noizey.app.model.SoundCatalog
import com.noizey.app.model.SoundDefinition
import com.noizey.app.playback.PlaybackUiState
import kotlin.math.roundToInt

@Composable
private fun NoizeyMark(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        drawCircle(ink, radius * 0.8f, style = Stroke(radius * 0.4f))
        drawCircle(ink, radius * 0.27f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoizeyScreen(
    state: PlaybackUiState,
    presets: List<Preset>,
    controllerReady: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onPresetSelected: (Preset) -> Unit,
    onPresetDeleteRequested: (Preset) -> Unit,
    onSavePresetRequested: () -> Unit,
    onLayerVolumeChange: (String, Float) -> Unit,
    onLayerEnabledChange: (String, Boolean) -> Unit,
    onLayerRemove: (String) -> Unit,
    onAddSoundRequested: () -> Unit,
    onTimerRequested: () -> Unit,
    onPreferencesRequested: () -> Unit,
    onInfoRequested: () -> Unit,
) {
    val hasLayers = state.mix.layers.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            NoizeyTopBar(
                timerRemainingMillis = state.timer?.remainingMillis,
                onTimerRequested = onTimerRequested,
                onPreferencesRequested = onPreferencesRequested,
                onInfoRequested = onInfoRequested,
            )
        },
        bottomBar = {
            TransportBar(
                mixName = state.mix.name,
                layerCount = state.mix.layers.size,
                timerRemainingMillis = state.timer?.remainingMillis,
                isPlaying = state.isPlaying,
                canPlay = controllerReady && (state.isPlaying || hasLayers),
                canStop = controllerReady && (state.isPlaying || state.timer != null),
                onPlayPause = onPlayPause,
                onStop = onStop,
            )
        },
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    top = 12.dp,
                    end = 24.dp,
                    bottom = 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "current") {
                    CurrentMix(
                        state = state,
                        showSaveAction = state.mix.activePresetId == null && hasLayers,
                        onMasterVolumeChange = onMasterVolumeChange,
                        onSavePresetRequested = onSavePresetRequested,
                    )
                }

                item(key = "preset-heading") {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionLabel("Presets")
                }

                item(key = "presets") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(presets, key = Preset::id) { preset ->
                            PresetCard(
                                preset = preset,
                                selected = preset.id == state.mix.activePresetId,
                                onSelected = { onPresetSelected(preset) },
                                onDeleteRequested = {
                                    if (!preset.builtIn) onPresetDeleteRequested(preset)
                                },
                            )
                        }
                    }
                }

                item(key = "mixer-heading") {
                    Spacer(modifier = Modifier.height(8.dp))
                    MixerHeader(
                        layerCount = state.mix.layers.size,
                        onAddSoundRequested = onAddSoundRequested,
                    )
                }

                if (state.mix.layers.isEmpty()) {
                    item(key = "empty-mix") {
                        EmptyMix(onAddSoundRequested = onAddSoundRequested)
                    }
                } else {
                    items(
                        items = state.mix.layers.entries.toList(),
                        key = { it.key },
                    ) { (id, layer) ->
                        SoundCatalog.byId[id]?.let { definition ->
                            LayerCard(
                                definition = definition,
                                layer = layer,
                                onVolumeChange = { onLayerVolumeChange(id, it) },
                                onEnabledChange = { onLayerEnabledChange(id, it) },
                                onRemove = { onLayerRemove(id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoizeyTopBar(
    timerRemainingMillis: Long?,
    onTimerRequested: () -> Unit,
    onPreferencesRequested: () -> Unit,
    onInfoRequested: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NoizeyMark(Modifier.size(26.dp))
                Text(
                    text = "noizey",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = (-1).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            if (timerRemainingMillis != null) {
                TextButton(
                    onClick = onTimerRequested,
                    modifier = Modifier.semantics {
                        contentDescription =
                            "Sleep timer, ${formatRemaining(timerRemainingMillis)} remaining"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(formatRemaining(timerRemainingMillis))
                }
            } else {
                IconButton(onClick = onTimerRequested) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = "Set sleep timer",
                    )
                }
            }
            IconButton(onClick = onPreferencesRequested) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Preferences",
                )
            }
            IconButton(onClick = onInfoRequested) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = "About playback",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
private fun CurrentMix(
    state: PlaybackUiState,
    showSaveAction: Boolean,
    onMasterVolumeChange: (Float) -> Unit,
    onSavePresetRequested: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = if (state.isPlaying) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                        shape = CircleShape,
                    ),
            )
            Text(
                text = if (state.isPlaying) "Playing" else "Your mix",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.weight(1f))
            if (showSaveAction) {
                TextButton(onClick = onSavePresetRequested) {
                    Text("Save preset")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = state.mix.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (state.mix.layers.isEmpty()) {
                        "Add a sound to build your mix"
                    } else {
                        "Plays alongside your other apps"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Master volume",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = percentText(state.mix.masterVolume),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = state.mix.masterVolume,
                onValueChange = onMasterVolumeChange,
                steps = 19,
                colors = SliderDefaults.colors(
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    disabledActiveTickColor = Color.Transparent,
                    disabledInactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Master volume"
                        stateDescription = percentText(state.mix.masterVolume)
                    },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun PresetCard(
    preset: Preset,
    selected: Boolean,
    onSelected: () -> Unit,
    onDeleteRequested: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val selectionDescription = if (selected) "Selected" else "Not selected"

    Surface(
        onClick = onSelected,
        modifier = Modifier
            .width(176.dp)
            .heightIn(min = 88.dp)
            .semantics { stateDescription = selectionDescription },
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.82f))
        } else {
            null
        },
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (!preset.builtIn) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Options for ${preset.name}",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete preset") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteRequested()
                                },
                            )
                        }
                    }
                }
            }
            Text(
                text = preset.note,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MixerHeader(
    layerCount: Int,
    onAddSoundRequested: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel("Sounds")
            Text(
                soundCountLabel(layerCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onAddSoundRequested) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add sound")
        }
    }
}

@Composable
private fun EmptyMix(onAddSoundRequested: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("A very quiet mix", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Add generated noise, nature, or both. They can all play together.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onAddSoundRequested,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            ) {
                Text("Choose a sound")
            }
        }
    }
}

@Composable
private fun LayerCard(
    definition: SoundDefinition,
    layer: LayerSetting,
    onVolumeChange: (Float) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = soundIcon(definition),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = definition.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (layer.enabled) definition.category.label else "Muted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = layer.enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics {
                        contentDescription = "${definition.name} layer"
                        stateDescription = if (layer.enabled) "On" else "Muted"
                    },
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Options for ${definition.name}",
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Remove from mix") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Slider(
                    value = layer.volume,
                    onValueChange = onVolumeChange,
                    enabled = layer.enabled,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                        disabledActiveTickColor = Color.Transparent,
                        disabledInactiveTickColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "${definition.name} volume"
                            stateDescription = percentText(layer.volume)
                        },
                )
                Text(
                    text = percentText(layer.volume),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(44.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun TransportBar(
    mixName: String,
    layerCount: Int,
    timerRemainingMillis: Long?,
    isPlaying: Boolean,
    canPlay: Boolean,
    canStop: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = mixName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(soundCountLabel(layerCount))
                            timerRemainingMillis?.let {
                                append(" · ")
                                append(formatRemaining(it))
                                append(" left")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onStop,
                    enabled = canStop,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = "Stop playback",
                    )
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = canPlay,
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause playback" else "Play mix",
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

internal fun formatRemaining(milliseconds: Long): String {
    val totalMinutes = ((milliseconds.coerceAtLeast(0L) + 59_999L) / 60_000L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes.coerceAtLeast(1L)}m"
    }
}

internal fun percentText(value: Float): String = "${(value.coerceIn(0f, 1f) * 100f).roundToInt()}%"

private fun soundCountLabel(count: Int): String = if (count == 1) "1 sound" else "$count sounds"

internal fun soundIcon(definition: SoundDefinition): ImageVector = when (definition.kind) {
    GeneratorKind.WHITE,
    GeneratorKind.PINK,
    GeneratorKind.BROWN,
    GeneratorKind.GRAY,
    GeneratorKind.GREEN,
    GeneratorKind.BLUE,
    GeneratorKind.VIOLET,
    -> Icons.Rounded.GraphicEq

    GeneratorKind.DEEP_FAN,
    GeneratorKind.CABIN_HUM,
    GeneratorKind.WIND_TREES,
    -> Icons.Rounded.Air

    GeneratorKind.SOFT_RAIN,
    GeneratorKind.RAIN_WINDOW,
    GeneratorKind.HEAVY_RAIN,
    -> Icons.Rounded.WaterDrop

    GeneratorKind.DISTANT_THUNDER -> Icons.Rounded.Thunderstorm
    GeneratorKind.OCEAN,
    GeneratorKind.STREAM,
    GeneratorKind.WATERFALL,
    -> Icons.Rounded.Waves

    GeneratorKind.FOREST_NIGHT -> Icons.Rounded.Forest
    GeneratorKind.FIREPLACE -> Icons.Rounded.LocalFireDepartment
}
