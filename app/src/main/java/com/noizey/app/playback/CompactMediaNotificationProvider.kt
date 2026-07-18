package com.noizey.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.noizey.app.R
import com.noizey.app.model.SoundCatalog

/**
 * An intentionally small service notification.
 *
 * MediaStyle notifications become a large system media card on recent Android versions. Noizey
 * only needs a glanceable description and one transport control, so this provider deliberately
 * uses a regular decorated notification with the play/pause target embedded in its single row.
 */
@OptIn(UnstableApi::class)
class CompactMediaNotificationProvider(
    private val context: Context,
) : MediaNotification.Provider {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        ensureChannel()

        val state = PlaybackStore.state.value
        val isPlaying = mediaSession.player.playWhenReady
        val toggleIntent = actionFactory.createMediaActionPendingIntent(
            mediaSession,
            Player.COMMAND_PLAY_PAUSE,
        )
        val compactView = createCompactView(state, isPlaying).apply {
            setOnClickPendingIntent(R.id.notification_toggle, toggleIntent)
        }
        val expandedView = createCompactView(state, isPlaying).apply {
            setOnClickPendingIntent(R.id.notification_toggle, toggleIntent)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(state.mix.name)
            .setContentText(describeMix(state))
            .setContentIntent(mediaSession.sessionActivity)
            .setCustomContentView(compactView)
            .setCustomBigContentView(expandedView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
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

    private fun createCompactView(state: PlaybackUiState, isPlaying: Boolean) =
        RemoteViews(context.packageName, R.layout.notification_playback_compact).apply {
            setTextViewText(R.id.notification_mix, describeMix(state))
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
    }

    private companion object {
        const val CHANNEL_ID = "noizey-playback"
        const val NOTIFICATION_ID = 1001

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
