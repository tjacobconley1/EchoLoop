package com.conley.game.levels

import com.conley.game.sim.Level

internal class LevelDef(val title: String, val hint: String, val level: Level)

/** All playable levels: hand-built tutorials first, then the generated, bot-verified levels. */
internal object LevelCatalog {
    private val tutorials = listOf(
        LevelDef(
            "Level 1",
            "Reach the green portal.\nHOLD the left side of the screen to run.",
            Level(listOf(
                "###########",
                "#.........#",
                "#P.......G#",
                "###########",
            )),
        ),
        LevelDef(
            "Level 2",
            "Red spikes hurt!\nKeep holding the left side to run and TAP the right side to jump over them.",
            Level(listOf(
                "###########",
                "#.........#",
                "#.........#",
                "#.........#",
                "#P...^..G.#",
                "###########",
            )),
        ),
        LevelDef(
            "Level 3",
            "Time your jumps.",
            Level(listOf(
                "#############",
                "#...........#",
                "#...........#",
                "#...........#",
                "#P..^...^..G#",
                "#############",
            )),
        ),
        LevelDef(
            "Level 4",
            "A glowing door opens while something stands on the plate of the same colour.\n" +
                "Run onto the plate, then tap END LOOP. Your past self stays there as a ghost holding it. Now run again!",
            Level(listOf(
                "#########",
                "#P.a.A.G#",
                "#########",
            )),
        ),
        LevelDef(
            "Level 5",
            "Every loop adds another ghost. Use one for each plate, then walk through.",
            Level(listOf(
                "#############",
                "#P.a.A.b.B.G#",
                "#############",
            )),
        ),
        LevelDef(
            "Level 6",
            "The ledge is too high to jump to. Ghosts are platforms!\n" +
                "Run to the wall and END LOOP. Next loop, wait a moment so your ghost arrives first, then jump on it and jump again.",
            Level(listOf(
                "############",
                "#..........#",
                "#.......G..#",
                "#....#######",
                "#....#######",
                "#P.**#######",
                "############",
            )),
        ),
        LevelDef(
            "Level 7",
            "You only ever run forward. To turn around, bump into a wall!\n" +
                "Run to the far wall, then come back for the portal.",
            Level(listOf(
                "###############",
                "#.............#",
                "#.............#",
                "#.............#",
                "#G...P..^.....#",
                "###############",
            )),
        ),
        LevelDef(
            "Level 8",
            "Ghosts stay exactly where you leave them — even in mid-air!\n" +
                "Jump into the gap, tap END LOOP while airborne, then use your ghost as a stepping stone.",
            Level(listOf(
                "##############",
                "#............#",
                "#............#",
                "#............#",
                "#P....~~...G.#",
                "#####....#####",
            )),
        ),
    )

    private val cache = HashMap<Int, LevelDef>()

    val count: Int get() = tutorials.size + GENERATED_LEVELS.size

    /** [index] is zero-based. */
    fun get(index: Int): LevelDef {
        val i = index.coerceIn(0, count - 1)
        if (i < tutorials.size) return tutorials[i]
        return cache.getOrPut(i) {
            LevelDef("Level ${i + 1}", "", Level.decode(GENERATED_LEVELS[i - tutorials.size]))
        }
    }
}




