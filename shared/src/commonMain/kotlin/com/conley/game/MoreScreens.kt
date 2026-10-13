package com.conley.game.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .widthIn(min = 160.dp)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .background(Brush.horizontalGradient(listOf(Color(0xFF58C8FF), Color(0xFF7A6BFF))), RoundedCornerShape(50))
            .padding(horizontal = 28.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, maxLines = 1, softWrap = false, style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold))
    }
}

/** Asks for the player's name (used on the high score table). */
@Composable
internal fun NameScreen(store: KeyValueStore, onDone: () -> Unit, startBlank: Boolean = false) {
    val t = rememberTime()
    var text by remember { mutableStateOf(if (startBlank) "" else store.getString(KEY_NAME, "")) }
    val focus = remember { FocusRequester() }
    fun submit() {
        val name = Scores.clean(text)
        if (name.isNotEmpty()) { store.putString(KEY_NAME, name); onDone() }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawBackdrop(t + 20f) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BasicText("WHAT'S YOUR NAME?", style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
            Spacer(Modifier.height(4.dp))
            BasicText("It goes on the high score table.", style = TextStyle(color = Color(0xAAFFFFFF), fontSize = 13.sp))
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(14) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 24.sp, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.focusRequester(focus),
                decorationBox = { inner ->
                    Box(
                        Modifier.widthIn(min = 240.dp).background(Color(0x33FFFFFF), RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (text.isEmpty()) BasicText("Your name", style = TextStyle(color = Color(0x66FFFFFF), fontSize = 24.sp))
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(14.dp))
            PillButton("OK", ::submit)
        }
    }
}

/** Top-10 table plus the run that is currently in progress. */
@Composable
internal fun ScoresScreen(store: KeyValueStore, onBack: () -> Unit) {
    val t = rememberTime()
    val entries = remember { Scores.load(store) }
    val name = store.getString(KEY_NAME, "")
    val current = store.getInt(KEY_SCORE, 0)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawBackdrop(t + 30f) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("HIGH SCORES", style = TextStyle(color = Pal.gold, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp))
            Spacer(Modifier.height(8.dp))
            if (entries.isEmpty()) {
                BasicText("No scores yet — finish a run or restart the game to record one.", style = TextStyle(color = Color(0xCCFFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center))
            } else {
                // Two columns keeps all ten rows visible on a landscape phone.
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    for (half in entries.chunked(5).withIndex()) {
                        Column {
                            for ((i, e) in half.value.withIndex()) {
                                val rank = half.index * 5 + i + 1
                                BasicText(
                                    "$rank.  ${e.name}  —  ${e.score}  (lvl ${e.level})",
                                    Modifier.padding(vertical = 3.dp),
                                    style = TextStyle(color = if (rank == 1) Pal.gold else Color.White, fontSize = 15.sp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                "Current run${if (name.isNotEmpty()) " ($name)" else ""}: $current pts",
                style = TextStyle(color = Color(0xAAFFFFFF), fontSize = 13.sp),
            )
            Spacer(Modifier.height(12.dp))
            PillButton("BACK", onBack)
        }
    }
}

/** Shown when pressing PLAY with a saved name: keep playing as that player or start as someone new. */
@Composable
internal fun WhoScreen(name: String, onContinue: () -> Unit, onNewPlayer: () -> Unit, onBack: () -> Unit) {
    val t = rememberTime()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawBackdrop(t + 40f) }
        BasicText(
            "‹ BACK",
            Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .pointerInput(Unit) { detectTapGestures { onBack() } }
                .background(Color(0x33FFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("WELCOME BACK", style = TextStyle(color = Color(0xAAFFFFFF), fontSize = 14.sp, letterSpacing = 3.sp))
            Spacer(Modifier.height(4.dp))
            BasicText(name, style = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black))
            Spacer(Modifier.height(18.dp))
            PillButton("CONTINUE AS $name".uppercase(), onContinue)
            Spacer(Modifier.height(10.dp))
            BasicText(
                "NEW PLAYER",
                Modifier.pointerInput(Unit) { detectTapGestures { onNewPlayer() } }.padding(horizontal = 20.dp, vertical = 10.dp),
                style = TextStyle(color = Pal.gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
            )
            BasicText(
                "A new player starts from level 1.\nYour current score is saved to the high score table.",
                style = TextStyle(color = Color(0x88FFFFFF), fontSize = 12.sp, textAlign = TextAlign.Center),
            )
        }
    }
}

