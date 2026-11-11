package com.flowfolio.feature.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGhostButton
import com.flowfolio.core.designsystem.FlowFolioGlyph
import com.flowfolio.core.designsystem.FlowFolioIconButton
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Glyph
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.appBackground
import com.flowfolio.core.designsystem.softShadow
import com.flowfolio.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One page of the tour: a picture of the thing itself, what it is, and where to find it.
 *
 * The pictures are real screenshots of this build, not drawings of it. A tour that shows something the app does not
 * look like teaches the wrong thing, and is worse than no tour at all; these are retaken whenever the screens change.
 */
internal enum class Tour(@DrawableRes val shot: Int, @StringRes val title: Int, @StringRes val body: Int) {
    Library(R.drawable.tour_library, R.string.tour_library_title, R.string.tour_library_body),
    Reading(R.drawable.tour_reading, R.string.tour_reading_title, R.string.tour_reading_body),
    Riffle(R.drawable.tour_riffle, R.string.tour_riffle_title, R.string.tour_riffle_body),
    Flow(R.drawable.tour_flow, R.string.tour_flow_title, R.string.tour_flow_body),
    Marking(R.drawable.tour_marking, R.string.tour_marking_title, R.string.tour_marking_body),
    Voice(R.drawable.tour_voice, R.string.tour_voice_title, R.string.tour_voice_body),
    Words(R.drawable.tour_words, R.string.tour_words_title, R.string.tour_words_body),
    Listen(R.drawable.tour_listen, R.string.tour_listen_title, R.string.tour_listen_body),
    Atmosphere(R.drawable.tour_atmosphere, R.string.tour_atmosphere_title, R.string.tour_atmosphere_body),
    Goal(R.drawable.tour_goal, R.string.tour_goal_title, R.string.tour_goal_body),
    Yours(R.drawable.tour_yours, R.string.tour_yours_title, R.string.tour_yours_body)
}

/** What the app remembers about having given the tour; cleared by "Introduce features again". */
const val TOUR_SEEN = "tour"

/**
 * The tour FlowFolio gives itself the first time it opens (owner, M5 round 4).
 *
 * Swipe or press Next; Skip leaves at any point and is on screen the whole way through, because a tour you cannot
 * leave is a wall. Either way it is marked seen and never appears again on its own -- Settings has it under
 * "Introduce features again" for anyone who wants it back.
 */
@Composable
internal fun OnboardingTour(onDone: () -> Unit) {
    val p = FlowFolio.palette
    val pages = Tour.entries
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Column(
        Modifier
            .fillMaxSize()
            .appBackground(p)
            .systemBarsPadding()
            .testTag(LibraryTestTags.TOUR),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Spacer(Modifier.weight(1f))
            // The last slide's own button starts reading; Skip would say the same thing twice.
            if (!last) {
                FlowFolioGhostButton(
                    text = stringResource(R.string.tour_skip),
                    onClick = onDone,
                    modifier = Modifier.testTag(LibraryTestTags.TOUR_SKIP)
                )
            } else {
                Spacer(Modifier.height(SKIP_HEIGHT))
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            TourPage(pages[page])
        }
        Text(
            stringResource(R.string.tour_step, pager.currentPage + 1, pages.size),
            style = FlowFolioType.eyebrow.copy(color = p.accent),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Dots(pages.size, pager.currentPage) { scope.launch { pager.animateScrollToPage(it) } }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pager.currentPage > 0) {
                val back = stringResource(R.string.tour_back)
                FlowFolioIconButton(
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                    tooltip = back
                ) { FlowFolioGlyph(Glyph.Back, back, color = p.ink) }
            }
            FlowFolioPrimaryButton(
                text = stringResource(if (last) R.string.tour_start else R.string.tour_next),
                onClick = {
                    if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                modifier = Modifier.testTag(LibraryTestTags.TOUR_NEXT)
            )
        }
    }
}

/**
 * The screenshot and the words: stacked when folded, side by side when unfolded (ADR-0052), where a stacked page
 * would leave the picture small in a wide frame. The picture gets the room because it is what is being explained.
 */
@Composable
private fun TourPage(tour: Tour) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TWO_COLUMNS) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1.2f), contentAlignment = Alignment.Center) { Shot(tour, Modifier) }
                Column(Modifier.weight(1f)) { Words(tour, TextAlign.Start) }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Shot(tour, Modifier.weight(1f, fill = false))
                Words(tour, TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Shot(tour: Tour, modifier: Modifier) {
    val p = FlowFolio.palette
    val shape = RoundedCornerShape(20.dp)
    Image(
        painter = painterResource(tour.shot),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            // Hugs the picture rather than the space it was given, so the frame is drawn around the screenshot
            // and not around a band of nothing above and below it.
            .aspectRatio(SHOT_ASPECT)
            .softShadow(shape, p.ink.copy(alpha = SHOT_GLOW), blur = 28.dp)
            .clip(shape)
            .border(1.dp, p.edge, shape)
    )
}

@Composable
private fun Words(tour: Tour, align: TextAlign) {
    Text(
        stringResource(tour.title),
        style = FlowFolioType.sectionTitle.merge(FlowFolio.title),
        textAlign = align,
        modifier = Modifier.padding(top = 20.dp)
    )
    Text(
        stringResource(tour.body),
        style = FlowFolioType.sub.merge(FlowFolio.hint),
        textAlign = align,
        modifier = Modifier.widthIn(max = BODY_WIDTH).padding(top = 8.dp)
    )
}

/** Where you are in the tour, and how much is left. */
@Composable
private fun Dots(count: Int, current: Int, onPick: (Int) -> Unit) {
    val p = FlowFolio.palette
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val here = i == current
            val width by animateFloatAsState(
                if (here) DOT_WIDE else DOT,
                androidx.compose.animation.core.tween(Motion.FAST_MS, easing = Motion.EaseOut),
                label = "dot"
            )
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable { onPick(i) }
                    .height(DOT.dp)
                    .widthIn(min = width.dp)
                    .size(width.dp, DOT.dp)
                    .clip(CircleShape)
                    .background(if (here) p.ink else p.ink.copy(alpha = DOT_DIM), CircleShape)
            )
        }
    }
}

private val BODY_WIDTH = 440.dp
private val TWO_COLUMNS = 600.dp
private val SKIP_HEIGHT = 42.dp

/**
 * The reference device's unfolded screen, which every tour picture was taken on, less the status bar and the gesture
 * bar (72 px each), which the pictures leave out (owner, 2026-11-10).
 */
private const val SHOT_ASPECT = 2256f / 2360f

private const val SHOT_GLOW = 0.22f
private const val DOT = 8f
private const val DOT_WIDE = 24f
private const val DOT_DIM = 0.3f

/**
 * Holds the tour over the whole app until it has been given once.
 *
 * It sits in the shell rather than in the library, because a tour with the app's own tab bar showing through it is
 * not a first impression. Nothing is drawn until the settings have actually loaded, so a reader who saw the tour
 * months ago never sees it flash past on the way in.
 */
@Composable
fun OnboardingHost(viewModel: OnboardingViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val seen = settings?.seenGuides ?: return
    if (TOUR_SEEN in seen) return
    OnboardingTour(onDone = viewModel::finish)
}

/** The tour's own small piece of state: whether it is still owed, and marking it given. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<UserSettings?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    /** Marks the tour given, and the old one-line welcome with it, so the two never follow each other. */
    fun finish() {
        viewModelScope.launch {
            repo.markGuideSeen(TOUR_SEEN)
            repo.markGuideSeen(Guide.Welcome.id)
        }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
