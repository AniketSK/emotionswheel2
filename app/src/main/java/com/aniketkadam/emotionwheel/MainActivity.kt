package com.aniketkadam.emotionwheel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.aniketkadam.emotionwheel.ui.theme.EmotionWheelTheme
import com.aniketkadam.emotionwheel.ui.wheel.EmotionWheel
import com.aniketkadam.emotionwheel.ui.wheel.rememberEmotionWheelState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EmotionWheelTheme {
                EmotionWheelApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun EmotionWheelApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            WheelScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
fun WheelScreen(modifier: Modifier = Modifier) {
    val wheelState = rememberEmotionWheelState()
    val focus = wheelState.focusLineage
    val selected = wheelState.selectedLineage
    // Show the selection only while it's inside the area currently zoomed into.
    val breadcrumb = if (selected.isNotEmpty() && selected.take(focus.size) == focus) selected else focus

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        EmotionWheel(
            state = wheelState,
            modifier = Modifier.widthIn(max = 600.dp).weight(1f, fill = false),
        )
        Text(
            text = if (breadcrumb.isEmpty()) "Tap an emotion to explore" else breadcrumb.joinToString("  ›  "),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}
