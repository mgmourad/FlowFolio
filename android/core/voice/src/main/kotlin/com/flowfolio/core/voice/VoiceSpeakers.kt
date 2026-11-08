package com.flowfolio.core.voice

/** A Narrator voice: a Kokoro speaker (its integer id in the model) with a stable name. */
data class Voice(val id: Int, val name: String)

/**
 * The voices Narrator Mode offers, from the `kokoro-multi-lang-v1_0` model (one download, 53 speakers).
 * Only the two defaults are surfaced for now; the rest are free once the model is downloaded and can be
 * added behind the same picker later (Phase 6 M3, owner-approved 2026-11-08).
 */
object VoiceSpeakers {
    val defaults: List<Voice> = listOf(
        Voice(id = 3, name = "af_heart"),
        Voice(id = 16, name = "am_michael")
    )

    /** The Kokoro speaker id for [name], or null if it is not one of the default voices. */
    fun idOf(name: String): Int? = defaults.firstOrNull { it.name == name }?.id
}
