@file:Suppress("ReturnCount", "MagicNumber")

package com.flowfolio.core.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.flowfolio.core.data.R
import com.flowfolio.core.database.BookDao
import com.flowfolio.core.database.ReadingGoalDao
import com.flowfolio.core.database.ReadingGoalEntity
import com.flowfolio.core.database.ReadingSessionDao
import com.flowfolio.core.model.GoalPeriod
import com.flowfolio.core.model.ReadingGoal
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reading goals for the whole library or for individual books, and their reminders (owner, M3T3). */
interface ReadingGoalRepository {
    fun observeLibraryGoal(): Flow<ReadingGoal?> = flowOf(null)

    fun observeBookGoal(bookId: String): Flow<ReadingGoal?> = flowOf(null)

    fun observeAllGoals(): Flow<List<ReadingGoal>> = flowOf(emptyList())

    fun observePeriodReadMillis(period: GoalPeriod): Flow<Long> = flowOf(0L)

    suspend fun saveGoal(goal: ReadingGoal) {}

    suspend fun resetGoal(scope: String) {}

    /** Days in a row with some reading, up to today (or yesterday, when today has none yet). */
    suspend fun streakDays(): Int = 0
}

@Singleton
class DefaultReadingGoalRepository @Inject constructor(
    private val dao: ReadingGoalDao,
    private val sessions: ReadingSessionDao,
    private val scheduler: GoalReminderScheduler,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher
) : ReadingGoalRepository {
    override fun observeLibraryGoal(): Flow<ReadingGoal?> = dao.observe(ReadingGoal.LIBRARY_SCOPE).map { it?.toModel() }

    override fun observeBookGoal(bookId: String): Flow<ReadingGoal?> = dao.observe(bookId).map { it?.toModel() }

    override fun observeAllGoals(): Flow<List<ReadingGoal>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observePeriodReadMillis(period: GoalPeriod): Flow<Long> =
        sessions.observeLibraryTotalSince(periodStartMillis(period, clock.now()))

    override suspend fun saveGoal(goal: ReadingGoal) = withContext(io) {
        val stamped = goal.copy(updatedAtMillis = clock.now())
        dao.upsert(stamped.toEntity())
        scheduler.scheduleOrCancel(stamped)
    }

    override suspend fun resetGoal(scope: String) = withContext(io) {
        dao.delete(scope)
        scheduler.cancel(scope)
    }

    override suspend fun streakDays(): Int = withContext(io) {
        val now = clock.now()
        val since = now - ReadingStreak.LOOKBACK_DAYS * ReadingStreak.DAY_MS
        ReadingStreak.days(sessions.sessionsSince(since).map { it.startedAt to it.endedAt }, now)
    }

    companion object {
        internal fun periodStartMillis(period: GoalPeriod, nowMillis: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            when (period) {
                GoalPeriod.DAY -> Unit

                GoalPeriod.WEEK -> {
                    val dow = cal.get(Calendar.DAY_OF_WEEK)
                    val daysSinceMonday = (dow + 5) % 7
                    cal.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
                }

                GoalPeriod.MONTH -> cal.set(Calendar.DAY_OF_MONTH, 1)
            }
            return cal.timeInMillis
        }
    }
}

internal fun ReadingGoalEntity.toModel(): ReadingGoal = ReadingGoal(
    scope = scope,
    targetMinutes = targetMinutes,
    period = runCatching { GoalPeriod.valueOf(period) }.getOrDefault(GoalPeriod.DAY),
    remindEnabled = remindEnabled,
    remindHour = remindHour,
    remindMinute = remindMinute,
    remindDaysMask = remindDaysMask,
    updatedAtMillis = updatedAt
)

internal fun ReadingGoal.toEntity(): ReadingGoalEntity = ReadingGoalEntity(
    scope = scope,
    targetMinutes = targetMinutes,
    period = period.name,
    remindEnabled = remindEnabled,
    remindHour = remindHour,
    remindMinute = remindMinute,
    remindDaysMask = remindDaysMask,
    updatedAt = updatedAtMillis
)

/**
 * Schedules reading-goal reminders with Android's own [AlarmManager] (owner, M3T3; no new dependency).
 */
@Singleton
class GoalReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock
) {
    fun scheduleOrCancel(goal: ReadingGoal) {
        if (!goal.remindEnabled || goal.remindDaysMask == 0) {
            cancel(goal.scope)
            return
        }
        val nextAt = nextTriggerMillis(clock.now(), goal.remindHour, goal.remindMinute, goal.remindDaysMask) ?: return
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pendingIntent(goal.scope))
    }

    fun cancel(scope: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarm.cancel(pendingIntent(scope))
    }

    private fun pendingIntent(scope: String): PendingIntent {
        val intent = Intent(context, GoalReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_SCOPE, scope)
        }
        return PendingIntent.getBroadcast(
            context,
            scope.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_REMIND = "com.flowfolio.action.GOAL_REMIND"
        const val EXTRA_SCOPE = "scope"
        private const val DAYS_IN_WEEK = 7

        internal fun nextTriggerMillis(nowMillis: Long, hour: Int, minute: Int, daysMask: Int): Long? {
            if (daysMask == 0) return null
            for (offset in 0..DAYS_IN_WEEK) {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, offset)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val dow = cal.get(Calendar.DAY_OF_WEEK)
                val mondayZero = (dow + 5) % DAYS_IN_WEEK
                val enabled = (daysMask and (1 shl mondayZero)) != 0
                if (enabled && cal.timeInMillis > nowMillis) {
                    return cal.timeInMillis
                }
            }
            return null
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GoalReminderEntryPoint {
    fun goalDao(): ReadingGoalDao
    fun bookDao(): BookDao
    fun scheduler(): GoalReminderScheduler
}

/** Fires a reminder notification ("Time to read <book title>" + quote) and schedules the next day's alarm. */
class GoalReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val scope = intent?.getStringExtra(GoalReminderScheduler.EXTRA_SCOPE) ?: return
        val pending = goAsync()
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, GoalReminderEntryPoint::class.java)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val entity = entry.goalDao().observe(scope).first() ?: return@launch
                val goal = entity.toModel()
                if (!goal.remindEnabled) return@launch
                val bookTitle = if (scope.isNotEmpty()) {
                    entry.bookDao().observe(scope).first()?.title
                } else {
                    entry.bookDao().observeByRecent().first()
                        .firstOrNull { it.lastPage != null && it.finishedAt == null }?.title
                        ?: entry.bookDao().observeByRecent().first().firstOrNull()?.title
                }
                postNotification(context, scope, bookTitle)
                entry.scheduler().scheduleOrCancel(goal)
            } finally {
                pending.finish()
            }
        }
    }

    private fun postNotification(context: Context, scope: String, bookTitle: String?) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reading Goal Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)
        val title = if (!bookTitle.isNullOrBlank()) "Time to read $bookTitle" else "Time to read"
        val quote = ReadingQuotes.pick((System.currentTimeMillis() / 60_000L).toInt() + scope.hashCode())
        val body = "“${quote.quote}” — ${quote.author}"
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(
                context,
                scope.hashCode(),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flowfolio)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_BASE_ID + (scope.hashCode() and 0xFFF), notification)
    }

    companion object {
        const val CHANNEL_ID = "flowfolio_reading_goals"
        private const val NOTIFICATION_BASE_ID = 4200
    }
}

/** Restores reading-goal reminder alarms after a phone restart (owner, M3T3). */
class GoalBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, GoalReminderEntryPoint::class.java)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                entry.goalDao().getAll().map { it.toModel() }.forEach { goal ->
                    entry.scheduler().scheduleOrCancel(goal)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
