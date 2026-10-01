package com.calcquest.kids.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.calcquest.kids.R

enum class Sfx { CORRECT, TRY_AGAIN, COMPLETE }

/**
 * Short bundled feedback sounds, played only in response to taps while the app is visible.
 * Uses the media/game stream, so device volume and silent settings apply. No background audio.
 */
class SoundPlayer(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<Sfx, Int> = mapOf(
        Sfx.CORRECT to pool.load(context, R.raw.sfx_correct, 1),
        Sfx.TRY_AGAIN to pool.load(context, R.raw.sfx_try_again, 1),
        Sfx.COMPLETE to pool.load(context, R.raw.sfx_complete, 1),
    )

    fun play(sfx: Sfx) {
        val id = ids[sfx] ?: return
        pool.play(id, 0.8f, 0.8f, 1, 0, 1f)
    }
}
