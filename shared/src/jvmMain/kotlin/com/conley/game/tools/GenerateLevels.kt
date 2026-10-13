package com.conley.game.tools

import com.conley.game.gen.Candidate
import com.conley.game.gen.PuzzleGen
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Generates puzzle levels, keeps the ones the solver can beat, picks a difficulty-ordered selection and saves them.
 *   ./gradlew :shared:jvmRun --args="<seedsPerFamily> <levelsToKeep>"
 * Output: shared/src/commonMain/kotlin/com/conley/game/levels/GeneratedLevels.kt (+ shared/levels.txt)
 */
fun main(args: Array<String>) {
    val perFamily = args.getOrNull(0)?.toInt() ?: 400
    val keep = args.getOrNull(1)?.toInt() ?: 94
    val started = System.currentTimeMillis()
    val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    val jobs = ArrayList<Pair<Int, java.util.concurrent.Future<Candidate?>>>()
    for (family in PuzzleGen.FAMILIES.indices) for (seed in 0 until perFamily) {
        jobs.add(family to pool.submit(Callable { runCatching { PuzzleGen.candidate(seed, family) }.getOrNull() }))
    }
    val found = ArrayList<Candidate>()
    val seen = HashSet<String>()
    val perFam = IntArray(PuzzleGen.FAMILIES.size)
    for ((family, f) in jobs) {
        val c = f.get() ?: continue
        if (seen.add(c.signature)) { found.add(c); perFam[family]++ }
    }
    pool.shutdown()
    println("Solved candidates: ${found.size}  by family ${PuzzleGen.FAMILIES.zip(perFam.toList())}  in ${(System.currentTimeMillis() - started) / 1000}s")

    // Pick `keep` levels spread across the difficulty range, mixing families.
    val sorted = found.sortedBy { it.difficulty }
    val chosen = ArrayList<Candidate>()
    val used = HashSet<Candidate>()
    val famCount = HashMap<String, Int>()
    val window = maxOf(3, sorted.size / keep * 2)
    for (slot in 0 until minOf(keep, sorted.size)) {
        val center = slot * sorted.size / minOf(keep, sorted.size)
        val options = (maxOf(0, center - window)..minOf(sorted.size - 1, center + window)).map { sorted[it] }.filter { it !in used }
        val prevFamily = chosen.lastOrNull()?.family
        val pick = options.minWithOrNull(compareBy<Candidate>({ if (it.family == prevFamily) 1 else 0 }, { famCount[it.family] ?: 0 }, { Math.abs(sorted.indexOf(it) - center) }))
            ?: continue
        used.add(pick); chosen.add(pick); famCount[pick.family] = (famCount[pick.family] ?: 0) + 1
    }

    val dir = File("src/commonMain/kotlin/com/conley/game/levels").also { it.mkdirs() }
    val sb = StringBuilder()
    sb.appendLine("// GENERATED FILE - do not edit by hand. Regenerate with: ./gradlew :shared:jvmRun")
    sb.appendLine("package com.conley.game.levels")
    sb.appendLine()
    sb.appendLine("/** Puzzle levels ordered easy to hard; each was solved and verified by the level solver. Format: maxTicks;row|row|... */")
    sb.appendLine("internal val GENERATED_LEVELS: List<String> = listOf(")
    for (c in chosen) {
        val longest = c.solution.loops.maxOf { it.size }
        val maxTicks = (longest * 3).coerceIn(900, 1800)
        sb.appendLine("    \"$maxTicks;${c.rows.joinToString("|")}\",")
    }
    sb.appendLine(")")
    File(dir, "GeneratedLevels.kt").writeText(sb.toString())
    File("levels.txt").writeText(chosen.withIndex().joinToString("\n\n") { (i, c) ->
        val s = c.solution
        "Puzzle ${i + 1} (${c.family}) loops=${s.loops.size} ticks=${s.ticks} jumps=${s.jumps} turns=${s.turns} diff=${"%.1f".format(c.difficulty)}\n" + c.rows.joinToString("\n")
    })
    println("Saved ${chosen.size} levels. Families: $famCount. Total ${(System.currentTimeMillis() - started) / 1000}s")
}

