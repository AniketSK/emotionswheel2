# Emotion Wheel

An Android app that draws an interactive three-ring "feelings wheel" in Jetpack Compose, to help you put a name to what you're feeling.

The wheel has 6 core emotions (Anger, Disgust, Sad, Happy, Surprise, Fear). Each one splits into secondary emotions, and each of those splits into two more specific ones: 6 / 36 / 72 segments in total.

## Using it

- **Tap** a segment to zoom into its sub-emotions, one level per tap.
- **Tap a leaf** once its parent is in focus to select it. The full path (e.g. `Anger › Hurt › Embarrassed`) shows below the wheel.
- **Tap the center** or press **Back** to zoom out.
- **Drag** to rotate the wheel, or **fling** to spin it.

## Building

Requires Android Studio with SDK 37. The minimum SDK is 24.

```sh
./gradlew installDebug
```

## Code layout

| Path | What's in it |
|------|--------------|
| `data/EmotionWheelData.kt` | The emotion tree and the color of each core emotion |
| `ui/wheel/EmotionWheel.kt` | The Canvas composable: drawing, hit-testing, gestures, label fitting |
| `ui/wheel/EmotionWheelState.kt` | Rotation, zoom/focus and selection state, plus animations |
| `ui/wheel/WheelModel.kt` | The tree flattened into angular segments for drawing and hit-testing |
| `MainActivity.kt` | The app shell and the wheel screen with its breadcrumb |

`EmotionWheel` is a standalone composable. To use different emotions, pass your own `data`, and use `onEmotionSelected` to receive the lineage of the selected emotion.
