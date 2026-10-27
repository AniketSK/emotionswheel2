package com.aniketkadam.emotionwheel.ui.wheel

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Job

/**
 * Hoistable state for [EmotionWheel]: which segment is zoomed into, which leaf is selected,
 * and the current rotation. Zoom transitions and rotation flings are animated here.
 */
@Stable
class EmotionWheelState(
    initialFocusId: String? = null,
    initialSelectedId: String? = null,
    initialRotation: Float = 0f,
) {
    /** Id of the segment currently zoomed into, or null for the full wheel. */
    var focusId: String? by mutableStateOf(initialFocusId)
        private set

    /** Id of the selected leaf segment, or null. */
    var selectedId: String? by mutableStateOf(initialSelectedId)
        private set

    /** Rotation of the wheel in degrees (clockwise). */
    var rotation: Float by mutableFloatStateOf(initialRotation)
        private set

    // --- Zoom transition (internal, read during draw) ---
    internal var fromFocusId: String? by mutableStateOf(initialFocusId)
        private set

    /** 0..1 progress of the current zoom transition; 1 when idle. */
    internal var progress: Float by mutableFloatStateOf(1f)
        private set

    /** Extra rotation applied progressively during a zoom so the anchor segment stays put. */
    internal var zoomRotation: Float by mutableFloatStateOf(0f)
        private set

    /** Wheel geometry; provided by [EmotionWheel]. */
    internal var model: WheelModel? by mutableStateOf(null)

    internal var flingJob: Job? = null

    val isAnimating: Boolean get() = progress < 1f
    val canZoomOut: Boolean get() = focusId != null

    /** Names from the core emotion to the focused one, e.g. [Anger, Hurt]. */
    val focusLineage: List<String> get() = model?.get(focusId)?.lineage.orEmpty()

    /** Names from the core emotion to the selected leaf, e.g. [Anger, Hurt, Devastated]. */
    val selectedLineage: List<String> get() = model?.get(selectedId)?.lineage.orEmpty()

    fun select(segmentId: String?) {
        selectedId = segmentId
    }

    internal fun rotateBy(degrees: Float) {
        rotation = (rotation + degrees) % 360f
    }

    /** Spins the wheel with the given angular velocity (deg/s) and lets it decay. */
    internal suspend fun fling(velocity: Float) {
        var last = 0f
        AnimationState(initialValue = 0f, initialVelocity = velocity)
            .animateDecay(exponentialDecay(frictionMultiplier = 1.5f)) {
                rotateBy(value - last)
                last = value
            }
    }

    /** Zooms out one level. */
    suspend fun zoomOut() {
        val m = model ?: return
        if (focusId == null) return
        zoomTo(m.parentId(focusId))
    }

    /** Zooms into the segment with [segmentId] (null = full wheel). */
    suspend fun zoomTo(segmentId: String?) {
        val m = model ?: return
        if (isAnimating || segmentId == focusId) return
        val from = focusId
        val fromWindow = m.window(from)
        val toWindow = m.window(segmentId)
        val zoomingIn = segmentId != null && (from == null || segmentId.startsWith("$from/"))
        // Keep this base angle at the same screen position before and after the zoom.
        val anchor = (if (zoomingIn) m[segmentId] else m[from])?.mid ?: toWindow.mid
        val delta = (anchor - fromWindow.mid) * 360f / fromWindow.sweep -
            (anchor - toWindow.mid) * 360f / toWindow.sweep

        Snapshot.withMutableSnapshot {
            fromFocusId = from
            focusId = segmentId
            zoomRotation = delta
            progress = 0f
        }
        try {
            animate(0f, 1f, animationSpec = tween(450, easing = FastOutSlowInEasing)) { value, _ ->
                progress = value
            }
        } finally {
            Snapshot.withMutableSnapshot {
                rotation = (rotation + zoomRotation) % 360f
                zoomRotation = 0f
                fromFocusId = segmentId
                progress = 1f
            }
        }
    }

    companion object {
        val Saver = listSaver<EmotionWheelState, Any>(
            save = { listOf(it.focusId.orEmpty(), it.selectedId.orEmpty(), it.rotation) },
            restore = {
                EmotionWheelState(
                    initialFocusId = (it[0] as String).ifEmpty { null },
                    initialSelectedId = (it[1] as String).ifEmpty { null },
                    initialRotation = it[2] as Float,
                )
            },
        )
    }
}

@Composable
fun rememberEmotionWheelState(
    initialFocusId: String? = null,
    initialSelectedId: String? = null,
): EmotionWheelState = rememberSaveable(saver = EmotionWheelState.Saver) {
    EmotionWheelState(initialFocusId, initialSelectedId)
}
