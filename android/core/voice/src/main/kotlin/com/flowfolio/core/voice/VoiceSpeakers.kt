package com.flowfolio.core.voice

/** A Narrator voice: a Kokoro speaker (its integer id in the model) with a stable name and a friendly label. */
data class Voice(val id: Int, val name: String) {
    val label: String = labelOf(name)
}

/**
 * The voices Narrator Mode offers, all speakers of the `kokoro-multi-lang-v1_0` model (one download, 53
 * speakers). The two defaults come first, then the rest of the English speakers, then the other languages.
 */
object VoiceSpeakers {
    val defaults: List<Voice> = listOf(Voice(id = 3, name = "af_heart"), Voice(id = 16, name = "am_michael"))

    val all: List<Voice> = listOf(
        Voice(0, "af_alloy"),
        Voice(1, "af_aoede"),
        Voice(2, "af_bella"),
        Voice(3, "af_heart"),
        Voice(4, "af_jessica"),
        Voice(5, "af_kore"),
        Voice(6, "af_nicole"),
        Voice(7, "af_nova"),
        Voice(8, "af_river"),
        Voice(9, "af_sarah"),
        Voice(10, "af_sky"),
        Voice(11, "am_adam"),
        Voice(12, "am_echo"),
        Voice(13, "am_eric"),
        Voice(14, "am_fenrir"),
        Voice(15, "am_liam"),
        Voice(16, "am_michael"),
        Voice(17, "am_onyx"),
        Voice(18, "am_puck"),
        Voice(19, "am_santa"),
        Voice(20, "bf_alice"),
        Voice(21, "bf_emma"),
        Voice(22, "bf_isabella"),
        Voice(23, "bf_lily"),
        Voice(24, "bm_daniel"),
        Voice(25, "bm_fable"),
        Voice(26, "bm_george"),
        Voice(27, "bm_lewis"),
        Voice(28, "ef_dora"),
        Voice(29, "em_alex"),
        Voice(30, "ff_siwis"),
        Voice(31, "hf_alpha"),
        Voice(32, "hf_beta"),
        Voice(33, "hm_omega"),
        Voice(34, "hm_psi"),
        Voice(35, "if_sara"),
        Voice(36, "im_nicola"),
        Voice(37, "jf_alpha"),
        Voice(38, "jf_gongitsune"),
        Voice(39, "jf_nezumi"),
        Voice(40, "jf_tebukuro"),
        Voice(41, "jm_kumo"),
        Voice(42, "pf_dora"),
        Voice(43, "pm_alex"),
        Voice(44, "pm_santa"),
        Voice(45, "zf_xiaobei"),
        Voice(46, "zf_xiaoni"),
        Voice(47, "zf_xiaoxiao"),
        Voice(48, "zf_xiaoyi"),
        Voice(49, "zm_yunjian"),
        Voice(50, "zm_yunxi"),
        Voice(51, "zm_yunxia"),
        Voice(52, "zm_yunyang")
    )

    /** The Kokoro speaker id for [name], or null if it is not a known voice. */
    fun idOf(name: String): Int? = all.firstOrNull { it.name == name }?.id
}

/** A friendly picker label: `af_heart` -> "Heart (US · female)", `zf_xiaobei` -> "Xiaobei". */
private fun labelOf(name: String): String {
    val base = name.substringAfter('_', name).replaceFirstChar { it.uppercase() }
    val hint = LANGUAGE_HINTS[name.substringBefore('_', "")]
    return if (hint != null) "$base ($hint)" else base
}

private val LANGUAGE_HINTS = mapOf(
    "af" to "US · female",
    "am" to "US · male",
    "bf" to "UK · female",
    "bm" to "UK · male",
    "ef" to "English · female",
    "em" to "English · male",
    "ff" to "French · female",
    "hf" to "Hindi · female",
    "hm" to "Hindi · male",
    "if" to "Italian · female",
    "im" to "Italian · male",
    "jf" to "Japanese · female",
    "jm" to "Japanese · male",
    "pf" to "Portuguese · female",
    "pm" to "Portuguese · male",
    "zf" to "Chinese · female",
    "zm" to "Chinese · male"
)
