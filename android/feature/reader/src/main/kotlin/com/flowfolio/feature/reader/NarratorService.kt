package com.flowfolio.feature.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.PowerManager
import android.widget.RemoteViews
import com.flowfolio.core.data.R as DataR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps Narrator Mode reading with the app in the background or the phone locked (owner, 2026-11-10), as a foreground
 * service of type media playback, and shows it in two players:
 * - the phone's own player (lock screen, quick settings, headsets) through a [MediaSession] and its media
 *   notification: previous, play or pause, next, and speed;
 * - FlowFolio's own persistent notification, with the same controls, the speed as a button showing the rate, and
 *   close, because the phone's player cannot show a speed.
 *
 * Previous and next move by the reader's choice in Settings, Sound & voice (paragraph, page, chapter, subchapter).
 * There is no seekbar: the session never publishes a duration. The narration itself runs in the reader
 * ([NarrationHub] carries the commands): this service keeps the process in the foreground and holds a wake lock while
 * it speaks, and stops when the reader lets go.
 */
// One function per part of a player (session state, metadata, each notification, each control): splitting the
// service would only scatter them.
@Suppress("TooManyFunctions")
class NarratorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSession
    private lateinit var notifications: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false
    private var art: Pair<String, Bitmap>? = null

    /** The art at notification size: a notification carries its bitmaps in its parcel, and 720 px is too large. */
    private var thumb: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.narrator_channel), NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = getString(R.string.narrator_channel_detail)
                    setShowBadge(false)
                }
        )
        session = MediaSession(this, "FlowFolioNarrator").apply {
            setCallback(Controls())
            openApp()?.let { setSessionActivity(it) }
            isActive = true
        }
        scope.launch {
            kotlinx.coroutines.flow.combine(NarrationHub.nowPlaying, NarrationHub.refreshes) { playing, _ -> playing }
                .collect { show(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val playing = NarrationHub.nowPlaying.value
        if (playing == null) {
            stop()
            return START_NOT_STICKY
        }
        // Android wants the foreground within seconds of the start: show what we have, the art follows.
        if (!inForeground) enterForeground(mediaNotification(playing, thumb))
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> NarrationHub.commands?.let { if (playing.playing) it.pause() else it.play() }
            ACTION_NEXT -> NarrationHub.commands?.next()
            ACTION_PREVIOUS -> NarrationHub.commands?.previous()
            ACTION_SPEED -> NarrationHub.commands?.cycleSpeed()
            ACTION_CLOSE -> NarrationHub.commands?.close()
        }
        return START_NOT_STICKY
    }

    private suspend fun show(playing: NowPlaying?) {
        if (playing == null) {
            stop()
            return
        }
        val key = "${playing.bookId}|${playing.coverPath}"
        val bitmap = art?.takeIf { it.first == key }?.second ?: withContext(Dispatchers.Default) {
            NarratorArt.compose(this@NarratorService, playing.coverPath, playing.title)
        }.also {
            if (art?.first != key) {
                thumb?.recycle()
                thumb = Bitmap.createScaledBitmap(it, THUMB_SIZE, THUMB_SIZE, true)
            }
            art = key to it
        }
        val small = thumb ?: bitmap
        session.setMetadata(metadata(playing, bitmap))
        session.setPlaybackState(state(playing))
        val media = mediaNotification(playing, small)
        if (inForeground) notifications.notify(MEDIA_ID, media) else enterForeground(media)
        notifications.notify(PLAYER_ID, playerNotification(playing, small))
        holdWakeLock(playing.playing)
    }

    private fun enterForeground(notification: Notification) {
        startForeground(MEDIA_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        inForeground = true
    }

    /** No duration on purpose: the phone's player then draws no seekbar (the owner asked for none). */
    private fun metadata(playing: NowPlaying, bitmap: Bitmap): MediaMetadata = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, playing.displayTitle)
        .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, playing.displayTitle)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, status(playing))
        .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, status(playing))
        .putString(MediaMetadata.METADATA_KEY_ALBUM, playing.title)
        .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
        .putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
        .build()

    private fun state(playing: NowPlaying): PlaybackState = PlaybackState.Builder()
        .setActions(
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP
        )
        .addCustomAction(
            PlaybackState.CustomAction.Builder(
                CUSTOM_SPEED,
                getString(R.string.narrator_speed_action, speedLabel(playing.rate)),
                R.drawable.ic_narrator_speed
            ).build()
        )
        .setState(
            if (playing.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
            PlaybackState.PLAYBACK_POSITION_UNKNOWN,
            playing.rate
        )
        .build()

    /** The phone's own player: the lock screen and quick settings draw it from the session. */
    private fun mediaNotification(playing: NowPlaying, bitmap: Bitmap?): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(DataR.drawable.ic_stat_flowfolio)
            .setContentTitle(playing.displayTitle)
            .setContentText(status(playing))
            .setLargeIcon(bitmap)
            .setContentIntent(openApp())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(action(R.drawable.ic_narrator_previous, R.string.narrator_previous, ACTION_PREVIOUS))
            .addAction(playPauseAction(playing))
            .addAction(action(R.drawable.ic_narrator_next, R.string.narrator_next, ACTION_NEXT))
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_narrator_speed),
                    getString(R.string.narrator_speed_action, speedLabel(playing.rate)),
                    command(ACTION_SPEED)
                ).build()
            )
            .setStyle(
                Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2)
            )
            .build()

    /** FlowFolio's own player in the notifications, with the speed and close the phone's player lacks. */
    private fun playerNotification(playing: NowPlaying, bitmap: Bitmap): Notification {
        val small = RemoteViews(packageName, R.layout.notification_narrator).also { bind(it, playing) }
        val big = RemoteViews(packageName, R.layout.notification_narrator_big).also {
            bind(it, playing)
            it.setImageViewBitmap(R.id.narrator_art, bitmap)
            it.setTextViewText(R.id.narrator_status, status(playing))
            it.setOnClickPendingIntent(R.id.narrator_close, command(ACTION_CLOSE))
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(DataR.drawable.ic_stat_flowfolio)
            .setContentTitle(playing.displayTitle)
            .setContentText(status(playing))
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setContentIntent(openApp())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setGroup(PLAYER_GROUP)
            .build()
    }

    private fun bind(views: RemoteViews, playing: NowPlaying) {
        views.setTextViewText(R.id.narrator_title, playing.displayTitle)
        views.setImageViewResource(
            R.id.narrator_play,
            if (playing.playing) R.drawable.ic_narrator_pause else R.drawable.ic_narrator_play
        )
        views.setContentDescription(
            R.id.narrator_play,
            getString(if (playing.playing) R.string.narrator_pause else R.string.narrator_play)
        )
        views.setTextViewText(R.id.narrator_speed, speedLabel(playing.rate))
        views.setOnClickPendingIntent(R.id.narrator_previous, command(ACTION_PREVIOUS))
        views.setOnClickPendingIntent(R.id.narrator_play, command(ACTION_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.narrator_next, command(ACTION_NEXT))
        views.setOnClickPendingIntent(R.id.narrator_speed, command(ACTION_SPEED))
    }

    private fun status(playing: NowPlaying): String = getString(
        if (playing.playing) R.string.narrator_status else R.string.narrator_status_paused,
        speedLabel(playing.rate)
    )

    private fun playPauseAction(playing: NowPlaying): Notification.Action = if (playing.playing) {
        action(R.drawable.ic_narrator_pause, R.string.narrator_pause, ACTION_PLAY_PAUSE)
    } else {
        action(R.drawable.ic_narrator_play, R.string.narrator_play, ACTION_PLAY_PAUSE)
    }

    private fun action(icon: Int, label: Int, command: String): Notification.Action = Notification.Action.Builder(
        android.graphics.drawable.Icon.createWithResource(this, icon),
        getString(label),
        command(command)
    ).build()

    private fun command(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, NarratorService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Back to the app as it was left (the reader, on the page being read). */
    private fun openApp(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    /** The CPU stays awake for the voice while it speaks with the screen off; released the moment it pauses. */
    private fun holdWakeLock(playing: Boolean) {
        if (playing && wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FlowFolio:narrator")
                .apply {
                    setReferenceCounted(false)
                    acquire(WAKE_LOCK_MS)
                }
        } else if (!playing) {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
    }

    private fun stop() {
        holdWakeLock(false)
        notifications.cancel(PLAYER_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        inForeground = false
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        holdWakeLock(false)
        notifications.cancel(PLAYER_ID)
        session.release()
        art?.second?.recycle()
        art = null
        thumb?.recycle()
        thumb = null
        super.onDestroy()
    }

    /** The phone's player, a headset's buttons and Bluetooth controls arrive here. */
    private inner class Controls : MediaSession.Callback() {
        override fun onPlay() {
            NarrationHub.commands?.play()
        }

        override fun onPause() {
            NarrationHub.commands?.pause()
        }

        override fun onSkipToNext() {
            NarrationHub.commands?.next()
        }

        override fun onSkipToPrevious() {
            NarrationHub.commands?.previous()
        }

        override fun onStop() {
            NarrationHub.commands?.pause()
        }

        override fun onCustomAction(action: String, extras: android.os.Bundle?) {
            if (action == CUSTOM_SPEED) NarrationHub.commands?.cycleSpeed()
        }
    }

    companion object {
        private const val CHANNEL = "narrator"
        private const val MEDIA_ID = 4201
        private const val PLAYER_ID = 4202
        private const val PLAYER_GROUP = "narrator_player"
        private const val THUMB_SIZE = 256
        private const val CUSTOM_SPEED = "com.flowfolio.narrator.SPEED"
        private const val ACTION_PLAY_PAUSE = "com.flowfolio.narrator.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.flowfolio.narrator.NEXT"
        private const val ACTION_PREVIOUS = "com.flowfolio.narrator.PREVIOUS"
        private const val ACTION_SPEED = "com.flowfolio.narrator.SPEED_STEP"
        private const val ACTION_CLOSE = "com.flowfolio.narrator.CLOSE"

        /** A safety limit on the wake lock; renewed with every update while the voice speaks. */
        private const val WAKE_LOCK_MS = 60L * 60 * 1000

        /** Starts the service for the reader that just began narrating (the app is in front: allowed). */
        internal fun start(context: Context) {
            context.startForegroundService(Intent(context, NarratorService::class.java))
        }
    }
}
