package com.noizey.app.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.noizey.app.MainActivity
import com.noizey.app.R
import com.noizey.app.audio.NoiseEngine
import com.noizey.app.model.SoundCatalog

@OptIn(UnstableApi::class)
class NoizePlaybackService : MediaSessionService() {
    private lateinit var engine: NoiseEngine
    private lateinit var noizePlayer: NoizePlayer
    private lateinit var mediaSession: MediaSession
    private val handler = Handler(Looper.getMainLooper())
    private val servicePreferences by lazy {
        getSharedPreferences("noizey.playback", Context.MODE_PRIVATE)
    }

    private val storeListener: (PlaybackUiState) -> Unit = { state ->
        engine.updateMix(state.mix)
        if (!state.isPlaying || state.timer == null) engine.setFadeMultiplier(1f)
        if (state.timer?.endsAtEpochMillis != null || state.isPlaying) {
            persistTimerEnd(state.timer?.endsAtEpochMillis)
        }
        noizePlayer.refreshState()
    }

    private val timerTicker = object : Runnable {
        override fun run() {
            val state = PlaybackStore.state.value
            if (state.isPlaying) {
                persistTimerEnd(state.timer?.endsAtEpochMillis)
                val remaining = PlaybackStore.tickTimer()
                if (remaining != null) {
                    if (remaining <= 0L) {
                        engine.setFadeMultiplier(0f)
                        noizePlayer.pause()
                        PlaybackStore.clearTimer()
                        persistTimerEnd(null)
                        stopSelf()
                    } else {
                        engine.setFadeMultiplier((remaining / FADE_DURATION_MS.toFloat()).coerceIn(0f, 1f))
                    }
                } else {
                    engine.setFadeMultiplier(1f)
                }
            }
            handler.postDelayed(this, TIMER_TICK_MS)
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                handler.post { noizePlayer.pause() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId("noizey-playback")
            .setChannelName(R.string.playback_channel_name)
            .build()
            .apply { setSmallIcon(R.drawable.ic_notification) }
        setMediaNotificationProvider(notificationProvider)
        engine = NoiseEngine(this).also { it.updateMix(PlaybackStore.state.value.mix) }
        noizePlayer = NoizePlayer(mainLooper, engine) { playing ->
            servicePreferences.edit { putBoolean(KEY_WAS_PLAYING, playing) }
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, noizePlayer)
            .setId("noizey-session")
            .setSessionActivity(sessionActivity)
            .build()
        PlaybackStore.addListener(storeListener)
        registerNoisyReceiver()
        handler.post(timerTicker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent == null && servicePreferences.getBoolean(KEY_WAS_PLAYING, false)) {
            val timerEnd = servicePreferences.getLong(KEY_TIMER_END, NO_TIMER)
            if (timerEnd != NO_TIMER && timerEnd <= System.currentTimeMillis()) {
                servicePreferences.edit {
                    putBoolean(KEY_WAS_PLAYING, false)
                    putLong(KEY_TIMER_END, NO_TIMER)
                }
            } else {
                if (timerEnd != NO_TIMER) PlaybackStore.restoreRunningTimer(timerEnd)
                noizePlayer.play()
            }
        }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        PlaybackStore.removeListener(storeListener)
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver was never registered if service setup failed early.
        }
        mediaSession.release()
        noizePlayer.release()
        engine.stop()
        super.onDestroy()
    }

    private fun registerNoisyReceiver() {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(becomingNoisyReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(becomingNoisyReceiver, filter)
        }
    }

    private fun persistTimerEnd(value: Long?) {
        val stored = value ?: NO_TIMER
        if (servicePreferences.getLong(KEY_TIMER_END, NO_TIMER) != stored) {
            servicePreferences.edit { putLong(KEY_TIMER_END, stored) }
        }
    }

    private class NoizePlayer(
        looper: Looper,
        private val engine: NoiseEngine,
        private val onPlaybackChanged: (Boolean) -> Unit,
    ) : SimpleBasePlayer(looper) {
        private val mainHandler = Handler(looper)
        private var wantsToPlay = false

        private val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
            )
            .build()

        private val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        override fun getState(): State {
            val appState = PlaybackStore.state.value
            val timerText = appState.timer?.remainingMillis?.let(::formatRemaining)
            val layerText = describeLayers(appState)
            val metadata = MediaMetadata.Builder()
                .setTitle(appState.mix.name)
                .setArtist(timerText?.let { "$layerText · $it left" } ?: layerText)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .build()
            return State.Builder()
                .setAvailableCommands(commands)
                .setAudioAttributes(audioAttributes)
                .setPlaylist(
                    listOf(
                        MediaItemData.Builder("noizey-live-mix")
                            .setMediaMetadata(metadata)
                            .build(),
                    ),
                )
                .setCurrentMediaItemIndex(0)
                .setContentPositionMs(0L)
                .setPlaybackState(Player.STATE_READY)
                .setPlayWhenReady(wantsToPlay, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            wantsToPlay = playWhenReady
            if (playWhenReady) engine.start() else engine.stop()
            onPlaybackChanged(playWhenReady)
            PlaybackStore.setPlaying(playWhenReady)
            return Futures.immediateVoidFuture()
        }

        override fun handleStop(): ListenableFuture<*> {
            wantsToPlay = false
            engine.stop()
            onPlaybackChanged(false)
            PlaybackStore.setPlaying(false)
            PlaybackStore.clearTimer()
            return Futures.immediateVoidFuture()
        }

        override fun handleRelease(): ListenableFuture<*> {
            wantsToPlay = false
            engine.stop()
            onPlaybackChanged(false)
            PlaybackStore.setPlaying(false)
            return Futures.immediateVoidFuture()
        }

        fun refreshState() {
            if (Looper.myLooper() == applicationLooper) invalidateState()
            else mainHandler.post(::invalidateState)
        }

        private companion object {
            fun describeLayers(state: PlaybackUiState): String {
                val activeNames = state.mix.layers.entries
                    .filter { (_, layer) -> layer.enabled && layer.volume > 0f }
                    .mapNotNull { (id, _) -> SoundCatalog.byId[id]?.name }
                if (activeNames.isEmpty()) return "No active sounds"
                val visible = activeNames.take(3).joinToString(" · ")
                return if (activeNames.size > 3) "$visible +${activeNames.size - 3}" else visible
            }

            fun formatRemaining(milliseconds: Long): String {
                val totalMinutes = ((milliseconds + 59_999L) / 60_000L).coerceAtLeast(1L)
                val hours = totalMinutes / 60L
                val minutes = totalMinutes % 60L
                return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
            }
        }
    }

    private companion object {
        const val TIMER_TICK_MS = 500L
        const val FADE_DURATION_MS = 30_000L
        const val KEY_WAS_PLAYING = "was_playing"
        const val KEY_TIMER_END = "timer_end"
        const val NO_TIMER = -1L
    }
}
