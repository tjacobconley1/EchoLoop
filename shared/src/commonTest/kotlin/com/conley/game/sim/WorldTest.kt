package com.conley.game.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldTest {
    private val run = InputFrame(move = true)

    @Test
    fun runsToGoal() {
        val level = Level(listOf("#######", "#P...G#", "#######"))
        val w = World(level)
        repeat(300) { w.step(run) }
        assertEquals(Status.Won, w.status)
    }

    @Test
    fun deterministic() {
        val level = Level(listOf("#########", "#P.....G#", "#########"))
        fun play(): Long {
            val w = World(level)
            for (t in 0 until 120) w.step(InputFrame(move = t % 7 != 0, jump = t % 23 == 0))
            return w.stateHash()
        }
        assertEquals(play(), play())
    }

    @Test
    fun fallsAndLands() {
        val level = Level(listOf("#####", "#..G#", "#P..#", "#####"))
        val w = World(level)
        repeat(30) { w.step(InputFrame(move = false)) }
        assertTrue(w.onGround)
    }

    @Test
    fun ghostHoldsPlateOpeningDoor() {
        val level = Level(listOf("#########", "#P.a.A.G#", "#########"))
        val session = LoopSession(level)
        repeat(25) { session.step(run) }
        session.world.end()
        session.finishLoop()
        assertEquals(2, session.loopNumber)

        // Without the ghost the door blocks the way; with it the level is solvable.
        repeat(400) { if (session.world.status == Status.Running) session.step(run) }
        assertEquals(Status.Won, session.world.status)
        session.finishLoop()
        assertTrue(session.solved)
    }

    @Test
    fun jumpClearsAboutThreeTilesButNotFour() {
        val level = Level(listOf("#####", "#...G", "#...#", "#...#", "#...#", "#P..#", "#####"))
        val w = World(level)
        repeat(3) { w.step(InputFrame(move = false)) }
        val floorY = w.y
        var minY = floorY
        w.step(InputFrame(move = false, jump = true))
        repeat(60) { w.step(InputFrame(move = false)); if (w.y < minY) minY = w.y }
        val height = floorY - minY
        assertTrue(height in 2500..3000, "jump height was $height")
    }

    @Test
    fun spikeJumpHasForgivingTimingWindow() {
        val level = Level(listOf("###########", "#.........#", "#.........#", "#.........#", "#P...^..G.#", "###########"))
        var wins = 0
        for (t0 in 0..60) {
            val w = World(level)
            for (t in 0 until 300) w.step(InputFrame(move = true, jump = t == t0))
            if (w.status == Status.Won) wins++
        }
        assertTrue(wins >= 8, "only $wins jump timings clear the spike")
    }
    @Test
    fun doorBlocksWithoutGhost() {
        val level = Level(listOf("#########", "#P.a.A.G#", "#########"))
        val w = World(level)
        repeat(400) { w.step(run) }
        assertTrue(w.status != Status.Won)
    }
}



