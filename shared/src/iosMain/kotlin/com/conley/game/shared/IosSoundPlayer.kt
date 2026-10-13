package com.conley.game.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

/** Plays the synthesized effects through AVAudioPlayer (the PCM is wrapped as a WAV in memory). */
@OptIn(ExperimentalForeignApi::class)
class IosSoundPlayer : SoundPlayer {
    private val players: Map<Sound, AVAudioPlayer?> = Sound.entries.associateWith { sound ->
        val wav = SoundSynth.wav(SoundSynth.render(sound))
        val data = wav.usePinned { NSData.create(bytes = it.addressOf(0), length = wav.size.toULong()) }
        AVAudioPlayer(data = data, error = null).also { it.prepareToPlay() }
    }

    override fun play(sound: Sound) {
        val p = players[sound] ?: return
        p.currentTime = 0.0
        p.play()
    }
}

