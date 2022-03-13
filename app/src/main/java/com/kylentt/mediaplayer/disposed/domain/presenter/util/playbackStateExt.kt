package com.kylentt.mediaplayer.disposed.domain.presenter.util

import android.media.session.PlaybackState
import android.media.session.PlaybackState.STATE_PLAYING
import android.os.SystemClock


inline val PlaybackState.isPlaying
    get() = state == PlaybackState.STATE_PLAYING
            || state == PlaybackState.STATE_BUFFERING

inline val PlaybackState.isPrepared
    get() = state == PlaybackState.STATE_PLAYING
            || state == PlaybackState.STATE_BUFFERING
            || state == PlaybackState.STATE_PAUSED

inline val PlaybackState.isPlayEnabled
    get() = actions and PlaybackState.ACTION_PLAY != 0L
            || (actions and PlaybackState.ACTION_PLAY_PAUSE != 0L
                    && state == PlaybackState.STATE_PAUSED)


inline val PlaybackState.currentPlaybackPosition: Long
    get() = if(state == STATE_PLAYING) {
        val timeDelta = SystemClock.elapsedRealtime() - lastPositionUpdateTime
        (position + (timeDelta * playbackSpeed)).toLong()
    } else position