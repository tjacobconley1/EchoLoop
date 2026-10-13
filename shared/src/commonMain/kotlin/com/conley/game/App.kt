package com.conley.game.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private enum class Screen { Splash, Title, Who, Name, Game, Scores }

/** Root of the shared UI. [store] keeps level progress, the player's name and the high scores. */
@Composable
fun App(store: KeyValueStore = InMemoryStore(), sounds: SoundPlayer = NoSound) {
    var screen by remember { mutableStateOf(Screen.Splash) }
    // Set while a *new* player is choosing a name; the previous player's run is closed out once they finish.
    var previousPlayer by remember { mutableStateOf<String?>(null) }

    // Back button (Android): from any inner screen go to the main screen; on the main screen it exits the app.
    PlatformBackHandler(enabled = screen != Screen.Title && screen != Screen.Splash) {
        previousPlayer = null
        screen = Screen.Title
    }

    // Keep content clear of the status bar, notches and navigation bar.
    Box(Modifier.fillMaxSize().background(Pal.bgTop).safeDrawingPadding()) {
        when (screen) {
            Screen.Splash -> SplashScreen(onDone = { screen = Screen.Title })
            Screen.Title -> TitleScreen(
                store,
                // Every time PLAY is pressed: ask for a name the first time, otherwise ask who is playing.
                onPlay = { screen = if (store.getString(KEY_NAME, "").isBlank()) Screen.Name else Screen.Who },
                onScores = { screen = Screen.Scores },
            )
            Screen.Who -> WhoScreen(
                name = store.getString(KEY_NAME, ""),
                onContinue = { screen = Screen.Game },
                onBack = { screen = Screen.Title },
                onNewPlayer = {
                    previousPlayer = store.getString(KEY_NAME, "")
                    screen = Screen.Name
                },
            )
            Screen.Name -> NameScreen(
                store,
                startBlank = previousPlayer != null,
                onDone = {
                    previousPlayer?.let { old ->
                        // Save the old player's score, then start the new player from level 1.
                        Scores.submit(store, old, store.getInt(KEY_SCORE, 0), store.getInt(KEY_LEVEL, 0) + 1)
                        store.putInt(KEY_SCORE, 0)
                        store.putInt(KEY_LEVEL, 0)
                    }
                    previousPlayer = null
                    screen = Screen.Game
                },
            )
            Screen.Scores -> ScoresScreen(store, onBack = { screen = Screen.Title })
            Screen.Game -> GameScreen(
                store,
                onScores = { screen = Screen.Scores },
                onChangeName = { screen = Screen.Name },
                onHome = { screen = Screen.Title },
                sounds = sounds,
            )
        }
    }
}


