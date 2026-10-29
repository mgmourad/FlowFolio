package com.flowfolio.core.conversion.epub

import kotlinx.serialization.Serializable

/**
 * `flowfolio.json`, the sidecar beside `book.epub` (`docs/conversion-spec.md` section 3, normative from v1.0.0). Pages
 * are 1-based physical indices; boxes are PDF points from the top left of the page as displayed.
 */
@Serializable
data class Sidecar(
    val specVersion: String,
    val bookId: String,
    val complete: Boolean,
    val source: SourceInfo,
    val pages: List<SidecarPage>,
    val blocks: List<SidecarBlock>,
    val graph: Graph,
    val health: Health,
    val recipe: Recipe
)

@Serializable
data class SourceInfo(
    val fileName: String,
    val sha256: String,
    val pageCount: Int,
    val title: String?,
    val isbn: String? = null
)

@Serializable
data class SidecarPage(
    val page: Int,
    val pageLabel: String?,
    val width: Float,
    val height: Float,
    /** `born-digital`, `scanned` or `empty`. */
    val kind: String,
    val xhtml: String
)

@Serializable
data class SidecarBlock(
    val blockId: String,
    val type: String,
    val page: Int,
    val bbox: List<Float>,
    val xhtml: String,
    val confidence: Float,
    val method: String,
    val continuesFrom: String? = null
)

/** Cross-references between blocks; Phase 5 fills it. */
@Serializable
data class Graph(val nodes: List<GraphNode> = emptyList(), val edges: List<GraphEdge> = emptyList())

@Serializable
data class GraphNode(val id: String, val kind: String, val label: String, val blockId: String)

@Serializable
data class GraphEdge(val from: String, val to: String, val kind: String)

@Serializable
data class Health(val coverageThreshold: Float, val summary: HealthSummary, val pages: List<PageHealth>)

@Serializable
data class HealthSummary(
    val pages: Int,
    val pagesBelowThreshold: Int,
    val fallbackPages: Int,
    val scannedPages: Int,
    val chapters: Int,
    val chaptersFrom: String,
    val fallbackBlocks: Int
)

@Serializable
data class PageHealth(
    val page: Int,
    val textCoverage: Float,
    /** `ok`, `degraded` (went out as an image) or `scanned`. */
    val status: String,
    /** Text and equations drawn as vector shapes, shown as images (Phase 5 converts them). */
    val drawingsAsImages: Int
)

@Serializable
data class Recipe(val engine: Engine, val components: Map<String, String>, val settings: Map<String, String>)

@Serializable
data class Engine(val name: String, val version: String)
