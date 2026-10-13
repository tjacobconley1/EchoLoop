package com.conley.game.sim

/** One tick of player input. One-touch: hold = run, tap = jump. */
data class InputFrame(val move: Boolean, val jump: Boolean = false)

/** A recorded run: player top-left position after each tick. Index 0 is the start position. */
class Recording(val xs: IntArray, val ys: IntArray) {
    val size: Int get() = xs.size
    fun xAt(tick: Int) = xs[minOf(tick, xs.lastIndex)]
    fun yAt(tick: Int) = ys[minOf(tick, ys.lastIndex)]
}

enum class Status { Running, Won, Died, TimedOut, Ended }

/**
 * Deterministic fixed-step simulation of one loop. Ghosts replay recorded positions exactly
 * (they never diverge) and act as one-way platforms and plate-pressers for the live player.
 *
 * Movement rules: the character only runs while the player holds, always in its facing direction, and it turns
 * around whenever it bumps into a wall (solid tile or closed door). The only way to change direction is a wall.
 */
class World(val level: Level, val ghosts: List<Recording> = emptyList()) {
    var x = level.start.first; private set
    var y = level.start.second; private set
    var vx = 0; private set
    var vy = 0; private set
    var facing = 1; private set
    var onGround = false; private set
    var tick = 0; private set
    var status = Status.Running; private set

    /** Bit i is set while plate 'a'+i is pressed. */
    private var pressedMask = 0

    /** Turn this off for search/solver use to skip recording the path. */
    var recordHistory = true
    private val xs = ArrayList<Int>().apply { add(x) }
    private val ys = ArrayList<Int>().apply { add(y) }

    init { updatePlates() }

    fun isPressed(plate: Char) = (pressedMask shr (plate - 'a')) and 1 != 0
    fun ghostRect(i: Int, atTick: Int = tick): Rect = box(ghosts[i].xAt(atTick), ghosts[i].yAt(atTick))
    fun playerRect(): Rect = box(x, y)

    fun recording() = Recording(xs.toIntArray(), ys.toIntArray())

    /** Manually end the loop (the player stays where they are and becomes a ghost). */
    fun end() { if (status == Status.Running) status = Status.Ended }

    /** Restores a state captured earlier (used by the level solver). */
    fun load(px: Int, py: Int, pvy: Int, pfacing: Int, pOnGround: Boolean, atTick: Int) {
        x = px; y = py; vx = 0; vy = pvy; facing = pfacing; onGround = pOnGround; tick = atTick
        status = Status.Running
        updatePlates()
    }

    fun step(input: InputFrame) = step(input.move, input.jump)

    fun step(move: Boolean, jump: Boolean) {
        if (status != Status.Running) return

        vx = if (move) facing * RUN_SPEED else 0
        if (jump && onGround) vy = JUMP_SPEED
        vy = minOf(vy + GRAVITY, MAX_FALL)

        // Horizontal
        x += vx
        var ty0 = y / TILE - 1; var ty1 = (y + Level.PLAYER_H) / TILE + 1
        var tx0 = x / TILE - 1; var tx1 = (x + Level.PLAYER_W) / TILE + 1
        for (ty in ty0..ty1) for (tx in tx0..tx1) {
            if (level.isSolidTile(tx, ty)) resolveX(tx * TILE, ty * TILE, (tx + 1) * TILE, (ty + 1) * TILE)
        }
        for (d in level.doors.indices) {
            val door = level.doors[d]
            if (!isPressed(door.first)) resolveX(door.second.left, door.second.top, door.second.right, door.second.bottom)
        }
        // Vertical
        val prevBottom = y + Level.PLAYER_H
        y += vy
        onGround = false
        ty0 = y / TILE - 1; ty1 = (y + Level.PLAYER_H) / TILE + 1
        tx0 = x / TILE - 1; tx1 = (x + Level.PLAYER_W) / TILE + 1
        for (ty in ty0..ty1) for (tx in tx0..tx1) {
            if (level.isSolidTile(tx, ty)) resolveY(tx * TILE, ty * TILE, (tx + 1) * TILE, (ty + 1) * TILE)
        }
        for (d in level.doors.indices) {
            val door = level.doors[d]
            if (!isPressed(door.first)) resolveY(door.second.left, door.second.top, door.second.right, door.second.bottom)
        }
        for (i in ghosts.indices) {
            val gx = ghosts[i].xAt(tick)
            val gy = ghosts[i].yAt(tick)
            if (vy >= 0 && prevBottom <= gy &&
                x < gx + Level.PLAYER_W && x + Level.PLAYER_W > gx && y < gy + Level.PLAYER_H && y + Level.PLAYER_H > gy
            ) {
                y = gy - Level.PLAYER_H; vy = 0; onGround = true
            }
        }

        tick++
        if (recordHistory) { xs.add(x); ys.add(y) }
        updatePlates()

        var dead = y > (level.height + 2) * TILE
        if (!dead) for (h in level.hazards.indices) {
            val r = level.hazards[h]
            if (x < r.right && x + Level.PLAYER_W > r.left && y < r.bottom && y + Level.PLAYER_H > r.top) { dead = true; break }
        }
        val g = level.goal
        status = when {
            dead -> Status.Died
            x < g.right && x + Level.PLAYER_W > g.left && y < g.bottom && y + Level.PLAYER_H > g.top -> Status.Won
            tick >= level.maxTicks -> Status.TimedOut
            else -> Status.Running
        }
    }

    private fun resolveX(l: Int, t: Int, r: Int, b: Int) {
        if (x < r && x + Level.PLAYER_W > l && y < b && y + Level.PLAYER_H > t) {
            if (vx > 0) x = l - Level.PLAYER_W else if (vx < 0) x = r
            facing = -facing
            vx = 0
        }
    }

    private fun resolveY(l: Int, t: Int, r: Int, b: Int) {
        if (x < r && x + Level.PLAYER_W > l && y < b && y + Level.PLAYER_H > t) {
            if (vy > 0) { y = t - Level.PLAYER_H; onGround = true } else if (vy < 0) y = b
            vy = 0
        }
    }

    /** Order-sensitive hash of the full state; used to verify determinism. */
    fun stateHash(): Long {
        var h = 1125899906842597L
        for (v in intArrayOf(x, y, vx, vy, facing, tick, status.ordinal, if (onGround) 1 else 0))
            h = 31 * h + v
        for (i in xs.indices) h = 31 * h + xs[i] * 7 + ys[i]
        return h
    }

    private fun box(px: Int, py: Int) = Rect(px, py, px + Level.PLAYER_W, py + Level.PLAYER_H)

    private fun updatePlates() {
        var mask = 0
        for (p in level.plates.indices) {
            val plate = level.plates[p]
            val r = plate.second
            var on = x < r.right && x + Level.PLAYER_W > r.left && y < r.bottom && y + Level.PLAYER_H > r.top
            if (!on) for (i in ghosts.indices) {
                val gx = ghosts[i].xAt(tick); val gy = ghosts[i].yAt(tick)
                if (gx < r.right && gx + Level.PLAYER_W > r.left && gy < r.bottom && gy + Level.PLAYER_H > r.top) { on = true; break }
            }
            if (on) mask = mask or (1 shl (plate.first - 'a'))
        }
        pressedMask = mask
    }

    companion object {
        const val RUN_SPEED = 90
        const val GRAVITY = 45
        const val JUMP_SPEED = -530
        const val MAX_FALL = 700
    }
}

