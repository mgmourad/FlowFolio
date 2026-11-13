package com.flowfolio.feature.library

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flowfolio.core.data.Resource
import com.flowfolio.core.data.ResourceDownloads
import com.flowfolio.core.data.ResourceState
import com.flowfolio.core.data.SettingsRepository
import com.flowfolio.core.designsystem.FlowFolio
import com.flowfolio.core.designsystem.FlowFolioGhostButton
import com.flowfolio.core.designsystem.FlowFolioPrimaryButton
import com.flowfolio.core.designsystem.FlowFolioProgressBar
import com.flowfolio.core.designsystem.FlowFolioToggle
import com.flowfolio.core.designsystem.FlowFolioType
import com.flowfolio.core.designsystem.Motion
import com.flowfolio.core.designsystem.card
import com.flowfolio.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The guide id that remembers the offer was made; forgetting it (Settings → Storage) shows it again. */
const val RESOURCES_OFFERED = "resources_offer"

/**
 * The first-launch offer of optional downloads (TM2, owner 2026-11-12), its own window rather than a tour slide,
 * because most people skip the tour. It comes once, after the tour is done or skipped, and again from Settings →
 * Storage.
 */
@Composable
fun ResourcesOfferHost(viewModel: ResourcesOfferViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val seen = settings?.seenGuides
    val states = Resource.entries.map { viewModel.state(it).collectAsStateWithLifecycle().value }
    when {
        seen == null || TOUR_SEEN !in seen || RESOURCES_OFFERED in seen -> Unit

        // The Full edition carries every pack: the offer is made, quietly.
        viewModel.bundled -> LaunchedEffect(Unit) { viewModel.close() }

        // Nothing left to offer (all three already on the phone): the offer is made, quietly.
        states.all { it == ResourceState.Ready } -> LaunchedEffect(Unit) { viewModel.close() }

        else -> ResourcesOffer(viewModel)
    }
}

/** What the offer shows and does. */
@HiltViewModel
class ResourcesOfferViewModel @Inject constructor(
    private val downloads: ResourceDownloads,
    private val repo: SettingsRepository
) : ViewModel() {
    val settings: StateFlow<UserSettings?> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    val bundled: Boolean get() = downloads.bundled

    fun state(resource: Resource): StateFlow<ResourceState> = downloads.state(resource)

    fun sizeOf(resource: Resource): Long = downloads.sizeOf(resource)

    fun download(chosen: Set<Resource>) = downloads.download(chosen)

    /** The offer was answered (downloads keep going in the background). */
    fun close() {
        viewModelScope.launch { repo.markGuideSeen(RESOURCES_OFFERED) }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}

/** One optional download: its picture, words and resource. */
private class Offer(val resource: Resource, val picture: Int, val title: Int, val text: Int)

private val OFFERS = listOf(
    Offer(Resource.SCANNED_BOOKS, R.drawable.tour_flow, R.string.resources_scanned, R.string.resources_scanned_text),
    Offer(Resource.DICTIONARY, R.drawable.tour_words, R.string.resources_dictionary, R.string.resources_dictionary_text)
)

@Composable
private fun ResourcesOffer(viewModel: ResourcesOfferViewModel) {
    val p = FlowFolio.palette
    val context = LocalContext.current
    var chosen by rememberSaveable { mutableStateOf(Resource.entries.map { it.name }.toSet()) }
    var started by rememberSaveable { mutableStateOf(false) }
    // Each download's state, collected (not read from the flow) so the total follows it.
    val states = Resource.entries.associateWith { viewModel.state(it).collectAsStateWithLifecycle().value }
    Dialog(onDismissRequest = viewModel::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = MAX_WIDTH)
                    .fillMaxWidth()
                    .card(RoundedCornerShape(28.dp))
                    .background(p.surface, RoundedCornerShape(28.dp))
                    .padding(22.dp)
                    .testTag(LibraryTestTags.RESOURCES_OFFER)
            ) {
                Text(stringResource(R.string.resources_title), style = FlowFolioType.heroTitle.copy(color = p.ink))
                Text(
                    stringResource(R.string.resources_subtitle),
                    style = FlowFolioType.body.copy(color = p.ink2),
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OFFERS.forEachIndexed { i, offer ->
                        val state = states.getValue(offer.resource)
                        OfferCard(
                            offer = offer,
                            order = i,
                            size = Formatter.formatShortFileSize(context, viewModel.sizeOf(offer.resource)),
                            on = offer.resource.name in chosen,
                            state = state,
                            started = started
                        ) { on -> chosen = if (on) chosen + offer.resource.name else chosen - offer.resource.name }
                    }
                }
                val total = Resource.entries
                    .filter { it.name in chosen && states[it] != ResourceState.Ready }
                    .sumOf { viewModel.sizeOf(it) }
                OfferFooter(
                    note = if (started) {
                        stringResource(R.string.resources_background)
                    } else {
                        stringResource(R.string.resources_total, Formatter.formatShortFileSize(context, total))
                    },
                    started = started,
                    onClose = viewModel::close,
                    onDownload = {
                        viewModel.download(Resource.entries.filter { it.name in chosen }.toSet())
                        started = true
                    }
                )
            }
        }
    }
}

/** The total (or that downloads go on in the background) and the offer's buttons. */
@Composable
private fun OfferFooter(note: String, started: Boolean, onClose: () -> Unit, onDownload: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            note,
            style = FlowFolioType.rowDetail.copy(color = FlowFolio.palette.ink3),
            modifier = Modifier.weight(1f)
        )
        if (started) {
            FlowFolioPrimaryButton(stringResource(R.string.resources_done), onClose)
        } else {
            FlowFolioGhostButton(stringResource(R.string.resources_later), onClose)
            Spacer(Modifier.width(8.dp))
            FlowFolioPrimaryButton(stringResource(R.string.resources_download), onClick = onDownload)
        }
    }
}

/** One download as a card: its screenshot, what it adds, its size and a switch; its progress once started. */
@Composable
private fun OfferCard(
    offer: Offer,
    order: Int,
    size: String,
    on: Boolean,
    state: ResourceState,
    started: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val p = FlowFolio.palette
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(order * STAGGER_MS)
        shown = true
    }
    AnimatedVisibility(
        shown,
        enter = fadeIn(tween(Motion.MEDIUM_MS)) +
            slideInVertically(tween(Motion.RISE_MS, easing = Motion.Rise)) { it / 3 }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(p.bg2)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painterResource(offer.picture),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(PICTURE_WIDTH, PICTURE_HEIGHT)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.05f))
            )
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(stringResource(offer.title), style = FlowFolioType.rowLabel.copy(color = p.ink))
                Text(
                    stringResource(offer.text),
                    style = FlowFolioType.rowDetail.copy(color = p.ink2),
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    statusText(state, size),
                    style = FlowFolioType.rowDetail.copy(color = p.ink3),
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (state is ResourceState.Downloading) {
                    FlowFolioProgressBar(
                        state.fraction,
                        Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(max = 6.dp)
                    )
                }
            }
            if (!started && state != ResourceState.Ready) FlowFolioToggle(on, onToggle)
        }
    }
}

@Composable
private fun statusText(state: ResourceState, size: String): String = when (state) {
    ResourceState.Missing -> size
    is ResourceState.Downloading -> stringResource(R.string.resources_downloading, (state.fraction * PERCENT).toInt())
    ResourceState.Ready -> stringResource(R.string.resources_ready)
    ResourceState.Failed -> stringResource(R.string.resources_failed)
}

private val MAX_WIDTH = 560.dp
private val PICTURE_WIDTH = 88.dp
private val PICTURE_HEIGHT = 92.dp
private const val STAGGER_MS = 110L
private const val PERCENT = 100
