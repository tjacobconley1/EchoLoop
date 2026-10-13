package com.conley.game.gen

import com.conley.game.sim.Level

/** Small deterministic xorshift RNG so a given seed always yields the same level on every platform. */
class Rng(seed: Int) {
    private var s = if (seed == 0) 0x1234567 else seed

    init { repeat(8) { next() } }

    fun next(): Int {
        s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5)
        return s
    }

    fun int(n: Int): Int = (next() ushr 1) % n
    fun pct(p: Int): Boolean = int(100) < p
}

/** ASCII canvas with a solid border. Row [h]-2 is the standing row on the bottom floor. */
private class Cv(val w: Int, val h: Int) {
    val c = Array(h) { CharArray(w) { '.' } }

    init {
        for (x in 0 until w) { c[0][x] = '#'; c[h - 1][x] = '#' }
        for (y in 0 until h) { c[y][0] = '#'; c[y][w - 1] = '#' }
    }

    fun set(r: Int, col: Int, ch: Char) { if (r in 0 until h && col in 0 until w) c[r][col] = ch }
    fun get(r: Int, col: Int): Char = if (r in 0 until h && col in 0 until w) c[r][col] else '#'
    fun rows(): List<String> = c.map { it.concatToString() }
}

class Candidate(val rows: List<String>, val family: String, val solution: Solution) {
    /** Rough difficulty: longer, more jumps, more wall-turns, and more ghosts all make a level harder. */
    val difficulty: Double
        get() = solution.ticks / 20.0 + solution.jumps * 1.5 + solution.turns * 3.0 + (solution.loops.size - 1) * 12.0
    val signature: String get() = rows.joinToString("|")
}

/**
 * Builds puzzle levels from hand-designed "families" with random structure, then keeps only the ones a
 * breadth-first solver can actually beat. The solver also measures how many jumps, wall-turns and ghost loops
 * the shortest solution needs, which is used to rank levels by difficulty and drop boring ones.
 */
object PuzzleGen {
    val FAMILIES = listOf("zigzag", "boomerang", "gate", "ghoststep", "terrain", "slabs", "hover", "vault")

    fun candidate(seed: Int, family: Int): Candidate? {
        val rng = Rng(seed * -1640531535 + family * 668265263 + 7)
        val base = when (family) {
            0 -> zigzag(rng)
            1 -> boomerang(rng)
            2 -> gate(rng)
            3 -> ghostStep(rng)
            4 -> terrain(rng, vault = false)
            5 -> slabs(rng)
            6 -> hoverBridge(rng)
            else -> terrain(rng, vault = true)
        } ?: return null
        // Try a decorated variant first (more detail); fall back to the plain layout if the decor breaks the puzzle.
        for (rows in listOf(decorate(base, rng), base)) {
            val sol = Solver.solve(Level(rows, 900)) ?: continue
            // Skip trivial levels: a good puzzle needs real jumping, wall-turns or ghost work.
            val complexity = sol.jumps + 2 * sol.turns + 6 * (sol.loops.size - 1)
            if (complexity < 11) continue
            if (sol.loops.size == 1 && sol.turns < 1) continue
            return Candidate(rows, FAMILIES[family], sol)
        }
        return null
    }

    /** Sprinkles solid lumps that cling to existing walls/floors/ceilings so every level has its own texture. */
    private fun decorate(rows: List<String>, rng: Rng): List<String> {
        val g = rows.map { it.toCharArray() }
        val h = g.size; val w = g[0].size
        fun special(r: Int, c: Int): Boolean {
            for (dr in -1..1) for (dc in -1..1) {
                val ch = g.getOrNull(r + dr)?.getOrNull(c + dc) ?: continue
                if (ch != '.' && ch != '#') return true
            }
            return false
        }
        repeat(3 + rng.int(5)) {
            val r = 1 + rng.int(h - 2); val c = 1 + rng.int(w - 2)
            if (g[r][c] != '.' || special(r, c)) return@repeat
            val touches = g[r - 1][c] == '#' || g[r + 1][c] == '#' || g[r][c - 1] == '#' || g[r][c + 1] == '#'
            if (touches) g[r][c] = '#'
        }
        return g.map { it.concatToString() }
    }

    // ------------------------------------------------------------------ zigzag tower

    /** Climb tiers that alternate sides; the only way to reverse direction is to bump the walls. */
    private fun zigzag(rng: Rng): List<String>? {
        val k = 1 + rng.int(3)
        val ds = IntArray(k) { 1 + rng.int(2) }
        val rise = ds.sum()
        val h = rise + 6
        val w = 12 + rng.int(5)
        val cv = Cv(w, h)
        val s = IntArray(k + 1)
        s[0] = h - 2
        for (i in 1..k) s[i] = s[i - 1] - ds[i - 1]
        var attachLeft = false
        val spans = ArrayList<IntRange>()
        val sides = ArrayList<Boolean>()
        for (i in 1..k) {
            val len = 4 + rng.int(maxOf(1, w - 8))
            val range = if (attachLeft) 1..len else (w - 1 - len)..(w - 2)
            for (col in range) cv.set(s[i] + 1, col, '#')
            spans.add(range); sides.add(attachLeft)
            attachLeft = !attachLeft
        }
        // Hazards on each surface.
        for (i in 0..k) {
            val range = if (i == 0) 3..(w - 4) else spans[i - 1].first.coerceAtLeast(2)..spans[i - 1].last
            repeat(rng.int(3)) {
                val col = range.first + 1 + rng.int(maxOf(1, range.last - range.first - 1))
                if (cv.get(s[i], col) == '.' && cv.get(s[i] + 1, col) == '#') cv.set(s[i], col, '^')
            }
        }
        if (rng.pct(50)) {
            val col = 4 + rng.int(maxOf(1, w - 8))
            if (cv.get(s[0] - 1, col) == '.' && cv.get(s[0], col) == '.') cv.set(s[0] + 1, col, '.')
        }
        cv.set(s[0], 1, 'P')
        cv.set(s[k], if (sides.last()) 1 else w - 2, 'G')
        return cv.rows()
    }

    // ------------------------------------------------------------------ boomerang gauntlet

    /** The goal is behind the start, so you must run the gauntlet, bounce off the far wall and run it back. */
    private fun boomerang(rng: Rng): List<String>? {
        val w = 13 + rng.int(4)
        val h = 6 + rng.int(2)
        val cv = Cv(w, h)
        val s = h - 2
        val startCol = 3 + rng.int(2)
        cv.set(s, startCol, 'P')
        cv.set(s, 1, 'G')
        var pos = startCol + 3
        repeat(2 + rng.int(4)) {
            val kind = rng.int(3)
            val wd = if (kind == 1) 1 + rng.int(2) else 1
            if (pos + wd > w - 3) return@repeat
            when (kind) {
                0 -> cv.set(s, pos, '^')
                1 -> for (i in 0 until wd) cv.set(s + 1, pos + i, '.')
                else -> {
                    cv.set(s, pos, '#')
                    if (rng.pct(40)) cv.set(s - 1, pos, '#')
                }
            }
            pos += wd + 2 + rng.int(2)
        }
        // A ceiling stalactite or two for texture.
        repeat(rng.int(3)) { cv.set(1, 3 + rng.int(w - 6), '#') }
        return cv.rows()
    }

    // ------------------------------------------------------------------ gates

    /** Plates on raised platforms open doors that split the room; ghosts hold the plates. */
    private fun gate(rng: Rng): List<String>? {
        val w = 15 + rng.int(2)
        val h = 7 + rng.int(2)
        val cv = Cv(w, h)
        val s = h - 2
        val gates = 1 + rng.int(2)
        cv.set(s, 1, 'P')
        cv.set(s, w - 2, 'G')
        var prev = 1
        val cols = ArrayList<Int>()
        for (i in 0 until gates) {
            val g = if (i == 0) 7 + rng.int(3) else prev + 5 + rng.int(2)
            if (g > w - 4) return null
            cols.add(g)
            for (r in 1..s) cv.set(r, g, 'A' + i)
            // Platform with the plate somewhere between the previous gate and this one.
            val p = prev + 1 + rng.int(maxOf(1, g - prev - 4))
            for (col in p..p + 2) cv.set(s - 1, col, '#')
            cv.set(s - 2, p + 1, 'a' + i)
            prev = g
        }
        // Hazards: floor spikes / pits away from platforms and gates.
        repeat(1 + rng.int(3)) {
            val col = 3 + rng.int(w - 6)
            if (col in cols || cv.get(s, col) != '.' || cv.get(s - 1, col) == '#') return@repeat
            if (cols.any { kotlin.math.abs(it - col) < 2 }) return@repeat
            if (rng.pct(60)) cv.set(s, col, '^') else cv.set(s + 1, col, '.')
        }
        return cv.rows()
    }

    // ------------------------------------------------------------------ ghost step

    /** A ledge three tiles up is unreachable alone; park a ghost in front of it and climb it. */
    private fun ghostStep(rng: Rng): List<String>? {
        val w = 12 + rng.int(5)
        val h = 8 + rng.int(2)
        val cv = Cv(w, h)
        val s = h - 2
        val c = 5 + rng.int(maxOf(1, w - 9))
        if (w - c < 5) return null
        for (r in s - 2..s) for (col in c..w - 2) cv.set(r, col, '#')
        val goalCol = c + 2 + rng.int(w - 2 - (c + 2) + 1)
        cv.set(s - 3, goalCol, 'G')
        cv.set(s, 1, 'P')
        for (a in 1..3) if (c - a >= 2) cv.set(s, c - a, '*')
        var col = 3
        repeat(1 + rng.int(2)) {
            if (col <= c - 3 && rng.pct(70)) { if (rng.pct(35)) cv.set(s + 1, col, '.') else cv.set(s, col, '^') }
            col += 3
        }
        var top = c + 3 + rng.int(2)
        repeat(rng.int(3)) {
            if (top <= goalCol - 2) cv.set(s - 3, top, '^')
            top += 3
        }
        repeat(rng.int(3)) { cv.set(1, 3 + rng.int(w - 6), '#') }
        return cv.rows()
    }

    // ------------------------------------------------------------------ terrain / vault

    /**
     * Random rolling terrain with steps, tall walls, pits, spikes and floating ledges. With [vault] set, a door splits
     * the room and a plate somewhere on the terrain opens it, so a ghost has to hold the plate.
     */
    private fun terrain(rng: Rng, vault: Boolean): List<String>? {
        val w = 14 + rng.int(3)
        val h = 9 + rng.int(2)
        val cv = Cv(w, h)
        val ht = IntArray(w)
        var cur = 0
        for (c in 1..w - 2) {
            val roll = rng.int(100)
            cur += when {
                roll < 45 -> 0
                roll < 62 -> 1
                roll < 79 -> -1
                roll < 87 -> 2
                roll < 93 -> -2
                else -> 3
            }
            cur = cur.coerceIn(0, 4)
            ht[c] = cur
        }
        ht[1] = 0
        for (c in 1..w - 2) for (r in h - 1 - ht[c]..h - 1) cv.set(r, c, '#')
        fun stand(c: Int) = h - 2 - ht[c]
        // Pits (only where both neighbours are at the same height as the floor level, so they stay jumpable).
        repeat(rng.int(3)) {
            val c = 3 + rng.int(w - 6)
            if (ht[c] == 0 && ht[c - 1] == 0 && ht[c + 1] == 0) cv.set(h - 1, c, '.')
        }
        // Spikes on flat stretches.
        repeat(1 + rng.int(4)) {
            val c = 2 + rng.int(w - 4)
            if (cv.get(stand(c), c) == '.' && cv.get(stand(c) + 1, c) == '#' && ht[c] == ht[c - 1] && ht[c] == ht[c + 1]) cv.set(stand(c), c, '^')
        }
        // Floating ledges.
        repeat(rng.int(3)) {
            val c = 2 + rng.int(w - 6)
            val len = 2 + rng.int(2)
            val r = h - 2 - ht[c] - (2 + rng.int(2))
            if (r in 2 until h - 2) for (i in 0 until len) if (cv.get(r, c + i) == '.') cv.set(r, c + i, '#')
        }
        cv.set(stand(1), 1, 'P')
        var gc = w - 2
        if (rng.pct(40)) gc = w / 2 + rng.int(w / 2 - 1)
        if (gc < 4) return null
        cv.set(stand(gc), gc, 'G')
        if (vault) {
            val g = 6 + rng.int(maxOf(1, w - 11))
            if (g >= gc - 1 || g <= 3) return null
            for (r in 1 until h - 1 - ht[g]) cv.set(r, g, 'A')
            // Plate on a flat cell left of the gate.
            val pc = 2 + rng.int(g - 3)
            if (cv.get(stand(pc), pc) != '.') return null
            cv.set(stand(pc), pc, 'a')
            if (cv.get(stand(g), g) != 'A') return null
        }
        return cv.rows()
    }

    // ------------------------------------------------------------------ slabs

    /** Two floors joined by holes in the slab between them, with walls to bounce off and spikes on each level. */
    private fun slabs(rng: Rng): List<String>? {
        val w = 14 + rng.int(3)
        val h = 9 + rng.int(2)
        val cv = Cv(w, h)
        val s = h - 2
        val slabRow = s - 2
        for (c in 1..w - 2) cv.set(slabRow, c, '#')
        // Holes in the slab (2 wide) and a step block to help reach them.
        val holes = ArrayList<Int>()
        repeat(1 + rng.int(2)) {
            val c = 3 + rng.int(w - 7)
            if (holes.none { kotlin.math.abs(it - c) < 4 }) { holes.add(c); cv.set(slabRow, c, '.'); cv.set(slabRow, c + 1, '.') }
        }
        // Lower floor.
        repeat(rng.int(3)) {
            val c = 3 + rng.int(w - 6)
            if (cv.get(s, c) == '.' && holes.none { kotlin.math.abs(it - c) < 3 }) cv.set(s, c, '^')
        }
        if (rng.pct(60)) { val c = 2 + rng.int(w - 4); if (cv.get(s, c) == '.') cv.set(s, c, '#') }
        // Upper floor.
        val u = slabRow - 1
        repeat(1 + rng.int(3)) {
            val c = 2 + rng.int(w - 4)
            if (cv.get(u, c) == '.' && holes.none { kotlin.math.abs(it - c) < 3 }) cv.set(u, c, '^')
        }
        // A second slab fragment higher up for the goal.
        val top = rng.pct(60)
        if (top) {
            val len = 4 + rng.int(4)
            val left = rng.pct(50)
            val range = if (left) 1..len else (w - 1 - len)..(w - 2)
            for (c in range) cv.set(u - 2, c, '#')
            cv.set(u - 3, if (left) 1 else w - 2, 'G')
        } else {
            cv.set(u, if (rng.pct(50)) 1 else w - 2, 'G')
        }
        cv.set(s, 1, 'P')
        return cv.rows()
    }

    // ------------------------------------------------------------------ hover bridge

    /**
     * A pit too wide to jump. Leave a ghost hovering in mid-air above it (end the loop mid-jump) and use it as a
     * stepping stone.
     */
    private fun hoverBridge(rng: Rng): List<String>? {
        val w = 14 + rng.int(3)
        val h = 7 + rng.int(2)
        val cv = Cv(w, h)
        val s = h - 2
        val p = 5 + rng.int(3)
        val len = 3 + rng.int(2)
        if (p + len > w - 4) return null
        for (c in p until p + len) cv.set(h - 1, c, '.')
        for (c in p + 1 until p + len - 1) { cv.set(s, c, '~'); cv.set(s - 1, c, '~') }
        cv.set(s, 1, 'P')
        cv.set(s, w - 2, 'G')
        if (rng.pct(70)) cv.set(s, 3 + rng.int(maxOf(1, p - 4)), '^')
        if (rng.pct(70)) cv.set(s, p + len + 1 + rng.int(maxOf(1, w - p - len - 4)), '^')
        if (rng.pct(40)) cv.set(1, 3 + rng.int(w - 6), '#')
        return cv.rows()
    }
}




