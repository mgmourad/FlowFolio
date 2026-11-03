package com.flowfolio.core.model

/** The period a library reading goal is measured over (owner, M3T3). */
enum class GoalPeriod { DAY, WEEK, MONTH }

/**
 * A reading goal (owner, M3T3): for the whole library ([scope] is [LIBRARY_SCOPE]) or for one book ([scope] is the
 * book's id). [targetMinutes] is the goal's length; [remindDaysMask] has bit `0` = Monday .. bit `6` = Sunday.
 */
data class ReadingGoal(
    val scope: String = LIBRARY_SCOPE,
    val targetMinutes: Int = DEFAULT_MINUTES,
    val period: GoalPeriod = GoalPeriod.DAY,
    val remindEnabled: Boolean = false,
    val remindHour: Int = DEFAULT_REMIND_HOUR,
    val remindMinute: Int = 0,
    val remindDaysMask: Int = ALL_DAYS_MASK,
    val updatedAtMillis: Long = 0L
) {
    val isLibraryGoal: Boolean get() = scope == LIBRARY_SCOPE

    val targetMillis: Long get() = targetMinutes.toLong() * MS_PER_MINUTE

    fun progress(readMillis: Long): Float =
        if (targetMillis > 0L) (readMillis.toFloat() / targetMillis.toFloat()).coerceIn(0f, 1f) else 0f

    fun exceeded(readMillis: Long): Boolean = targetMillis > 0L && readMillis > targetMillis

    fun isDayEnabled(dayIndexMondayZero: Int): Boolean = (remindDaysMask and (1 shl dayIndexMondayZero)) != 0

    companion object {
        const val LIBRARY_SCOPE = ""
        const val DEFAULT_MINUTES = 30
        const val DEFAULT_REMIND_HOUR = 20
        const val ALL_DAYS_MASK = 0b1111111
        private const val MS_PER_MINUTE = 60_000L
    }
}
