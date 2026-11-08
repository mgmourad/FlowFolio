package com.flowfolio.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Wikipedia's opening summary for a term (owner, 2026-11-07): the REST summary API's `extract`, CC BY-SA 3.0. It is
 * fetched online and never cached, so a network-free reader still falls back to Wiktionary and WordNet, and specialized
 * terms that are not in a dictionary ("kubernetes", "vram") get their first sentence here.
 */
@Singleton
class WikipediaStore @Inject constructor(private val http: HttpGet, @IoDispatcher private val io: CoroutineDispatcher) {
    suspend fun summary(title: String): String? = withContext(io) {
        val clean = title.trim()
        if (clean.isEmpty()) return@withContext null
        val url = "https://en.wikipedia.org/api/rest_v1/page/summary/" + java.net.URLEncoder.encode(clean, "UTF-8")
        val body = http.get(url) ?: return@withContext null
        parseSummary(body)
    }

    private fun parseSummary(json: String): String? = runCatching {
        JSONObject(json).optString("extract").takeIf { it.isNotBlank() }
    }.getOrNull()
}
