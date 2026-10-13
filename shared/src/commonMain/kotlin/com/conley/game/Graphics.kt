package com.conley.game.shared

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.conley.game.sim.Level
import com.conley.game.sim.TILE
import com.conley.game.sim.World
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal object Pal {
    val bgTop = Color(0xFF080B18)
    val bgBottom = Color(0xFF1D1B3F)
    val wall = Color(0xFF262B52)
    val wallEdge = Color(0xFF5560B8)
    val goal = Color(0xFF3DF5A0)
    val hero = Color(0xFFFFFFFF)
    val heroShade = Color(0xFFAFC2FF)
    val ghost = Color(0xFF58C8FF)
    val ghostShade = Color(0xFF7A6BFF)
    val spike = Color(0xFFFF4F6D)
    val gold = Color(0xFFFFD25A)
}

internal fun plateColor(id: Char): Color = when (id) {
    'a' -> Color(0xFFFFB347)
    'b' -> Color(0xFFFF5FC8)
    'c' -> Color(0xFF45E0C4)
    'd' -> Color(0xFFB6F25A)
    else -> Color(0xFFB07CFF)
}

/** Gradient sky with drifting, twinkling stars and soft nebula glows. */
internal fun DrawScope.drawBackdrop(time: Float) {
    drawRect(Brush.verticalGradient(listOf(Pal.bgTop, Pal.bgBottom)), size = size)
    val w = size.width
    val h = size.height
    for (k in 0 until 2) {
        val cx = w * (0.25f + 0.5f * k) + sin(time * 0.15f + k * 2f) * w * 0.08f
        val cy = h * (0.3f + 0.35f * k)
        val c = if (k == 0) Color(0x335A3DFF) else Color(0x2A00C2FF)
        drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(cx, cy), radius = w * 0.6f), w * 0.6f, Offset(cx, cy))
    }
    for (i in 0 until 80) {
        val layer = 1 + i % 3
        val baseX = ((i * 7919) % 1000) / 1000f * w
        val x = ((baseX - time * 6f * layer) % w + w) % w
        val y = ((i * 104729) % 1000) / 1000f * h
        val twinkle = 0.35f + 0.45f * (0.5f + 0.5f * sin(time * 1.6f + i))
        drawCircle(Color.White.copy(alpha = twinkle), 0.8f + layer * 0.6f, Offset(x, y))
    }
}

/** Pixel-space character: rounded body, glow and eyes. [x] is centre, [bottom] is feet. */
internal fun DrawScope.drawBuddy(
    x: Float, bottom: Float, w: Float, h: Float,
    body: Color, shade: Color, glow: Color, facing: Int, vy: Float, alpha: Float,
) {
    val st = (abs(vy).coerceAtMost(700f) / 700f) * 0.18f
    val bw = w * (1f - st)
    val bh = h * (1f + st)
    val left = x - bw / 2
    val top = bottom - bh
    val cy = top + bh / 2
    drawCircle(
        Brush.radialGradient(listOf(glow.copy(alpha = 0.45f * alpha), Color.Transparent), Offset(x, cy), h * 1.2f),
        h * 1.2f, Offset(x, cy),
    )
    drawRoundRect(
        Brush.verticalGradient(listOf(body.copy(alpha = alpha), shade.copy(alpha = alpha)), startY = top, endY = bottom),
        Offset(left, top), Size(bw, bh), CornerRadius(bw * 0.38f),
    )
    val ex = bw * 0.21f
    val ey = top + bh * 0.34f
    val shift = facing * bw * 0.06f
    for (side in listOf(-1, 1)) {
        val c = Offset(x + side * ex + shift, ey)
        drawCircle(Color.White.copy(alpha = alpha), bw * 0.15f, c)
        drawCircle(Color(0xFF14172B).copy(alpha = alpha), bw * 0.075f, c + Offset(facing * bw * 0.04f, 0f))
    }
}

internal const val DEATH_FX_SECONDS = 0.8f

/** Burst of shards plus a shock ring where the player died. [age] is seconds since death; [cx]/[cy] are pixels. */
private fun DrawScope.drawDeathBurst(cx: Float, cy: Float, size: Float, age: Float) {
    val p = (age / DEATH_FX_SECONDS).coerceIn(0f, 1f)
    val fade = 1f - p
    // Flash + shock ring
    drawCircle(Pal.spike.copy(alpha = 0.35f * fade * fade), size * (0.6f + 1.6f * p), Offset(cx, cy))
    drawCircle(Color.White.copy(alpha = 0.8f * fade), size * (0.3f + 1.4f * p), Offset(cx, cy), style = Stroke(size * 0.06f * fade + 1f))
    // Shards fly outwards, fall under gravity and shrink away
    val n = 16
    for (i in 0 until n) {
        val a = i * (2f * PI.toFloat() / n) + (i % 3) * 0.2f
        val speed = size * (1.3f + 0.9f * ((i * 37) % 10) / 10f)
        val dx = cos(a) * speed * p
        val dy = sin(a) * speed * p - size * 0.8f * p + size * 2.2f * p * p
        val c = when (i % 3) { 0 -> Color.White; 1 -> Pal.spike; else -> Pal.ghost }
        val r = size * (0.12f + 0.06f * (i % 4)) * (1f - 0.7f * p)
        drawCircle(c.copy(alpha = fade), r, Offset(cx + dx, cy + dy))
    }
}

internal fun DrawScope.drawWorld(
    world: World, time: Float, ox: Float, oy: Float, s: Float,
    deathX: Int = 0, deathY: Int = 0, deathAge: Float = -1f,
) {
    val level = world.level
    val tile = TILE * s
    fun px(v: Int) = v * s

    // Walls
    for (ty in 0 until level.height) for (tx in 0 until level.width) {
        if (!level.isSolidTile(tx, ty)) continue
        val x = ox + tx * tile
        val y = oy + ty * tile
        // Flat fill (a gradient per tile allocates a Brush for every wall tile every frame, which made iOS stutter).
        drawRoundRect(
            Pal.wall,
            Offset(x + 0.5f, y + 0.5f), Size(tile - 1f, tile - 1f), CornerRadius(tile * 0.14f),
        )
        if (!level.isSolidTile(tx, ty - 1))
            drawRoundRect(Pal.wallEdge, Offset(x + tile * 0.04f, y), Size(tile * 0.92f, tile * 0.1f), CornerRadius(tile * 0.05f))
    }

    // Door frames and plates (paired by colour)
    for ((id, r) in level.doors) {
        val c = plateColor(id)
        val open = world.isPressed(id)
        val x = ox + px(r.left); val y = oy + px(r.top); val w = px(r.right - r.left); val h = px(r.bottom - r.top)
        if (open) {
            drawRoundRect(c.copy(alpha = 0.35f), Offset(x + w * 0.35f, y), Size(w * 0.3f, h), CornerRadius(w * 0.1f), style = Stroke(2f))
        } else {
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent), Offset(x + w / 2, y + h / 2), h * 0.9f),
                h * 0.9f, Offset(x + w / 2, y + h / 2),
            )
            drawRoundRect(c, Offset(x + w * 0.3f, y), Size(w * 0.4f, h), CornerRadius(w * 0.1f))
            for (k in 1..3) drawRect(Color.White.copy(alpha = 0.35f), Offset(x + w * 0.3f, y + h * k / 4f - 1.5f), Size(w * 0.4f, 3f))
        }
    }
    for ((id, r) in level.plates) {
        val c = plateColor(id)
        val on = world.isPressed(id)
        val x = ox + px(r.left); val w = px(r.right - r.left)
        val ph = px(r.bottom - r.top) * (if (on) 0.5f else 1f)
        val y = oy + px(r.bottom) - ph
        if (on) drawCircle(
            Brush.radialGradient(listOf(c.copy(alpha = 0.6f), Color.Transparent), Offset(x + w / 2, y), tile * 0.9f),
            tile * 0.9f, Offset(x + w / 2, y),
        )
        drawRoundRect(c.copy(alpha = if (on) 1f else 0.7f), Offset(x + w * 0.1f, y), Size(w * 0.8f, ph), CornerRadius(ph / 2))
    }

    // Spikes (drawn slightly larger than the forgiving hitbox)
    for (r in level.hazards) {
        val x = ox + px(r.left - 150); val w = px(r.right - r.left + 300)
        val top = oy + px(r.top - 120); val bottom = oy + px(r.bottom)
        val glow = 0.5f + 0.5f * sin(time * 4f)
        for (k in 0 until 2) {
            val x0 = x + w / 2 * k
            val path = Path().apply {
                moveTo(x0, bottom); lineTo(x0 + w / 4, top); lineTo(x0 + w / 2, bottom); close()
            }
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFA1B3), Pal.spike), startY = top, endY = bottom))
        }
        drawCircle(
            Brush.radialGradient(listOf(Pal.spike.copy(alpha = 0.25f * glow), Color.Transparent), Offset(x + w / 2, bottom), tile * 0.7f),
            tile * 0.7f, Offset(x + w / 2, bottom),
        )
    }
    // Exit portal
    run {
        val g = level.goal
        val c = Offset(ox + px(g.left + g.right) / 2f, oy + px(g.top + g.bottom) / 2f)
        val pulse = 0.5f + 0.5f * sin(time * 3f)
        val gr = tile * (1.0f + 0.25f * pulse)
        drawCircle(Brush.radialGradient(listOf(Pal.goal.copy(alpha = 0.55f), Color.Transparent), c, gr), gr, c)
        drawCircle(Pal.goal, tile * 0.34f, c, style = Stroke(tile * 0.07f))
        drawCircle(Pal.goal.copy(alpha = 0.25f), tile * 0.3f, c)
        for (k in 0 until 6) {
            val a = time * 2f + k * (PI.toFloat() / 3f)
            drawCircle(Color.White, tile * 0.035f, c + Offset(cos(a), sin(a)) * (tile * 0.22f))
        }
    }

    // Ghosts with echo trails
    for (i in world.ghosts.indices) {
        val t = world.tick
        for (k in 3 downTo 1) {
            val tt = maxOf(0, t - k * 4)
            drawGhost(world, i, tt, ox, oy, s, 0.12f * (4 - k))
        }
        drawGhost(world, i, t, ox, oy, s, 0.75f)
    }

    // Player
    val p = world.playerRect()
    drawBuddy(
        ox + px(p.left + p.right) / 2f, oy + px(p.bottom), px(p.right - p.left), px(p.bottom - p.top),
        Pal.hero, Pal.heroShade, Color(0xFFBFD0FF), world.facing, world.vy.toFloat(), 1f,
    )

    if (deathAge in 0f..DEATH_FX_SECONDS) {
        drawDeathBurst(
            ox + px(deathX) + px(Level.PLAYER_W) / 2f, oy + px(deathY) + px(Level.PLAYER_H) / 2f,
            px(Level.PLAYER_H), deathAge,
        )
    }
}

private fun DrawScope.drawGhost(world: World, i: Int, tick: Int, ox: Float, oy: Float, s: Float, alpha: Float) {
    val r = world.ghostRect(i, tick)
    val prev = world.ghostRect(i, maxOf(0, tick - 1))
    val next = world.ghostRect(i, tick + 1)
    val facing = if (next.left - prev.left < 0) -1 else 1
    val bob = sin(world.tick * 0.2f + i) * 0.02f * (r.bottom - r.top) * s
    drawBuddy(
        ox + (r.left + r.right) / 2f * s, oy + r.bottom * s + bob, (r.right - r.left) * s, (r.bottom - r.top) * s,
        Pal.ghost, Pal.ghostShade, Pal.ghost, facing, (r.top - prev.top).toFloat() * 8f, alpha,
    )
}





