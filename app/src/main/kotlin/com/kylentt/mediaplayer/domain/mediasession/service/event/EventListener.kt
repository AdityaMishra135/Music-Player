package com.kylentt.mediaplayer.domain.mediasession.service.event

import androidx.annotation.MainThread
import androidx.lifecycle.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.kylentt.mediaplayer.core.exoplayer.PlayerExtension.isOngoing
import com.kylentt.mediaplayer.core.exoplayer.PlayerExtension.isStateBuffering
import com.kylentt.mediaplayer.core.exoplayer.PlayerExtension.isStateEnded
import com.kylentt.mediaplayer.core.exoplayer.PlayerExtension.isStateIdle
import com.kylentt.mediaplayer.core.exoplayer.PlayerExtension.isStateReady
import com.kylentt.mediaplayer.domain.mediasession.service.MusicLibraryService
import com.kylentt.mediaplayer.helper.Preconditions.checkMainThread
import com.kylentt.mediaplayer.helper.Preconditions.checkState
import com.kylentt.mediaplayer.ui.activity.CollectionExtension.forEachClear
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import timber.log.Timber


/*
	Responsibility: Listen to Service Event and dispatch it to EventHandler

 */

@MainThread
class MusicLibraryServiceListener(
	private val service: MusicLibraryService,
	private val mediaEventHandler: MusicLibraryEventHandler,
	private val lifecycle: LifecycleOwner
) {

	private val playerListeners = mutableListOf<Pair<Player, Player.Listener>>()

	private val playerListenerImpl = object : Player.Listener {

		override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
			Timber.d("EventListener onPlayWhenReadyChanged")

			super.onPlayWhenReadyChanged(playWhenReady, reason)
			playWhenReadyChangedImpl(currentMediaSession)
		}

		override fun onPlaybackStateChanged(playbackState: Int) {
			Timber.d("EventListener onPlaybackStateChanged")

			super.onPlaybackStateChanged(playbackState)
			playerStateChangedImpl(currentMediaSession, playbackState)
		}

		override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
			super.onMediaItemTransition(mediaItem, reason)
			mediaItemTransitionImpl(currentMediaSession, reason)
		}

		override fun onRepeatModeChanged(repeatMode: Int) {
			super.onRepeatModeChanged(repeatMode)
			repeatModeChangedImpl(currentMediaSession, repeatMode)
		}

		override fun onPlayerError(error: PlaybackException) {
			super.onPlayerError(error)
			playerErrorImpl(currentMediaSession, error)
		}
	}

	val defaultPlayerLister: Player.Listener
		get() = playerListenerImpl

	val currentMediaSession: MediaSession
		get() = service.currentMediaSession

	val currentPlayer: Player
		get() = currentMediaSession.player

	val currentPlayerMediaItem: MediaItem?
		get() = currentPlayer.currentMediaItem

	val isPlayerStateIdle: Boolean
		get() = currentPlayer.playbackState.isStateIdle()

	val isPlayerStateBuffering: Boolean
		get() = currentPlayer.playbackState.isStateBuffering()

	val isPlayerStateReady: Boolean
		get() = currentPlayer.playbackState.isStateReady()

	val isPlayerStateEnded: Boolean
		get() = currentPlayer.playbackState.isStateEnded()

	val isPlayerOngoing: Boolean
		get() = currentPlayer.playbackState.isOngoing()

	val isPlayerPlaying: Boolean
		get() = currentPlayer.isPlaying

	val isPlayerPlayWhenReady: Boolean
		get() = currentPlayer.playWhenReady

	private var isRegistered: Boolean = false
	var isReleased: Boolean = false

	@MainThread
	fun start(stopSelf: Boolean) {
		checkMainThread()

		checkNotNull(service.baseContext) {
			"Make Sure to Initialize MusicLibraryService EventListener at least service.onCreate()"
		}

		startListener()
		if (stopSelf) {
			// TODO: listen to Player / MediaSession changes if for whatever reason it does happen
			val unReg = LifecycleEventObserver { _, event ->
				if (event == Lifecycle.Event.ON_DESTROY) {
					Timber.d("EventListener received ON_DESTROY callback, releasing Self")
					stopListener()
					release()
				}
			}
			lifecycle.lifecycle.addObserver(unReg)
		}
	}

	@MainThread
	fun release() {
		checkMainThread()
		if (!isReleased) {
			playerListeners.forEachClear { it.first.removeListener(it.second) }
		}
	}

	fun registerPlayerListener(player: Player, listener: Player.Listener) {
		player.addListener(listener)
		this.playerListeners.add(player to listener)
	}

	fun unregisterPlayerListener(player: Player): Boolean {
		return this.playerListeners.removeAll {
			elseFalse(it.first === player) {
				it.first.removeListener(it.second)
			}
		}
	}

	fun unregisterPlayerListener(listener: Player.Listener): Boolean {
		return this.playerListeners.removeAll {
			elseFalse(listener === playerListeners) {
				it.first.removeListener(it.second)
			}
		}
	}

	fun unregisterPlayerListener(player: Player, listener: Player.Listener): Boolean {
		return this.playerListeners.removeAll {
			elseFalse(it.first === player && it.second == listener) {
				it.first.removeListener(it.second)
			}
		}
	}

	fun unregisterAllListener(obj: Any) {
		checkMainThread()
		playerListeners.forEachClear { it.first.removeListener(it.second) }
		Timber.d("$obj made call to unregisterAllListener")
	}

	private fun startListener() {
		checkState(!isRegistered)
		registerPlayerListener(currentMediaSession.player, defaultPlayerLister)
		isRegistered = true
	}

	private fun stopListener() {
		checkState(isRegistered)
		unregisterPlayerListener(currentMediaSession.player, defaultPlayerLister)
		isRegistered = false
	}

	private var playWhenReadyChangedJob = Job().job
	private fun playWhenReadyChangedImpl(session: MediaSession) {
		playWhenReadyChangedJob.cancel()
		playWhenReadyChangedJob = service.mainScope.launch {
			mediaEventHandler.handlePlayerPlayWhenReadyChanged(session)
		}
	}

	private var mediaItemTransitionJob = Job().job
	private fun mediaItemTransitionImpl(session: MediaSession, reason: Int) {
		mediaItemTransitionJob.cancel()
		mediaItemTransitionJob = service.mainScope.launch {
			mediaEventHandler.handlePlayerMediaItemChanged(currentMediaSession, reason)
		}
	}

	private var playerErrorJob = Job().job
	private fun playerErrorImpl(session: MediaSession, error: PlaybackException) {
		playerErrorJob.cancel()
		playerErrorJob = service.mainScope.launch {
			mediaEventHandler.handlePlayerError(session, error)
		}
	}

	private var playerStateChangedJob = Job().job
	private fun playerStateChangedImpl(session: MediaSession, playbackState: @Player.State Int) {
		playerStateChangedJob.cancel()
		playerStateChangedJob = service.mainScope.launch {
			mediaEventHandler.handlePlayerStateChanged(session)
		}
	}

	private var repeatModeChangedJob = Job().job
	private fun repeatModeChangedImpl(session: MediaSession, mode: Int) {
		repeatModeChangedJob.cancel()
		repeatModeChangedJob = service.mainScope.launch {
			mediaEventHandler.handlePlayerRepeatModeChanged(session)
		}
	}

	private inline fun elseFalse(condition: Boolean, block: () -> Unit): Boolean {
		return if (condition) {
			block()
			condition
		} else {
			false
		}
	}
}
