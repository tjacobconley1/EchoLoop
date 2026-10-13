package com.conley.game.sim

/** Manages the loops of one level: each finished loop becomes a ghost for the next. */
class LoopSession(val level: Level) {
    private val ghostList = ArrayList<Recording>()
    var world = World(level, ghostList.toList()); private set
    var solved = false; private set

    val loopNumber: Int get() = ghostList.size + 1
    val ghosts: List<Recording> get() = ghostList

    fun step(input: InputFrame) = world.step(input)

    /** Call once the world is no longer Running. Won solves the level, Died discards the loop, otherwise it becomes a ghost. */
    fun finishLoop() {
        when (world.status) {
            Status.Running -> return
            Status.Won -> solved = true
            Status.Died -> Unit
            Status.TimedOut, Status.Ended -> ghostList.add(world.recording())
        }
        if (!solved) world = World(level, ghostList.toList())
    }

    fun restartLevel() {
        ghostList.clear()
        solved = false
        world = World(level, emptyList())
    }
}

