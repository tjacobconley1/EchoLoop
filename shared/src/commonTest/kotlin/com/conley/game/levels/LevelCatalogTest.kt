package com.conley.game.levels

import com.conley.game.gen.Solver
import com.conley.game.sim.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LevelCatalogTest {
    @Test
    fun thereAreOneHundredWellFormedLevels() {
        assertEquals(100, LevelCatalog.count)
        for (i in 0 until LevelCatalog.count) {
            val level = LevelCatalog.get(i).level
            val text = level.rows.joinToString("")
            assertEquals(1, text.count { it == 'P' }, "level ${i + 1} needs one start")
            assertEquals(1, text.count { it == 'G' }, "level ${i + 1} needs one goal")
            assertTrue(level.rows.all { it.length == level.width }, "level ${i + 1} must be rectangular")
            assertEquals(level.encode(), Level.decode(level.encode()).encode())
        }
    }

    @Test
    fun everyLevelIsSolvable() {
        for (i in 0 until LevelCatalog.count) {
            val sol = Solver.solve(LevelCatalog.get(i).level)
            assertNotNull(sol, "level ${i + 1} has no solution")
        }
    }

    @Test
    fun levelsAreAllDifferent() {
        val seen = HashSet<String>()
        for (i in 0 until LevelCatalog.count) seen.add(LevelCatalog.get(i).level.rows.joinToString("|"))
        assertEquals(LevelCatalog.count, seen.size)
    }
}

