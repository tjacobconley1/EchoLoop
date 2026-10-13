package com.conley.game.sim

/** One tile is [TILE] fixed-point units. All simulation math uses Int so every platform is bit-identical. */
const val TILE = 1000

/** Integer axis-aligned rectangle in fixed-point units. */
data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun overlaps(o: Rect): Boolean =
        left < o.right && right > o.left && top < o.bottom && bottom > o.top
}

/**
 * A room parsed from ASCII art.
 *
 *  `#` solid   `.` empty   `P` player start   `G` goal   `^` hazard
 *  `a`..`e` pressure plate   `A`..`E` door that is open while the matching plate is pressed
 */
class Level(val rows: List<String>, val maxTicks: Int = 1200) {
    val width: Int = rows.maxOf { it.length }
    val height: Int = rows.size
    val start: Pair<Int, Int>
    val goal: Rect
    private val solid = BooleanArray(width * height)
    val hazards = ArrayList<Rect>()
    val plates = ArrayList<Pair<Char, Rect>>()
    val doors = ArrayList<Pair<Char, Rect>>()

    /** Cells marked `*` in the source. Invisible in play; the level solver uses them as "park a ghost here" hints. */
    val anchors = ArrayList<Rect>()

    /** Cells marked `~`: solver hint to park a ghost hovering mid-air here. */
    val hovers = ArrayList<Rect>()

    init {
        var s: Pair<Int, Int>? = null
        var g: Rect? = null
        for (ty in rows.indices) for (tx in rows[ty].indices) {
            val c = rows[ty][tx]
            val tile = Rect(tx * TILE, ty * TILE, (tx + 1) * TILE, (ty + 1) * TILE)
            when (c) {
                '#' -> solid[ty * width + tx] = true
                'P' -> s = (tx * TILE + (TILE - PLAYER_W) / 2) to ((ty + 1) * TILE - PLAYER_H)
                'G' -> g = tile
                '*' -> anchors.add(tile)
                '~' -> hovers.add(tile)
                '^' -> hazards.add(tile.copy(top = tile.top + 650, left = tile.left + 300, right = tile.right - 300))
                in 'a'..'e' -> plates.add(c to tile.copy(top = tile.bottom - 200))
                in 'A'..'E' -> doors.add(c.lowercaseChar() to tile)
            }
        }
        start = s ?: error("Level has no player start 'P'")
        goal = g ?: error("Level has no goal 'G'")
    }

    fun isSolidTile(tx: Int, ty: Int): Boolean {
        if (tx < 0 || tx >= width) return true
        if (ty < 0 || ty >= height) return false
        return solid[ty * width + tx]
    }

    companion object {
        const val PLAYER_W = 600
        const val PLAYER_H = 800

        /** Compact text form used for saved/bundled levels: `maxTicks;row|row|row`. */
        fun decode(text: String): Level {
            val (ticks, body) = text.split(';', limit = 2)
            return Level(body.split('|'), ticks.toInt())
        }
    }

    fun encode(): String = "$maxTicks;${rows.joinToString("|")}"
}




