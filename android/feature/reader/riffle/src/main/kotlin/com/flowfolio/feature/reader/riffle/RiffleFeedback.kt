package com.flowfolio.feature.reader.riffle

import kotlin.math.abs

/** The haptic for a step of a riffle (ADR-0023 maps each to a system constant per API level). */
enum class RiffleHaptic { TICK, CHAPTER, FLAG, SPEED }

/**
 * What one step of a drag sounds and feels like: the haptic (or none), the [units] passed (capped), whether a chapter
 * start was crossed, and whether the step went toward the end of the book.
 */
data class RiffleFeedback(val haptic: RiffleHaptic?, val units: Int, val chapter: Boolean, val forward: Boolean = true)

/**
 * Feedback for the previewed unit moving from one unit to another during a drag (spec 5.6): a chapter start crossed
 * outranks a bookmark, which outranks the light tick every [TICK_EVERY] units. A start is crossed when it lies in
 * (low, high], whichever way the finger moves. Only the reader's own drags get feedback; the caller decides that.
 */
class FeedbackRules(chapterStarts: Collection<Int>, flagUnits: Collection<Int>) {
    private val chapters = chapterStarts.toSortedSet()
    private val flags = flagUnits.toSortedSet()

    fun between(from: Int, to: Int): RiffleFeedback {
        if (from == to) return RiffleFeedback(null, 0, false)
        val low = minOf(from, to)
        val high = maxOf(from, to)
        val chapter = chapters.subSet(low + 1, high + 1).isNotEmpty()
        val flag = flags.subSet(low + 1, high + 1).isNotEmpty()
        val haptic = when {
            chapter -> RiffleHaptic.CHAPTER
            flag -> RiffleHaptic.FLAG
            Math.floorDiv(from, TICK_EVERY) != Math.floorDiv(to, TICK_EVERY) -> RiffleHaptic.TICK
            else -> null
        }
        return RiffleFeedback(haptic, minOf(MAX_UNITS, abs(to - from)), chapter, to > from)
    }

    companion object {
        const val TICK_EVERY = 4

        /** Units counted per step at most; fast riffles skip many units in one frame. */
        const val MAX_UNITS = 6
    }
}

/** A run of a section's sheets, coloured on the fore edge at band [band] (spec 4.1). */
data class BleedBand(val firstSheet: Int, val endSheet: Int, val band: Int, val section: Section)

object BleedBands {
    const val BANDS = 8

    /** Each section's sheets, stepping down one band per section and starting again after [BANDS]. */
    fun of(sections: List<Section>, mapping: StripMapping): List<BleedBand> = sections.mapIndexed { i, s ->
        val end = sections.getOrNull(i + 1)?.let { mapping.sheetOf(it.firstPage) } ?: mapping.sheetCount
        BleedBand(mapping.sheetOf(s.firstPage), maxOf(end, mapping.sheetOf(s.firstPage) + 1), i % BANDS, s)
    }
}
