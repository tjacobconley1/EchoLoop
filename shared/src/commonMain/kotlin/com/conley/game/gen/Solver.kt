package com.conley.game.gen

import com.conley.game.sim.InputFrame
import com.conley.game.sim.Level
import com.conley.game.sim.LoopSession
import com.conley.game.sim.Recording
import com.conley.game.sim.Rect
import com.conley.game.sim.Status
import com.conley.game.sim.World

/** A verified solution: one input list per loop (the last loop reaches the goal; earlier loops leave ghosts). */
class Solution(
    val loops: List<List<InputFrame>>,
    val ticks: Int,
    val jumps: Int,
    val turns: Int,
    val nodes: Int,
)

/** Where a loop must end to park its ghost: standing on a plate, or standing in an anchor cell. */
private class Target(val rect: Rect, val isPlate: Boolean, val hover: Boolean = false) {
    fun reached(w: World): Boolean {
        val p = w.playerRect()
        if (hover) {
            val cx = (p.left + p.right) / 2
            val cy = (p.top + p.bottom) / 2
            return cx >= rect.left && cx < rect.right && cy >= rect.top && cy < rect.bottom
        }
        if (!w.onGround) return false
        return if (isPlate) p.overlaps(rect)
        else {
            val cx = (p.left + p.right) / 2
            val cy = (p.top + p.bottom) / 2
            cx >= rect.left && cx < rect.right && cy >= rect.top && cy < rect.bottom
        }
    }
}

private class IntList(cap: Int = 4096) {
    var a = IntArray(cap)
    var n = 0
    fun add(v: Int) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
    operator fun get(i: Int) = a[i]
}

/**
 * Exhaustive breadth-first solver over the real simulation. Decisions are made every [M] ticks (run / idle, with an
 * optional jump on the first tick). Ghost puzzles are solved by searching loop by loop: park a ghost on a plate or
 * anchor, then search again with that ghost in the world.
 */
object Solver {
    private const val M = 3

    fun solve(level: Level, nodeBudget: Int = 400_000, maxGhosts: Int = 3): Solution? {
        val targets = ArrayList<Target>()
        for ((_, r) in level.plates) targets.add(Target(r, true))
        for (r in level.anchors) targets.add(Target(r, false))
        for (r in level.hovers) targets.add(Target(r, false, hover = true))
        var spent = 0

        fun rec(ghosts: List<Recording>, used: Int, loops: List<List<InputFrame>>): List<List<InputFrame>>? {
            val goal = bfs(level, ghosts, null, nodeBudget - spent)
            spent += goal.nodes
            if (goal.inputs != null) return loops + listOf(goal.inputs)
            if (ghosts.size >= maxGhosts || spent >= nodeBudget) return null
            for (t in targets.indices) {
                if ((used shr t) and 1 != 0) continue
                val r = bfs(level, ghosts, targets[t], nodeBudget - spent)
                spent += r.nodes
                val inputs = r.inputs ?: continue
                val w = World(level, ghosts)
                for (i in inputs) w.step(i)
                val sub = rec(ghosts + w.recording(), used or (1 shl t), loops + listOf(inputs))
                if (sub != null) return sub
                if (spent >= nodeBudget) return null
            }
            return null
        }

        val loops = rec(emptyList(), 0, emptyList()) ?: return null
        return verify(level, loops, spent)
    }

    /** Replays the solution exactly as the game would; returns null if it does not really win. */
    fun verify(level: Level, loops: List<List<InputFrame>>, nodes: Int = 0): Solution? {
        val session = LoopSession(level)
        var ticks = 0; var jumps = 0; var turns = 0
        for ((idx, inputs) in loops.withIndex()) {
            val w = session.world
            for (inp in inputs) {
                if (inp.jump && w.onGround) jumps++
                val f = w.facing
                w.step(inp)
                if (w.facing != f) turns++
                ticks++
                if (w.status != Status.Running) break
            }
            if (idx < loops.size - 1) {
                if (w.status != Status.Running) return null
                w.end()
                session.finishLoop()
            } else if (w.status != Status.Won) return null
        }
        return Solution(loops, ticks, jumps, turns, nodes)
    }

    private class Result(val inputs: List<InputFrame>?, val nodes: Int)

    private fun bfs(level: Level, ghosts: List<Recording>, target: Target?, cap: Int): Result {
        if (cap <= 0) return Result(null, 0)
        val w = World(level, ghosts)
        w.recordHistory = false
        val px = IntList(); val py = IntList(); val pvy = IntList(); val pf = IntList(); val pg = IntList()
        val parent = IntList(); val act = IntList()
        fun add(x: Int, y: Int, vy: Int, f: Int, g: Int, p: Int, a: Int) {
            px.add(x); py.add(y); pvy.add(vy); pf.add(f); pg.add(g); parent.add(p); act.add(a)
        }
        add(level.start.first, level.start.second, 0, 1, 0, -1, -1)

        val maxMacro = minOf(level.maxTicks, 600) / M
        val ghostLen = ghosts.maxOfOrNull { it.size } ?: 0
        val timeMacros = if (ghosts.isEmpty()) 0 else (ghostLen + M - 1) / M + 1
        val visited = HashSet<Long>()
        var frontier = IntList(); frontier.add(0)

        fun path(node: Int, a: Int, partial: Int): List<InputFrame> {
            val acts = ArrayList<Int>()
            var n = node
            while (parent[n] != -1) { acts.add(act[n]); n = parent[n] }
            acts.reverse()
            val out = ArrayList<InputFrame>()
            for (ac in acts) for (j in 0 until M) out.add(InputFrame(ac and 1 != 0, ac and 2 != 0 && j == 0))
            for (j in 0 until partial) out.add(InputFrame(a and 1 != 0, a and 2 != 0 && j == 0))
            return out
        }

        for (macro in 0 until maxMacro) {
            val next = IntList()
            for (fi in 0 until frontier.n) {
                val node = frontier[fi]
                for (a in 0..3) {
                    w.load(px[node], py[node], pvy[node], pf[node], pg[node] == 1, macro * M)
                    var alive = true
                    for (j in 0 until M) {
                        w.step(a and 1 != 0, a and 2 != 0 && j == 0)
                        when (w.status) {
                            Status.Died, Status.TimedOut -> { alive = false }
                            Status.Won -> { if (target == null) return Result(path(node, a, j + 1), parent.n) else alive = false }
                            else -> if (target != null && target.reached(w)) return Result(path(node, a, j + 1), parent.n)
                        }
                        if (!alive) break
                    }
                    if (!alive) continue
                    val bucket = if (macro < timeMacros) macro else timeMacros
                    val key = (w.x.toLong() shl 38) or (w.y.toLong() shl 23) or ((w.vy + 1024).toLong() shl 12) or
                        ((if (w.facing == 1) 1L else 0L) shl 11) or ((if (w.onGround) 1L else 0L) shl 10) or bucket.toLong()
                    if (!visited.add(key)) continue
                    if (parent.n >= cap) return Result(null, parent.n)
                    add(w.x, w.y, w.vy, w.facing, if (w.onGround) 1 else 0, node, a)
                    next.add(parent.n - 1)
                }
            }
            if (next.n == 0) break
            frontier = next
        }
        return Result(null, parent.n)
    }
}


