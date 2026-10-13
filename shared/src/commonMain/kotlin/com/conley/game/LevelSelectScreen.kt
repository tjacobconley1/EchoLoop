package com.conley.game.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conley.game.levels.LevelCatalog
import com.conley.game.sim.TILE
import com.conley.game.sim.World

private val CheckGreen = Color(0xFF2FD86B)

/** Scrollable grid of level thumbnails. Tapping one starts it; completed levels get a green check mark. */
@Composable
internal fun LevelSelectScreen(store: KeyValueStore, onPick: (Int) -> Unit, onBack: () -> Unit) {
    val t = rememberTime()
    val done = remember { Progress.load(store) }
    val current = store.getInt(KEY_LEVEL, 0)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawBackdrop(t + 50f) }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                BasicText(
                    "‹ BACK",
                    Modifier
                        .align(Alignment.CenterStart)
                        .pointerInput(Unit) { detectTapGestures { onBack() } }
                        .background(Color(0x33FFFFFF), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                )
                BasicText(
                    "SELECT LEVEL  ·  ${done.size}/${LevelCatalog.count}",
                    Modifier.align(Alignment.Center),
                    style = TextStyle(color = Pal.gold, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items((0 until LevelCatalog.count).toList()) { index ->
                    LevelCard(index, completed = index in done, isCurrent = index == current, onClick = { onPick(index) })
                }
            }
        }
    }
}

@Composable
private fun LevelCard(index: Int, completed: Boolean, isCurrent: Boolean, onClick: () -> Unit) {
    val world = remember(index) { World(LevelCatalog.get(index).level).also { it.recordHistory = false } }
    val border = if (isCurrent) Pal.gold else Color(0x55FFFFFF)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .background(Pal.bgBottom, RoundedCornerShape(12.dp))
                .border(if (isCurrent) 2.dp else 1.dp, border, RoundedCornerShape(12.dp))
                .pointerInput(index) { detectTapGestures { onClick() } },
        ) {
            Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                val level = world.level
                val scale = minOf(size.width / (level.width * TILE), size.height / (level.height * TILE))
                val ox = (size.width - level.width * TILE * scale) / 2
                val oy = (size.height - level.height * TILE * scale) / 2
                drawWorld(world, 0f, ox, oy, scale)
            }
            if (completed) {
                Canvas(Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp)) { drawCheck() }
            }
        }
        BasicText(
            "Level ${index + 1}",
            Modifier.padding(top = 4.dp),
            style = TextStyle(color = if (isCurrent) Pal.gold else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** Green circle with a white check mark. */
private fun DrawScope.drawCheck() {
    val r = size.minDimension / 2
    val c = Offset(size.width / 2, size.height / 2)
    drawCircle(CheckGreen, r, c)
    val w = size.width
    val h = size.height
    val stroke = w * 0.12f
    drawLine(Color.White, Offset(w * 0.27f, h * 0.52f), Offset(w * 0.43f, h * 0.68f), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(Color.White, Offset(w * 0.43f, h * 0.68f), Offset(w * 0.74f, h * 0.33f), strokeWidth = stroke, cap = StrokeCap.Round)
}




