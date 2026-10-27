package com.aniketkadam.emotionwheel.ui.wheel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniketkadam.emotionwheel.data.CoreEmotion
import com.aniketkadam.emotionwheel.data.emotionWheel
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A three-ring "feelings wheel" drawn on a Canvas.
 *
 * - Tap a segment to zoom into its sub-emotions (one level per tap).
 * - Tap a leaf once its parent is focused to select it.
 * - Tap the center disc or press Back to zoom out.
 * - Drag to rotate; fling to spin.
 */
@Composable
fun EmotionWheel(
    state: EmotionWheelState,
    modifier: Modifier = Modifier,
    data: List<CoreEmotion> = emotionWheel,
    onEmotionSelected: (List<String>) -> Unit = {},
    outlineColor: Color = Color(0xFF1C1C1C),
    labelColor: Color = Color(0xFF1C1C1C),
    outlineWidth: Dp = 1.dp,
    selectedOutlineWidth: Dp = 3.dp,
) {
    val model = remember(data) { WheelModel(data) }
    SideEffect { state.model = model }

    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val currentOnSelected by rememberUpdatedState(onEmotionSelected)

    BackHandler(enabled = state.canZoomOut) {
        scope.launch { state.zoomOut() }
    }

    val focusName = model[state.focusId]?.emotion?.name
    val selectedName = model[state.selectedId]?.emotion?.name

    Spacer(
        modifier = modifier
            .aspectRatio(1f)
            .semantics {
                contentDescription = buildString {
                    append("Emotion wheel")
                    if (focusName != null) append(", showing $focusName")
                    if (selectedName != null) append(", selected $selectedName")
                }
            }
            .pointerInput(model, state) {
                detectTapGestures { tap ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = min(size.width, size.height) / 2f
                    val segment = hitTest(tap, center, radius, model, state) ?: return@detectTapGestures
                    val depth = model.depth(state.focusId)
                    when {
                        segment.level < depth -> scope.launch { state.zoomOut() }
                        segment.level == depth && segment.isLeaf -> {
                            state.select(segment.id)
                            currentOnSelected(segment.lineage)
                        }
                        else -> scope.launch { state.zoomTo(model.ancestorId(segment, depth)) }
                    }
                }
            }
            .pointerInput(state) {
                val tracker = VelocityTracker()
                var accumulated = 0f
                val deadZone = 12.dp.toPx()
                detectDragGestures(
                    onDragStart = {
                        state.flingJob?.cancel()
                        tracker.resetTracking()
                        accumulated = 0f
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().x.coerceIn(-1800f, 1800f)
                        state.flingJob = scope.launch { state.fling(velocity) }
                    },
                ) { change, _ ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val prev = change.previousPosition - center
                    val cur = change.position - center
                    if (prev.getDistance() < deadZone || cur.getDistance() < deadZone) return@detectDragGestures
                    val delta = normalize180(cur.angleDegrees() - prev.angleDegrees())
                    state.rotateBy(delta)
                    accumulated += delta
                    tracker.addPosition(change.uptimeMillis, Offset(accumulated, 0f))
                    change.consume()
                }
            }
            .drawWithCache {
                val outlinePx = outlineWidth.toPx()
                val radius = size.minDimension / 2f - outlinePx
                val labelCache = HashMap<String, LabelSet>()
                fun labels(focusId: String?) = labelCache.getOrPut(focusId.orEmpty()) {
                    buildLabelSet(model, focusId, radius, textMeasurer, labelColor)
                }
                val outline = Stroke(width = outlinePx)
                val selectedOutline = Stroke(width = selectedOutlineWidth.toPx())

                onDrawBehind {
                    val frame = WheelFrame(model, state, center, radius)

                    // Fills, outlines, selection highlight.
                    val shapes = model.segments.mapNotNull { seg -> frame.geometry(seg)?.let { seg to it } }
                    shapes.forEach { (seg, g) ->
                        val fill = if (seg.id == state.selectedId) lerp(seg.baseColor, Color.White, 0.15f) else seg.fillColor
                        drawPath(g.path, fill)
                    }
                    shapes.forEach { (_, g) -> drawPath(g.path, outlineColor, style = outline) }
                    shapes.firstOrNull { it.first.id == state.selectedId }?.let { (_, g) ->
                        drawPath(g.path, outlineColor, style = selectedOutline)
                    }

                    // Labels cross-fade between the outgoing and incoming focus.
                    val t = state.progress
                    if (state.fromFocusId != state.focusId) {
                        drawLabels(labels(state.fromFocusId), frame, alpha = (1f - 2f * t).coerceIn(0f, 1f))
                        drawLabels(labels(state.focusId), frame, alpha = (2f * t - 1f).coerceIn(0f, 1f))
                    } else {
                        drawLabels(labels(state.focusId), frame, alpha = 1f)
                    }
                }
            }
    )
}

// ---------------------------------------------------------------------------------------------
// Per-frame geometry
// ---------------------------------------------------------------------------------------------

private class SegmentGeometry(
    val path: Path,
    val inner: Float,
    val outer: Float,
    val startAngle: Float,
    val sweepAngle: Float,
)

/** Snapshot of the animated geometry for one frame. */
private class WheelFrame(
    private val model: WheelModel,
    state: EmotionWheelState,
    val center: Offset,
    val radius: Float,
) {
    private val t = state.progress
    private val window: WheelWindow = run {
        val a = model.window(state.fromFocusId)
        val b = model.window(state.focusId)
        WheelWindow(lerpF(a.start, b.start, t), lerpF(a.end, b.end, t))
    }
    private val rotation = state.rotation + state.zoomRotation * t
    private val fromDepth = model.depth(state.fromFocusId)
    private val toDepth = model.depth(state.focusId)

    fun segmentFor(id: String): WheelSegment? = model[id]

    fun band(level: Int): Pair<Float, Float> {
        val a = model.bandAt(level, fromDepth)
        val b = model.bandAt(level, toDepth)
        return lerpF(a.first, b.first, t) * radius to lerpF(a.second, b.second, t) * radius
    }

    /** Maps a base angle to a canvas angle. */
    fun display(u: Float): Float =
        model.startAngle + 180f + rotation + (u - window.mid) * 360f / window.sweep

    fun geometry(seg: WheelSegment): SegmentGeometry? {
        val s0 = max(seg.start, window.start)
        val s1 = min(seg.end, window.end)
        if (s1 - s0 <= 0.001f) return null
        val (inner, outer) = band(seg.level)
        if (outer - inner < 0.5f) return null
        val sweep = (s1 - s0) * 360f / window.sweep
        val start = display(s0)
        return SegmentGeometry(sectorPath(center, inner, outer, start, sweep), inner, outer, start, sweep)
    }
}

private fun hitTest(
    tap: Offset,
    center: Offset,
    radius: Float,
    model: WheelModel,
    state: EmotionWheelState,
): WheelSegment? {
    if (state.isAnimating) return null
    val dx = tap.x - center.x
    val dy = tap.y - center.y
    val fraction = hypot(dx, dy) / radius
    if (fraction > 1f) return null
    val depth = model.depth(state.focusId)
    val level = (0 until model.levels).firstOrNull {
        val (inner, outer) = model.bandAt(it, depth)
        outer > inner && fraction >= inner && fraction < outer
    } ?: return null
    val window = model.window(state.focusId)
    val theta = Offset(dx, dy).angleDegrees()
    val rel = normalize180(theta - model.startAngle - 180f - state.rotation)
    val u = window.mid + rel * window.sweep / 360f
    return model.segments.firstOrNull { it.level == level && u >= it.start && u < it.end }
}

/** Annular sector, a pie slice when [inner] is 0, or a full disc/ring when the sweep is ~360°. */
private fun sectorPath(center: Offset, inner: Float, outer: Float, start: Float, sweep: Float) =
    Path().apply {
        val outerRect = Rect(center, outer)
        if (sweep >= 359.9f) {
            fillType = PathFillType.EvenOdd
            addOval(outerRect)
            if (inner > 0f) addOval(Rect(center, inner))
            return@apply
        }
        if (inner <= 0f) {
            moveTo(center.x, center.y)
            arcTo(outerRect, start, sweep, forceMoveTo = false)
        } else {
            arcTo(outerRect, start, sweep, forceMoveTo = true)
            arcTo(Rect(center, inner), start + sweep, -sweep, forceMoveTo = false)
        }
        close()
    }

// ---------------------------------------------------------------------------------------------
// Labels
// ---------------------------------------------------------------------------------------------

/** Pre-measured labels for one focus state. */
private class LabelSet(
    val layouts: Map<String, TextLayoutResult>,
    val centerId: String?,
    val centerHint: TextLayoutResult?,
)

private val baseLabelStyle = TextStyle(
    fontWeight = FontWeight.Medium,
    textAlign = TextAlign.Center,
    letterSpacing = 0.sp,
)

private fun buildLabelSet(
    model: WheelModel,
    focusId: String?,
    radius: Float,
    textMeasurer: TextMeasurer,
    color: Color,
): LabelSet {
    val depth = model.depth(focusId)
    val window = model.window(focusId)
    val scale = 360f / window.sweep
    val style = baseLabelStyle.copy(color = color)
    val layouts = HashMap<String, TextLayoutResult>()

    for (level in depth until model.levels) {
        val segs = model.segments.filter { it.level == level && it.start >= window.start && it.end <= window.end }
        if (segs.isEmpty()) continue
        val (innerF, outerF) = model.bandAt(level, depth)
        val inner = innerF * radius
        val band = (outerF - innerF) * radius
        val isPie = inner <= 0f
        val labelCenter = if (isPie) inner + band * 0.58f else inner + band / 2f
        val maxLength = if (isPie) band * 0.72f else band * 0.88f
        val narrowest = min(segs.minOf { it.sweep } * scale, 120f)
        // Measure the available slice width part-way toward the inner end of the text: the text's
        // ends are rarely full-height letters, and pie slices are very narrow right at the tip.
        val thicknessRadius = labelCenter - maxLength * if (isPie) 0.25f else 0.5f
        val maxThickness = thicknessRadius * Math.toRadians(narrowest.toDouble()).toFloat() * 0.85f
        val fontSize = fitFontSize(segs.map { it.emotion.name.uppercase() }, maxLength, maxThickness, 26.sp, style, textMeasurer)
        segs.forEach { seg ->
            layouts[seg.id] = textMeasurer.measure(seg.emotion.name.uppercase(), style.copy(fontSize = fontSize), softWrap = false)
        }
    }

    var hint: TextLayoutResult? = null
    val focus = model[focusId]
    if (focus != null) {
        val diameter = 2f * WheelModel.CENTER_DISC * radius
        val name = focus.emotion.name.uppercase()
        val fontSize = fitFontSize(listOf(name), diameter * 0.78f, diameter * 0.32f, 24.sp, style, textMeasurer)
        layouts[focus.id] = textMeasurer.measure(
            name, style.copy(fontSize = fontSize, fontWeight = FontWeight.SemiBold), softWrap = false,
        )
        hint = textMeasurer.measure(
            "‹ BACK", style.copy(fontSize = fontSize * 0.5f, color = color.copy(alpha = 0.7f)), softWrap = false,
        )
    }
    return LabelSet(layouts, focus?.id, hint)
}

/** Largest font size (capped at [max]) such that every text fits within [maxWidth] x [maxHeight]. */
private fun fitFontSize(
    texts: List<String>,
    maxWidth: Float,
    maxHeight: Float,
    max: TextUnit,
    style: TextStyle,
    textMeasurer: TextMeasurer,
): TextUnit {
    val reference = 12.sp
    var widest = 1f
    var tallest = 1f
    texts.forEach {
        val r = textMeasurer.measure(it, style.copy(fontSize = reference), softWrap = false)
        widest = max(widest, r.size.width.toFloat())
        tallest = max(tallest, r.size.height.toFloat())
    }
    val factor = min(maxWidth / widest, maxHeight / tallest).coerceAtLeast(0.1f)
    val size = reference * factor
    return if (size.value > max.value) max else size
}

private fun DrawScope.drawLabels(set: LabelSet, frame: WheelFrame, alpha: Float) {
    if (alpha <= 0.01f) return
    set.layouts.forEach { (id, layout) ->
        val seg = frame.segmentFor(id) ?: return@forEach
        val g = frame.geometry(seg) ?: return@forEach
        val half = Offset(layout.size.width / 2f, layout.size.height / 2f)

        if (id == set.centerId) {
            val hint = set.centerHint
            val hintHeight = hint?.size?.height?.toFloat() ?: 0f
            val textCenter = frame.center - Offset(0f, hintHeight / 2f)
            drawText(layout, topLeft = textCenter - half, alpha = alpha)
            if (hint != null) {
                val hintTop = textCenter + Offset(-hint.size.width / 2f, half.y)
                drawText(hint, topLeft = hintTop, alpha = alpha)
            }
            return@forEach
        }

        val band = g.outer - g.inner
        val r = if (g.inner < 1f) g.inner + band * 0.58f else g.inner + band / 2f
        val mid = g.startAngle + g.sweepAngle / 2f
        val rad = Math.toRadians(mid.toDouble())
        val position = frame.center + Offset((cos(rad) * r).toFloat(), (sin(rad) * r).toFloat())
        val normalized = ((mid % 360f) + 360f) % 360f
        // Flip labels on the left half so they're never upside down.
        val rotation = if (normalized > 90f && normalized < 270f) mid + 180f else mid
        rotate(rotation, pivot = position) {
            drawText(layout, topLeft = position - half, alpha = alpha)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Math helpers
// ---------------------------------------------------------------------------------------------

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun normalize180(degrees: Float): Float = ((degrees % 360f) + 540f) % 360f - 180f

private fun Offset.angleDegrees(): Float = Math.toDegrees(atan2(y, x).toDouble()).toFloat()

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

@Preview(name = "Full wheel", widthDp = 380, heightDp = 380, showBackground = true)
@Composable
private fun EmotionWheelPreview() {
    EmotionWheel(rememberEmotionWheelState())
}

@Preview(name = "Zoomed: Anger", widthDp = 380, heightDp = 380, showBackground = true)
@Composable
private fun EmotionWheelAngerPreview() {
    EmotionWheel(rememberEmotionWheelState(initialFocusId = "0"))
}

@Preview(name = "Zoomed: Anger › Hurt", widthDp = 380, heightDp = 380, showBackground = true)
@Composable
private fun EmotionWheelHurtPreview() {
    EmotionWheel(rememberEmotionWheelState(initialFocusId = "0/0", initialSelectedId = "0/0/1"))
}
