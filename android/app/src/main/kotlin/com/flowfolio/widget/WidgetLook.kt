package com.flowfolio.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.flowfolio.MainActivity
import com.flowfolio.core.data.LibraryRepository
import com.flowfolio.core.data.ReadingGoalRepository
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.data.flow.FlowNotifications
import com.flowfolio.core.designsystem.Palette
import com.flowfolio.core.designsystem.paletteFamilyOf
import com.flowfolio.core.model.ThemePreference
import com.flowfolio.core.model.UserSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What the widgets read, from Hilt's graph (a widget is not an Android component Hilt injects). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun library(): LibraryRepository

    fun goals(): ReadingGoalRepository

    fun settings(): SettingsRepository
}

internal fun Context.widgetGraph(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/**
 * The app's colours for a widget (TM2: widgets follow the app's theme). With the theme on "System" each colour
 * carries its light and dark values and the launcher picks; a fixed theme gives one.
 */
internal class WidgetColors(settings: UserSettings, context: Context) {
    private val family = paletteFamilyOf(context, settings.appStyle, settings.materialSeed)
    private val mode = settings.theme

    private fun pick(of: (Palette) -> Color): ColorProvider = when (mode) {
        ThemePreference.SYSTEM -> ColorProvider(day = of(family.light), night = of(family.dark))
        ThemePreference.LIGHT -> ColorProvider(of(family.light))
        ThemePreference.DARK -> ColorProvider(of(family.dark))
    }

    val background = pick { it.surface }
    val panel = pick { it.bg2 }
    val ink = pick { it.ink }
    val ink2 = pick { it.ink2 }
    val ink3 = pick { it.ink3 }
    val accent = pick { it.accent }
    val accentInk = pick { it.accentInk }
    val track = pick { it.line }
}

/**
 * Shows [content] for each value of [values], live while Glance keeps the widget's session (an update then only
 * recomposes, it does not call provideGlance again, so a value read once beforehand would go stale).
 */
internal suspend fun <T> GlanceAppWidget.provideLive(values: Flow<T>, content: @Composable (T) -> Unit) {
    val first = values.first()
    provideContent {
        val value by values.collectAsState(first)
        content(value)
    }
}

/** The app's colours, as the settings change. */
internal fun Context.widgetColors(): Flow<WidgetColors> =
    widgetGraph().settings().settings.map { WidgetColors(it, this) }

/** Opens the app on [bookId] where it was left; [narrate] also starts Narrator Mode there. */
internal fun openBookIntent(context: Context, bookId: String, narrate: Boolean = false): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(if (narrate) ACTION_NARRATE else ACTION_OPEN)
        .putExtra(FlowNotifications.EXTRA_OPEN_BOOK, bookId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/** Opens the app at the library. */
internal fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The widget's "narrate" action, read by MainActivity. */
const val ACTION_NARRATE = "com.flowfolio.widget.NARRATE"
private const val ACTION_OPEN = "com.flowfolio.widget.OPEN"

/** A book cover, small enough for a widget (RemoteViews carries bitmaps in a parcel). */
internal fun coverBitmap(path: String?, maxSide: Int = COVER_PX): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    path?.let { BitmapFactory.decodeFile(it, bounds) }
    val side = maxOf(bounds.outWidth, bounds.outHeight)
    if (path == null || side <= 0) return null
    var sample = 1
    while (side / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * A ring as a white mask, the whole circle or [sweep] (0..1) of it from twelve o'clock; the widget tints it, so the
 * one picture serves light and dark.
 */
internal fun ringBitmap(sweep: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(RING_PX, RING_PX, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RING_STROKE
        strokeCap = Paint.Cap.ROUND
        color = android.graphics.Color.WHITE
    }
    val inset = RING_STROKE / 2f
    val box = RectF(inset, inset, RING_PX - inset, RING_PX - inset)
    if (sweep >= 1f) {
        Canvas(bitmap).drawOval(box, paint)
    } else if (sweep > 0f) {
        Canvas(bitmap).drawArc(box, START_ANGLE, FULL_CIRCLE * sweep, false, paint)
    }
    return bitmap
}

private const val COVER_PX = 420
private const val RING_PX = 240
private const val RING_STROKE = 26f
private const val START_ANGLE = -90f
private const val FULL_CIRCLE = 360f
