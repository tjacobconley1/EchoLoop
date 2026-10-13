package com.conley.game.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conley.game.levels.LevelCatalog
import com.conley.game.sim.InputFrame
import com.conley.game.sim.LoopSession
import com.conley.game.sim.Status
import com.conley.game.sim.TILE

private const val STEP_NANOS = 1_000_000_000L / 60
private const val MAX_CATCH_UP_NANOS = 100_000_000L
private const val TOP_FRACTION = 0.10f
private const val BOTTOM_FRACTION = 0.20f
private const val JUMP_BUFFER_TICKS = 8

@Composable
internal fun GameScreen(store: KeyValueStore, onScores: () -> Unit, onChangeName: () -> Unit, onHome: () -> Unit, sounds: SoundPlayer = NoSound) {
    val timeState = rememberTimeState()
    var levelIndex by remember { mutableIntStateOf(store.getInt(KEY_LEVEL, 0).coerceIn(0, LevelCatalog.count - 1)) }
    var session by remember { mutableStateOf(LoopSession(LevelCatalog.get(levelIndex).level)) }
    var frame by remember { mutableIntStateOf(0) }
    var holding by remember { mutableStateOf(false) }
    var jumpBuffer by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var messageTicks by remember { mutableIntStateOf(0) }
    var showHint by remember { mutableStateOf(LevelCatalog.get(levelIndex).hint.isNotEmpty()) }
    var menuOpen by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(store.getInt(KEY_MUTED, 0) == 1) }
    // Back closes an open menu first (this handler is more specific than the one in App).
    PlatformBackHandler(enabled = menuOpen) { menuOpen = false }
    fun sfx(sound: Sound) { if (!muted) sounds.play(sound) }
    var score by remember { mutableIntStateOf(store.getInt(KEY_SCORE, 0)) }
    var levelTicks by remember { mutableIntStateOf(0) }
    var levelDeaths by remember { mutableIntStateOf(0) }
    // Death burst effect: where the player died and when (animation time, seconds).
    var deathX by remember { mutableIntStateOf(0) }
    var deathY by remember { mutableIntStateOf(0) }
    var deathStart by remember { mutableStateOf(-1000f) }

    fun flash(text: String) { message = text; messageTicks = 150 }

    fun loadLevel(index: Int) {
        levelIndex = index
        val def = LevelCatalog.get(index)
        session = LoopSession(def.level)
        showHint = def.hint.isNotEmpty()
        levelTicks = 0
        levelDeaths = 0
        store.putInt(KEY_LEVEL, index)
    }

    fun setScore(value: Int) { score = value; store.putInt(KEY_SCORE, value) }

    fun restartGame() {
        Scores.submit(store, store.getString(KEY_NAME, ""), score, levelIndex + 1)
        setScore(0)
        loadLevel(0)
    }

    LaunchedEffect(Unit) {
        var last = 0L
        var acc = 0L
        while (true) {
            withFrameNanos { now ->
                // Cap the catch-up: after the phone sleeps, `now - last` can be minutes, which would otherwise run
                // hundreds of thousands of simulation steps in one frame and freeze the UI.
                if (last != 0L) acc += minOf(now - last, MAX_CATCH_UP_NANOS)
                last = now
                if (menuOpen || paused || (showHint && LevelCatalog.get(levelIndex).hint.isNotEmpty())) acc = 0L
                while (acc >= STEP_NANOS) {
                    acc -= STEP_NANOS
                    if (messageTicks > 0) messageTicks--
                    val s = session
                    if (s.world.status == Status.Running) {
                        val wantJump = jumpBuffer > 0
                        if (wantJump) jumpBuffer--
                        if (wantJump && s.world.onGround) sfx(Sound.Jump)
                        s.step(InputFrame(move = holding, jump = wantJump))
                        if (s.world.vy < 0) jumpBuffer = 0
                    } else {
                        val result = s.world.status
                        levelTicks += s.world.tick
                        if (result == Status.Died) {
                            levelDeaths++; sfx(Sound.Death)
                            // Burst only for spikes; falling into a pit (below the level) has no animation.
                            if (s.world.y <= (s.world.level.height + 2) * TILE) {
                                deathX = s.world.x
                                deathY = s.world.y
                                deathStart = timeState.value
                            }
                        }
                        s.finishLoop()
                        when {
                            s.solved -> {
                                sfx(Sound.Portal)
                                val points = (1000 - levelTicks - 75 * levelDeaths).coerceAtLeast(100)
                                setScore(score + points)
                                val next = levelIndex + 1
                                if (next >= LevelCatalog.count) {
                                    Scores.submit(store, store.getString(KEY_NAME, ""), score, LevelCatalog.count)
                                    setScore(0)
                                    loadLevel(0)
                                    flash("You beat every level! Score saved.")
                                } else {
                                    loadLevel(next)
                                    flash("Level complete!  +$points")
                                }
                            }
                            result == Status.Died -> flash("Ouch! That loop was discarded.")
                            result == Status.TimedOut -> flash("Out of time — you left a ghost.")
                            result == Status.Ended -> flash("Ghost left behind. Run again!")
                        }
                    }
                }
                frame++
            }
        }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (menuOpen) { holding = false; continue }
                    // Top-right buttons: hamburger (rightmost) and pause (just left of it).
                    val menuLeft = size.width - 56.dp.toPx()
                    val pauseLeft = size.width - 104.dp.toPx()
                    val topBand = size.height * 0.16f
                    // While paused, any tap resumes (the menu button still opens the menu).
                    if (paused) {
                        for (c in event.changes) {
                            if (c.pressed && !c.previousPressed) {
                                if (c.position.x > menuLeft && c.position.y < topBand) menuOpen = true
                                paused = false
                            }
                        }
                        holding = false
                        continue
                    }
                    // While a tutorial hint is showing the game is paused; any tap dismisses it (the buttons still work).
                    if (showHint && LevelCatalog.get(levelIndex).hint.isNotEmpty()) {
                        for (c in event.changes) {
                            if (c.pressed && !c.previousPressed) {
                                if (c.position.x > menuLeft && c.position.y < topBand) menuOpen = true
                                else if (c.position.x > pauseLeft && c.position.y < topBand) paused = true
                                else showHint = false
                            }
                        }
                        holding = false
                        continue
                    }
                    var run = false
                    for (c in event.changes) {
                        if (!c.pressed) continue
                        val newlyDown = !c.previousPressed
                        val fx = c.position.x / size.width
                        val left = fx < 0.5f
                        val inEndButton = fx > 0.34f && fx < 0.66f &&
                            c.position.y > size.height * (1f - BOTTOM_FRACTION + 0.03f)
                        val inMenuButton = c.position.x > menuLeft && c.position.y < topBand
                        val inPauseButton = c.position.x > pauseLeft && c.position.x <= menuLeft && c.position.y < topBand
                        val inHint = fx < 0.40f && c.position.y < size.height * TOP_FRACTION
                        when {
                            inMenuButton -> if (newlyDown) menuOpen = true
                            inPauseButton -> if (newlyDown) paused = true
                            inEndButton -> if (newlyDown) { session.world.end(); showHint = false }
                            inHint -> if (newlyDown) showHint = !showHint
                            left -> { run = true; showHint = false }
                            newlyDown -> { jumpBuffer = JUMP_BUFFER_TICKS; showHint = false }
                        }
                    }
                    holding = run
                }
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame.let { }
            val time = timeState.value   // read here (draw phase) so the whole screen doesn't recompose every frame
            drawBackdrop(time)
            val world = session.world
            val level = world.level
            val top = size.height * TOP_FRACTION
            val availH = size.height - top - size.height * BOTTOM_FRACTION
            val scale = minOf(size.width * 0.96f / (level.width * TILE), availH / (level.height * TILE))
            val ox = (size.width - level.width * TILE * scale) / 2
            val oy = top + (availH - level.height * TILE * scale) / 2
            drawWorld(world, time, ox, oy, scale, deathX, deathY, time - deathStart)
        }

        val def = LevelCatalog.get(levelIndex)
        // Only changes when the shown text changes (about once a second), not every frame.
        val hudText by remember {
            derivedStateOf {
                frame
                val w = session.world
                "${LevelCatalog.get(levelIndex).title} / ${LevelCatalog.count}  ·  $score pts\n" +
                    "Loop ${session.loopNumber}  ·  ${(w.level.maxTicks - w.tick) / 60}s"
            }
        }

        Box(
            Modifier.align(Alignment.TopStart).padding(12.dp)
                .background(Color(0x33FFFFFF), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            BasicText(
                hudText,
                style = TextStyle(color = Color.White, fontSize = 13.sp),
            )
        }
        // Hamburger button (touch handling is in the pointer handler above).
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).size(38.dp)
                .background(Color(0x33FFFFFF), RoundedCornerShape(10.dp)),
        ) {
            Canvas(Modifier.fillMaxSize().padding(9.dp)) {
                for (i in 0..2) {
                    val y = size.height * (0.12f + 0.38f * i)
                    drawLine(Color.White, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.5f * (size.width / 20f), cap = StrokeCap.Round)
                }
            }
        }
        // Pause button, just left of the hamburger (touch handling is in the pointer handler above).
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 58.dp).size(38.dp)
                .background(Color(0x33FFFFFF), RoundedCornerShape(10.dp)),
        ) {
            Canvas(Modifier.fillMaxSize().padding(11.dp)) {
                val bar = size.width * 0.3f
                drawRoundRect(Color.White, Offset(size.width * 0.1f, 0f), Size(bar, size.height), CornerRadius(bar / 3f))
                drawRoundRect(Color.White, Offset(size.width * 0.6f, 0f), Size(bar, size.height), CornerRadius(bar / 3f))
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
                .background(
                    Brush.horizontalGradient(listOf(Color(0xCC58C8FF), Color(0xCC7A6BFF))),
                    RoundedCornerShape(50),
                )
                .border(1.5.dp, Color(0xCCFFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    "↻ END LOOP",
                    style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, textAlign = TextAlign.Center),
                )
                BasicText(
                    "leave a ghost \uD83D\uDC7B",
                    style = TextStyle(color = Color(0xDDFFFFFF), fontSize = 11.sp, textAlign = TextAlign.Center),
                )
            }
        }
        BasicText(
            "◀ HOLD\nto run",
            Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 28.dp),
            style = TextStyle(color = Color(0x99FFFFFF), fontSize = 15.sp),
        )
        BasicText(
            "TAP\nto jump ▶",
            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 28.dp),
            style = TextStyle(color = Color(0x99FFFFFF), fontSize = 15.sp, textAlign = TextAlign.End),
        )
        if (showHint && def.hint.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp)
                    .background(Color(0xEE0B0E1A), RoundedCornerShape(20.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    def.hint,
                    style = TextStyle(color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center),
                )
                BasicText(
                    "\nTap anywhere to start",
                    style = TextStyle(color = Pal.gold, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
        if (paused) {
            Column(
                Modifier.align(Alignment.Center)
                    .background(Color(0xEE0B0E1A), RoundedCornerShape(20.dp)).padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText("PAUSED", style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp))
                BasicText("\nTap anywhere to resume", style = TextStyle(color = Pal.gold, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
        }
        if (messageTicks > 0) {
            BasicText(
                message,
                Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
                    .background(Color(0xCC0B0E1A), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                style = TextStyle(color = Pal.gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            )
        }

        if (menuOpen) {
            // Tapping anywhere outside the menu closes it; the game is paused while it is open.
            Box(Modifier.fillMaxSize().background(Color(0x66000000)).pointerInput(Unit) { detectTapGestures { menuOpen = false } })
            val items = listOf<Pair<String, () -> Unit>>(
                "Restart level" to { session.restartLevel(); levelTicks = 0; levelDeaths = 0; flash("Level restarted") },
                "Skip level" to {
                    if (levelIndex + 1 < LevelCatalog.count) { loadLevel(levelIndex + 1); flash("Skipped — no points") }
                },
                "Main menu" to { onHome() },
                "Restart game" to { restartGame(); flash("New game started") },
                (if (muted) "Sound: off" else "Sound: on") to { muted = !muted; store.putInt(KEY_MUTED, if (muted) 1 else 0) },
                "High scores" to { onScores() },
                "Change name" to { onChangeName() },
            )
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 12.dp)
                    .background(Color(0xF20B0E1A), RoundedCornerShape(16.dp)).padding(vertical = 6.dp),
            ) {
                for ((label, action) in items) {
                    BasicText(
                        label,
                        Modifier.widthIn(min = 190.dp)
                            .pointerInput(label) { detectTapGestures { menuOpen = false; action() } }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                        style = TextStyle(color = Color.White, fontSize = 16.sp),
                    )
                }
            }
        }
    }
}














