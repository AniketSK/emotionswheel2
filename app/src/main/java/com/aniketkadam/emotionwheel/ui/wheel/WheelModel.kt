package com.aniketkadam.emotionwheel.ui.wheel

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.aniketkadam.emotionwheel.data.CoreEmotion
import com.aniketkadam.emotionwheel.data.Emotion

/**
 * A single slice of the wheel in "base" angular space, where the whole wheel spans 0..360
 * starting at the first core emotion's leading edge.
 *
 * @property id stable, saveable id ("coreIndex/secondaryIndex/tertiaryIndex"), unique even when names repeat.
 * @property lineage names from the core emotion down to this one, e.g. [Fear, Insecure, Inferior].
 * @property level ring index: 0 = core, 1 = secondary, 2 = tertiary.
 */
@Immutable
data class WheelSegment(
    val id: String,
    val emotion: Emotion,
    val lineage: List<String>,
    val level: Int,
    val start: Float,
    val sweep: Float,
    val baseColor: Color,
) {
    val end: Float get() = start + sweep
    val mid: Float get() = start + sweep / 2f
    val isLeaf: Boolean get() = emotion.children.isEmpty()

    /** Fill color for this ring: the deeper the ring, the lighter the tint. */
    val fillColor: Color = when (level) {
        0 -> baseColor
        1 -> lerp(baseColor, Color.White, 0.35f)
        else -> lerp(baseColor, Color.White, 0.6f)
    }
}

/** An angular window into base space. */
@Immutable
data class WheelWindow(val start: Float, val end: Float) {
    val sweep: Float get() = end - start
    val mid: Float get() = (start + end) / 2f

    companion object {
        val Full = WheelWindow(0f, 360f)
    }
}

/** Precomputed, immutable geometry of the whole wheel. */
@Immutable
class WheelModel(data: List<CoreEmotion>) {
    val segments: List<WheelSegment> = buildWheelSegments(data)
    val levels: Int = (segments.maxOfOrNull { it.level } ?: 0) + 1

    /** Canvas angle (0° = 3 o'clock, clockwise) of base angle 0: centers the first core emotion at 12 o'clock. */
    val startAngle: Float = run {
        val first = segments.firstOrNull { it.level == 0 }
        -90f - (first?.sweep ?: 0f) / 2f
    }

    private val byId: Map<String, WheelSegment> = segments.associateBy { it.id }

    operator fun get(id: String?): WheelSegment? = id?.let(byId::get)

    fun window(focusId: String?): WheelWindow =
        this[focusId]?.let { WheelWindow(it.start, it.end) } ?: WheelWindow.Full

    /** Focus depth: 0 for the full wheel, otherwise the focused segment's level + 1. */
    fun depth(focusId: String?): Int = this[focusId]?.let { it.level + 1 } ?: 0

    fun parentId(id: String?): String? =
        id?.substringBeforeLast('/', missingDelimiterValue = "")?.ifEmpty { null }

    /** Id of [segment]'s ancestor at [level] (or the segment itself). */
    fun ancestorId(segment: WheelSegment, level: Int): String =
        segment.id.split('/').take(level + 1).joinToString("/")

    /**
     * Radial band (as fractions of the outer radius) for [level] when the wheel is focused at [depth].
     * - depth 0: all rings share the radius equally.
     * - depth d > 0: the focused level (d - 1) becomes a center disc, shallower levels collapse,
     *   deeper levels share the remaining annulus.
     */
    fun bandAt(level: Int, depth: Int): Pair<Float, Float> {
        if (depth == 0) return level.toFloat() / levels to (level + 1f) / levels
        return when {
            level < depth - 1 -> 0f to 0f
            level == depth - 1 -> 0f to CENTER_DISC
            else -> {
                val n = levels - depth
                val i = level - depth
                (CENTER_DISC + (1f - CENTER_DISC) * i / n) to (CENTER_DISC + (1f - CENTER_DISC) * (i + 1) / n)
            }
        }
    }

    companion object {
        const val CENTER_DISC = 0.30f
    }
}

private fun Emotion.leafCount(): Int =
    if (children.isEmpty()) 1 else children.sumOf { it.leafCount() }

/** Flattens the emotion tree into segments. Every leaf gets an equal slice of 360°. */
fun buildWheelSegments(data: List<CoreEmotion>): List<WheelSegment> {
    val totalLeaves = data.sumOf { it.emotion.leafCount() }
    if (totalLeaves == 0) return emptyList()
    val leafSweep = 360f / totalLeaves
    val result = mutableListOf<WheelSegment>()
    var angle = 0f

    fun add(emotion: Emotion, level: Int, id: String, lineage: List<String>, color: Color) {
        val start = angle
        val names = lineage + emotion.name
        if (emotion.children.isEmpty()) {
            angle += leafSweep
        } else {
            emotion.children.forEachIndexed { i, child -> add(child, level + 1, "$id/$i", names, color) }
        }
        result += WheelSegment(id, emotion, names, level, start, angle - start, color)
    }

    data.forEachIndexed { i, core -> add(core.emotion, 0, "$i", emptyList(), core.color) }
    // Parents first so children are drawn on top of nothing they overlap (rings don't overlap anyway).
    return result.sortedWith(compareBy({ it.level }, { it.start }))
}
