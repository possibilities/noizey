package com.noizey.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.noizey.app.R
import com.noizey.app.model.SoundCatalog
import kotlin.math.roundToInt

/**
 * An intentionally small service notification.
 *
 * MediaStyle notifications become a large system media card on recent Android versions. Noizey
 * only needs a glanceable description and focused controls, so this provider deliberately uses a
 * regular decorated notification that swaps between playback and master-volume modes in one row.
 */
@OptIn(UnstableApi::class)
class CompactMediaNotificationProvider(
    private val context: Context,
) : MediaNotification.Provider {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var volumeControlsVisible = false

    init {
        ensureChannel()
    }

    fun setVolumeControlsVisible(visible: Boolean) {
        volumeControlsVisible = visible
    }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val state = PlaybackStore.state.value
        val isPlaying = mediaSession.player.playWhenReady
        val toggleIntent = serviceIntent(
            action = NoizePlaybackService.ACTION_TOGGLE_PLAYBACK,
            requestCode = REQUEST_TOGGLE_PLAYBACK,
        )
        val compactView = if (volumeControlsVisible) {
            createVolumeView(state)
        } else {
            createPlaybackView(state, isPlaying, toggleIntent)
        }
        val expandedView = if (volumeControlsVisible) {
            createVolumeView(state)
        } else {
            createPlaybackView(state, isPlaying, toggleIntent)
        }
        val contentText = if (volumeControlsVisible) {
            context.getString(
                R.string.master_volume_percent,
                (state.mix.masterVolume * 100f).roundToInt(),
            )
        } else {
            describeMix(state)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(state.mix.name)
            .setContentText(contentText)
            .setContentIntent(mediaSession.sessionActivity)
            .setCustomContentView(compactView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
            .setNumber(0)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setOngoing(isPlaying)
            .build()

        return MediaNotification(NOTIFICATION_ID, notification)
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = false

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        MediaNotification.Provider.NotificationChannelInfo(
            CHANNEL_ID,
            context.getString(R.string.playback_channel_name),
        )

    private fun createPlaybackView(
        state: PlaybackUiState,
        isPlaying: Boolean,
        toggleIntent: PendingIntent,
    ) =
        RemoteViews(context.packageName, R.layout.notification_playback_compact).apply {
            setTextViewText(R.id.notification_mix, describeMix(state))
            setOnClickPendingIntent(
                R.id.notification_volume,
                serviceIntent(
                    action = NoizePlaybackService.ACTION_SHOW_MASTER_VOLUME,
                    requestCode = REQUEST_SHOW_VOLUME,
                ),
            )
            setOnClickPendingIntent(R.id.notification_toggle, toggleIntent)
            setImageViewResource(
                R.id.notification_toggle,
                if (isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
            )
            setContentDescription(
                R.id.notification_toggle,
                context.getString(
                    if (isPlaying) R.string.pause_playback else R.string.resume_playback,
                ),
            )
        }

    private fun createVolumeView(state: PlaybackUiState) =
        RemoteViews(context.packageName, R.layout.notification_playback_volume).apply {
            val percent = (state.mix.masterVolume * 100f).roundToInt()
            setTextViewText(
                R.id.notification_volume_percent,
                context.getString(R.string.master_volume_percent, percent),
            )
            setContentDescription(
                R.id.notification_volume_percent,
                context.getString(R.string.master_volume_content_description, percent),
            )

            val activeColor = ContextCompat.getColor(context, R.color.notification_volume_active)
            val inactiveColor = ContextCompat.getColor(context, R.color.notification_volume_inactive)
            VOLUME_ZONE_IDS.forEachIndexed { index, viewId ->
                val level = VOLUME_LEVELS[index]
                setTextColor(
                    viewId,
                    if (state.mix.masterVolume + 0.01f >= level) activeColor else inactiveColor,
                )
                setContentDescription(
                    viewId,
                    context.getString(
                        R.string.set_master_volume_percent,
                        (level * 100f).roundToInt(),
                    ),
                )
                setOnClickPendingIntent(
                    viewId,
                    serviceIntent(
                        action = NoizePlaybackService.ACTION_SET_MASTER_VOLUME,
                        requestCode = REQUEST_VOLUME_BASE + index,
                        volume = level,
                    ),
                )
            }
            setOnClickPendingIntent(
                R.id.notification_volume_hide,
                serviceIntent(
                    action = NoizePlaybackService.ACTION_HIDE_MASTER_VOLUME,
                    requestCode = REQUEST_HIDE_VOLUME,
                ),
            )
        }

    private fun serviceIntent(
        action: String,
        requestCode: Int,
        volume: Float? = null,
    ): PendingIntent {
        val intent = Intent(context, NoizePlaybackService::class.java)
            .setAction(action)
        if (volume != null) {
            intent.putExtra(NoizePlaybackService.EXTRA_MASTER_VOLUME, volume)
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            ?: NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        channel.apply {
            description = context.getString(R.string.playback_channel_description)
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
        notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }

    private companion object {
        // A fresh channel migrates installs whose original channel was created before badge
        // suppression took effect. Android preserves the behavior of an existing channel.
        const val CHANNEL_ID = "noizey-playback-v2"
        const val LEGACY_CHANNEL_ID = "noizey-playback"
        const val NOTIFICATION_ID = 1001
        const val REQUEST_TOGGLE_PLAYBACK = 2000
        const val REQUEST_SHOW_VOLUME = 2001
        const val REQUEST_HIDE_VOLUME = 2002
        const val REQUEST_VOLUME_BASE = 2100

        val VOLUME_LEVELS = floatArrayOf(0f, 0.17f, 0.33f, 0.5f, 0.67f, 0.83f, 1f)
        val VOLUME_ZONE_IDS = intArrayOf(
            R.id.notification_volume_0,
            R.id.notification_volume_1,
            R.id.notification_volume_2,
            R.id.notification_volume_3,
            R.id.notification_volume_4,
            R.id.notification_volume_5,
            R.id.notification_volume_6,
        )

        fun describeMix(state: PlaybackUiState): String {
            val activeNames = state.mix.layers.entries
                .filter { (_, layer) -> layer.enabled && layer.volume > 0f }
                .mapNotNull { (id, _) -> SoundCatalog.byId[id]?.name }
            val sounds = when {
                activeNames.isEmpty() -> "No active sounds"
                activeNames.size == 1 -> activeNames.first()
                else -> "${activeNames.first()} +${activeNames.size - 1}"
            }
            return "${state.mix.name} · $sounds"
        }
    }
}
