package com.conley.game.shared

/** Tiny persistence interface so game progress, the player's name and high scores survive restarts. */
interface KeyValueStore {
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
}

/** Fallback used in previews/tests; nothing is saved. */
class InMemoryStore : KeyValueStore {
    private val ints = HashMap<String, Int>()
    private val strings = HashMap<String, String>()
    override fun getInt(key: String, default: Int) = ints[key] ?: default
    override fun putInt(key: String, value: Int) { ints[key] = value }
    override fun getString(key: String, default: String) = strings[key] ?: default
    override fun putString(key: String, value: String) { strings[key] = value }
}

internal const val KEY_LEVEL = "level_v2"
internal const val KEY_SCORE = "run_score"
internal const val KEY_NAME = "player_name"
internal const val KEY_MUTED = "sound_muted"
internal const val KEY_SCORES = "high_scores"
internal const val KEY_DONE = "levels_done"

/** Which levels the current player has completed, saved as a comma-separated list of zero-based indices. */
internal object Progress {
    fun load(store: KeyValueStore): Set<Int> =
        store.getString(KEY_DONE, "").split(',').mapNotNull { it.toIntOrNull() }.toSet()

    fun markDone(store: KeyValueStore, level: Int) {
        val done = load(store)
        if (level !in done) store.putString(KEY_DONE, (done + level).sorted().joinToString(","))
    }

    fun clear(store: KeyValueStore) = store.putString(KEY_DONE, "")
}

internal class ScoreEntry(val name: String, val score: Int, val level: Int)

/** Top-10 high score table kept in the [KeyValueStore]. */
internal object Scores {
    const val MAX = 10

    fun clean(name: String): String = name.filter { it != '|' && it != ';' && it != '\n' }.trim().take(14)

    fun load(store: KeyValueStore): List<ScoreEntry> =
        store.getString(KEY_SCORES, "").split(';').mapNotNull { part ->
            val p = part.split('|')
            if (p.size != 3) return@mapNotNull null
            ScoreEntry(p[0], p[1].toIntOrNull() ?: return@mapNotNull null, p[2].toIntOrNull() ?: 0)
        }

    /** Adds a finished run to the table (ignored when the score is zero). */
    fun submit(store: KeyValueStore, name: String, score: Int, level: Int) {
        if (score <= 0) return
        val all = (load(store) + ScoreEntry(clean(name).ifEmpty { "Player" }, score, level))
            .sortedByDescending { it.score }.take(MAX)
        store.putString(KEY_SCORES, all.joinToString(";") { "${it.name}|${it.score}|${it.level}" })
    }
}


