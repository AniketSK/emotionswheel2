package com.aniketkadam.emotionwheel.data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.aniketkadam.emotionwheel.ui.theme.AngerRed
import com.aniketkadam.emotionwheel.ui.theme.DisgustPurple
import com.aniketkadam.emotionwheel.ui.theme.FearGreen
import com.aniketkadam.emotionwheel.ui.theme.HappyYellow
import com.aniketkadam.emotionwheel.ui.theme.SadBlue
import com.aniketkadam.emotionwheel.ui.theme.SurpriseCyan

/** A node in the emotion wheel tree. Leaves have no [children]. */
@Immutable
data class Emotion(
    val name: String,
    val children: List<Emotion> = emptyList(),
)

/** A top-level (innermost ring) emotion together with its base color. */
@Immutable
data class CoreEmotion(
    val emotion: Emotion,
    val color: Color,
)

private fun e(name: String, vararg children: String) =
    Emotion(name, children.map { Emotion(it) })

/**
 * The full feelings wheel, listed clockwise starting from Anger (centered at the top).
 * Each secondary emotion has two tertiary emotions, giving 6 / 36 / 72 segments.
 */
val emotionWheel: List<CoreEmotion> = listOf(
    CoreEmotion(
        Emotion(
            "Anger", listOf(
                e("Hurt", "Embarrassed", "Devastated"),
                e("Threatened", "Insecure", "Jealous"),
                e("Hateful", "Resentful", "Violated"),
                e("Mad", "Furious", "Enraged"),
                e("Aggressive", "Provoked", "Hostile"),
                e("Frustrated", "Infuriated", "Irritated"),
                e("Distant", "Withdrawn", "Suspicious"),
                e("Critical", "Skeptical", "Sarcastic"),
            )
        ),
        AngerRed,
    ),
    CoreEmotion(
        Emotion(
            "Disgust", listOf(
                e("Disapproval", "Judgmental", "Loathing"),
                e("Disappointed", "Repugnant", "Revolted"),
                e("Awful", "Revulsion", "Detestable"),
                e("Avoidance", "Aversion", "Hesitant"),
            )
        ),
        DisgustPurple,
    ),
    CoreEmotion(
        Emotion(
            "Sad", listOf(
                e("Guilty", "Remorseful", "Ashamed"),
                e("Abandoned", "Ignored", "Victimized"),
                e("Despair", "Powerless", "Vulnerable"),
                e("Depressed", "Inferior", "Empty"),
                e("Lonely", "Abandoned", "Isolated"),
                e("Bored", "Apathetic", "Indifferent"),
            )
        ),
        SadBlue,
    ),
    CoreEmotion(
        Emotion(
            "Happy", listOf(
                e("Optimistic", "Inspired", "Open"),
                e("Intimate", "Playful", "Sensitive"),
                e("Peaceful", "Hopeful", "Loving"),
                e("Powerful", "Provocative", "Courageous"),
                e("Accepted", "Respected", "Fulfilled"),
                e("Proud", "Important", "Confident"),
                e("Interested", "Amused", "Inquisitive"),
                e("Joyful", "Ecstatic", "Liberated"),
            )
        ),
        HappyYellow,
    ),
    CoreEmotion(
        Emotion(
            "Surprise", listOf(
                e("Excited", "Energetic", "Eager"),
                e("Amazed", "Awe", "Astonished"),
                e("Confused", "Perplexed", "Disillusioned"),
                e("Startled", "Dismayed", "Shocked"),
            )
        ),
        SurpriseCyan,
    ),
    CoreEmotion(
        Emotion(
            "Fear", listOf(
                e("Scared", "Terrified", "Frightened"),
                e("Anxious", "Overwhelmed", "Worried"),
                e("Insecure", "Inadequate", "Inferior"),
                e("Submissive", "Worthless", "Insignificant"),
                e("Rejected", "Inadequate", "Alienated"),
                e("Humiliated", "Disrespected", "Ridiculed"),
            )
        ),
        FearGreen,
    ),
)
