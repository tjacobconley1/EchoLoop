package com.conley.game.shared

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Plays the synthesized effects with one pre-loaded static [AudioTrack] per sound. */
class AndroidSoundPlayer : SoundPlayer {
    private val tracks: Map<Sound, AudioTrack> = Sound.entries.associateWith { sound ->
        val pcm = SoundSynth.render(sound)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SoundSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { it.write(pcm, 0, pcm.size) }
    }

    override fun play(sound: Sound) {
        val track = tracks[sound] ?: return
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }
}

