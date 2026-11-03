package com.flowfolio.core.data.flow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.flowfolio.core.data.R

/**
 * Dynamic Flow's notifications on the phone (ADR-0045): one channel, a progress notification while a book converts
 * (the foreground service's), and one when it is done or failed. Tapping one opens the book. Used from the app and from
 * the conversion process.
 */
class FlowNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.flow_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = context.getString(R.string.flow_channel_detail) }
        manager.createNotificationChannel(channel)
    }

    /** The ongoing notification: [title] converting, [percent] done, or paused for [pausedText]. */
    fun progress(bookId: String, title: String, percent: Int, pausedText: String? = null): Notification =
        builder(bookId)
            .setContentTitle(context.getString(R.string.flow_converting_title))
            .setContentText(pausedText ?: context.getString(R.string.flow_converting_text, title, percent))
            .setProgress(PERCENT, percent, percent == 0 && pausedText == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .build()

    fun showProgress(bookId: String, title: String, percent: Int, pausedText: String?) =
        show(bookId, progress(bookId, title, percent, pausedText))

    fun showDone(bookId: String, title: String) = show(
        bookId,
        builder(bookId)
            .setContentTitle(context.getString(R.string.flow_done_title))
            .setContentText(context.getString(R.string.flow_done_text, title))
            .setAutoCancel(true)
            .build()
    )

    fun showFailed(bookId: String, title: String) = show(
        bookId,
        builder(bookId)
            .setContentTitle(context.getString(R.string.flow_failed_title))
            .setContentText(context.getString(R.string.flow_failed_text, title))
            .setAutoCancel(true)
            .build()
    )

    /** Highlights, notes and bookmarks being carried into the other view, or done ([carried] of [total]). */
    fun showCarry(bookId: String, title: String, carried: Int?, total: Int) = show(
        bookId,
        builder(bookId).apply {
            if (carried == null) {
                setContentTitle(context.getString(R.string.flow_carry_title))
                setContentText(context.getString(R.string.flow_carry_text, title))
                setProgress(0, 0, true)
                setOngoing(true)
            } else {
                setContentTitle(context.getString(R.string.flow_carried_title))
                setContentText(context.getString(R.string.flow_carried_text, carried, total, title))
                setAutoCancel(true)
            }
        }.build()
    )

    fun idOf(bookId: String) = BASE_ID + (bookId.hashCode() and ID_MASK)

    private fun show(bookId: String, notification: Notification) {
        // Without the notification permission (Android 13+) this does nothing; the app's own notice still shows.
        runCatching { manager.notify(idOf(bookId), notification) }
    }

    private fun builder(bookId: String): Notification.Builder = Notification.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_flowfolio)
        .setContentIntent(openBook(bookId))

    /** Opens the app on the book (MainActivity reads [EXTRA_OPEN_BOOK]). */
    private fun openBook(bookId: String): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.putExtra(
            EXTRA_OPEN_BOOK,
            bookId
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            idOf(bookId),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val CHANNEL = "dynamic_flow"

        /** An intent extra: open this book (a Dynamic Flow notification was tapped). */
        const val EXTRA_OPEN_BOOK = "com.flowfolio.extra.OPEN_BOOK"
        private const val BASE_ID = 0x4000_0000
        private const val ID_MASK = 0x0FFF_FFFF
        private const val PERCENT = 100
    }
}
