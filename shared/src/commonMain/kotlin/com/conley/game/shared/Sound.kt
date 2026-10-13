package com.conley.game.shared

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class Sound { Jump, Death, Portal }

/** Plays short sound effects. Implemented per platform; [NoSound] is used when audio isn't available. */
interface SoundPlayer {
    fun play(sound: Sound)
}

object NoSound : SoundPlayer {
    override fun play(sound: Sound) {}
}

/** Generates the sound effects as raw 16-bit mono PCM, so no audio files need to ship with the app. */
object SoundSynth {
    const val SAMPLE_RATE = 22050

    fun render(sound: Sound): ShortArray = when (sound) {
        Sound.Jump -> jump()
        Sound.Death -> death()
        Sound.Portal -> portal()
    }

    /** Rising "boing": a quick upward sine sweep. */
    private fun jump(): ShortArray {
        val n = (SAMPLE_RATE * 0.16).toInt()
        var phase = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / n
            val freq = 320.0 + 520.0 * t
            phase += 2 * PI * freq / SAMPLE_RATE
            pcm(sin(phase) * (1.0 - t) * 0.55)
        }
    }

    /** Harsh descending buzz with a burst of noise. */
    private fun death(): ShortArray {
        val n = (SAMPLE_RATE * 0.38).toInt()
        var phase = 0.0
        var seed = 12345
        return ShortArray(n) { i ->
            val t = i.toDouble() / n
            val freq = 420.0 - 340.0 * t
            phase += 2 * PI * freq / SAMPLE_RATE
            seed = seed * 1103515245 + 12345
            val noise = ((seed ushr 16) and 0x7fff) / 16384.0 - 1.0
            val square = if (sin(phase) >= 0) 1.0 else -1.0
            pcm((square * 0.35 + noise * 0.45 * exp(-9.0 * t)) * exp(-3.0 * t) * 0.9)
        }
    }

    /** Bright rising arpeggio for reaching the portal. */
    private fun portal(): ShortArray {
        val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        val per = (SAMPLE_RATE * 0.11).toInt()
        val tail = (SAMPLE_RATE * 0.25).toInt()
        val out = ShortArray(per * notes.size + tail)
        for ((k, f) in notes.withIndex()) {
            val start = k * per
            val len = if (k == notes.lastIndex) per + tail else per * 2
            for (i in 0 until len) {
                val idx = start + i
                if (idx >= out.size) break
                val t = i.toDouble() / SAMPLE_RATE
                val v = (sin(2 * PI * f * t) + 0.35 * sin(4 * PI * f * t)) * exp(-7.0 * t) * 0.35
                out[idx] = (out[idx] + pcm(v)).coerceIn(-32767, 32767).toShort()
            }
        }
        return out
    }

    private fun pcm(v: Double): Short = (v.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()

    /** Wraps PCM in a minimal WAV container (used on iOS). */
    fun wav(samples: ShortArray): ByteArray {
        val dataLen = samples.size * 2
        val out = ByteArray(44 + dataLen)
        fun put32(at: Int, v: Int) { for (b in 0..3) out[at + b] = (v shr (8 * b)).toByte() }
        fun put16(at: Int, v: Int) { out[at] = v.toByte(); out[at + 1] = (v shr 8).toByte() }
        "RIFF".encodeToByteArray().copyInto(out, 0)
        put32(4, 36 + dataLen)
        "WAVEfmt ".encodeToByteArray().copyInto(out, 8)
        put32(16, 16); put16(20, 1); put16(22, 1)
        put32(24, SAMPLE_RATE); put32(28, SAMPLE_RATE * 2); put16(32, 2); put16(34, 16)
        "data".encodeToByteArray().copyInto(out, 36)
        put32(40, dataLen)
        for (i in samples.indices) put16(44 + i * 2, samples[i].toInt())
        return out
    }
}

