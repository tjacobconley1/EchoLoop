package com.conley.game.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Seconds since this composable entered composition, updated every frame. */
@Composable
internal fun rememberTime(): Float = rememberTimeState().value

/** Like [rememberTime] but returns the State so callers can read it in the draw phase and avoid recomposing. */
@Composable
internal fun rememberTimeState(): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var start = 0L
        while (true) withFrameNanos { n ->
            if (start == 0L) start = n
            t.floatValue = (n - start) / 1_000_000_000f
        }
    }
    return t
}

/** Title text with a trailing "echo" of offset copies. */
@Composable
internal fun EchoLogo(fontSize: TextUnit, time: Float, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        for (k in 3 downTo 1) {
            val dx = (k * (6f + 3f * sin(time * 2f))).dp
            BasicText(
                "ECHO LOOP",
                Modifier.offset(x = -dx),
                style = TextStyle(
                    color = Pal.ghost.copy(alpha = 0.14f * (4 - k)),
                    fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = 4.sp,
                ),
            )
        }
        BasicText(
            "ECHO LOOP",
            style = TextStyle(color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = 4.sp),
        )
    }
}

@Composable
internal fun SplashScreen(onDone: () -> Unit) {
    val t = rememberTime()
    val total = 2.6f
    val a = when {
        t < 0.8f -> t / 0.8f
        t < 1.9f -> 1f
        else -> ((total - t) / (total - 1.9f)).coerceIn(0f, 1f)
    }
    val done = t >= total
    LaunchedEffect(done) { if (done) onDone() }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDone() } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawBackdrop(t)
            val cx = size.width / 2
            val bottom = size.height * 0.40f
            val bh = size.height * 0.07f
            for (k in 3 downTo 0) {
                val lag = k * 0.25f
                val x = cx - size.width * 0.22f + (t - lag).coerceAtLeast(0f) * size.width * 0.15f
                drawBuddy(
                    x.coerceAtMost(cx + size.width * 0.22f), bottom, bh * 0.75f, bh,
                    if (k == 0) Pal.hero else Pal.ghost, if (k == 0) Pal.heroShade else Pal.ghostShade,
                    if (k == 0) Color(0xFFBFD0FF) else Pal.ghost, 1, 0f, a * (if (k == 0) 1f else 0.55f / k + 0.1f),
                )
            }
        }
        EchoLogo(44.sp, t, Modifier.alpha(a))
    }
}

@Composable
internal fun TitleScreen(store: KeyValueStore, onPlay: () -> Unit, onScores: () -> Unit) {
    val t = rememberTime()
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawBackdrop(t + 10f)
            val floorY = size.height * 0.80f
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFF30377A), Color(0xFF14183A)), startY = floorY, endY = size.height),
                Offset(0f, floorY), androidx.compose.ui.geometry.Size(size.width, size.height - floorY),
            )
            drawRect(Pal.wallEdge, Offset(0f, floorY), androidx.compose.ui.geometry.Size(size.width, 4f))
            val bh = size.height * 0.07f
            for (k in 3 downTo 0) {
                val tt = t - k * 0.35f
                val x = size.width / 2 + sin(tt * 1.4f) * size.width * 0.28f
                val hop = max(0f, sin(tt * 4f)) * size.height * 0.07f
                drawBuddy(
                    x, floorY - hop, bh * 0.75f, bh,
                    if (k == 0) Pal.hero else Pal.ghost, if (k == 0) Pal.heroShade else Pal.ghostShade,
                    if (k == 0) Color(0xFFBFD0FF) else Pal.ghost,
                    if (cos(tt * 1.4f) >= 0) 1 else -1, -cos(tt * 4f) * 300f,
                    if (k == 0) 1f else 0.6f / k + 0.1f,
                )
            }
        }
        // Fits short (landscape) screens: content is centred in the area above the hopping characters' floor.
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.78f).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EchoLogo(40.sp, t)
            Spacer(Modifier.height(6.dp))
            BasicText(
                "Solve puzzles with your past selves",
                style = TextStyle(color = Color(0xCCFFFFFF), fontSize = 15.sp, textAlign = TextAlign.Center),
            )
            Spacer(Modifier.height(18.dp))
            val pulse = 1f + 0.05f * sin(t * 3.5f)
            Box(
                Modifier
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .pointerInput(Unit) { detectTapGestures { onPlay() } }
                    .widthIn(min = 200.dp)
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF58C8FF), Color(0xFF7A6BFF))),
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 32.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "PLAY",
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                )
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                "Level ${store.getInt(KEY_LEVEL, 0) + 1}  ·  ${store.getInt(KEY_SCORE, 0)} pts",
                style = TextStyle(color = Color(0xAAFFFFFF), fontSize = 14.sp),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                "HIGH SCORES",
                Modifier
                    .pointerInput(Unit) { detectTapGestures { onScores() } }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = TextStyle(color = Pal.gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
            )
        }
    }
}







