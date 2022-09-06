package com.kylentt.musicplayer.medialib.player

import androidx.annotation.IntRange
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Timeline.Period
import com.kylentt.musicplayer.medialib.player.component.VolumeManager
import com.kylentt.musicplayer.medialib.player.event.LibraryPlayerEventListener
import com.kylentt.musicplayer.medialib.player.playback.RepeatMode

interface LibraryPlayer {

	// TODO: wrap
	val availableCommands: Player.Commands
	val playbackParameters: PlaybackParameters

	// TODO: wrap
	val playWhenReady: Boolean
	val playbackState: PlaybackState
	val repeatMode: RepeatMode

	// TODO: wrap
	val isLoading: Boolean
	val isPlaying: Boolean

	// TODO: wrap
	val currentPeriod: Period?
	val currentPeriodIndex: Int
	val timeLine: Timeline
	val mediaItemCount: Int
	val currentMediaItem: MediaItem?
	val currentMediaItemIndex: Int
	val nextMediaItemIndex: Int
	val previousMediaItemIndex: Int

	// TODO: wrap
	val positionMs: Long
	val bufferedPositionMs: Long
	val bufferedDurationMs: Long
	val durationMs: Long

	val contextInfo: PlayerContextInfo
	val volumeManager: VolumeManager
	val released: Boolean

	// TODO: wrap
	val seekable: Boolean
	fun seekToDefaultPosition()
	fun seekToDefaultPosition(index: Int)
	fun seekToPosition(position: Long)
	@Throws(IndexOutOfBoundsException::class)
	fun seekToMediaItem(index: Int, startPosition: Long = 0L)
	fun seekToPrevious()
	fun seekToNext()
	fun seekToPreviousMediaItem()
	fun seekToNextMediaItem()

	fun setMediaItems(items: List<MediaItem>)
	fun play()
	fun pause()
	fun prepare()
	fun stop()


	fun addListener(listener: LibraryPlayerEventListener)
	fun removeListener(listener: LibraryPlayerEventListener)

	fun release()

	@Throws(IndexOutOfBoundsException::class)
	fun getMediaItemAt(@IntRange(from = 0, to = 2147483647) index: Int): MediaItem

	fun getAllMediaItems(@IntRange(from = 0, to = 2147483647) limit: Int = Int.MAX_VALUE): List<MediaItem>


	sealed class PlaybackState {
		object IDLE : PlaybackState()
		object BUFFERING : PlaybackState()
		object READY : PlaybackState()
		object ENDED : PlaybackState()

		companion object {
			inline val @Player.State Int.asPlaybackState
				get() = when(this) {
					Player.STATE_IDLE -> IDLE
					Player.STATE_BUFFERING -> BUFFERING
					Player.STATE_READY -> READY
					Player.STATE_ENDED -> ENDED
					else -> throw IllegalArgumentException("Tried to cast invalid: $this to: ${PlaybackState::class}")
				}

			inline val PlaybackState.toPlaybackStateInt
				get() = when(this) {
					IDLE -> Player.STATE_IDLE
					BUFFERING -> Player.STATE_BUFFERING
					READY -> Player.STATE_READY
					ENDED -> Player.STATE_ENDED
				}
		}
	}
}
