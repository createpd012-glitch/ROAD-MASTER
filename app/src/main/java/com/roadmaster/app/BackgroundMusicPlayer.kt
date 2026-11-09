package com.roadmaster.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * A short, original synthesized arcade-style loop. No external music files or copyrighted tracks.
 */
object BackgroundMusicPlayer {
    private var audioTrack: AudioTrack? = null

    @Synchronized
    fun start() {
        if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) return
        stop()
        val sampleRate = 22050
        val notes = doubleArrayOf(
            523.25, 659.25, 783.99, 659.25, 587.33, 698.46, 880.00, 698.46,
            523.25, 659.25, 783.99, 1046.50, 987.77, 783.99, 698.46, 659.25,
            587.33, 698.46, 880.00, 1046.50, 880.00, 783.99, 698.46, 587.33,
            523.25, 659.25, 783.99, 880.00, 783.99, 698.46, 659.25, 523.25
        )
        val noteSamples = (sampleRate * 0.19).toInt()
        val pcm = ShortArray(noteSamples * notes.size)
        var phase = 0.0
        for (i in notes.indices) {
            val frequency = notes[i]
            for (j in 0 until noteSamples) {
                val t = j.toDouble() / sampleRate
                val envelope = (1.0 - j.toDouble() / noteSamples).coerceIn(0.0, 1.0)
                val lead = sin(phase + 2.0 * PI * frequency * t) * 0.55 +
                    sin(phase + 4.0 * PI * frequency * t) * 0.12
                val bass = sin(2.0 * PI * (frequency / 4.0) * t) * 0.22
                val value = ((lead + bass) * envelope * 0.58).coerceIn(-1.0, 1.0)
                pcm[i * noteSamples + j] = (value * Short.MAX_VALUE).toInt().toShort()
            }
            phase += 0.17
        }
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        val written = track.write(pcm, 0, pcm.size)
        if (written > 0) {
            track.setLoopPoints(0, written, -1)
            track.setVolume(0.42f)
            track.play()
            audioTrack = track
        } else {
            track.release()
        }
    }

    @Synchronized
    fun stop() {
        audioTrack?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        audioTrack = null
    }
}
