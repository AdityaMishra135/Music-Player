package com.flammky.musicplayer.domain.musiclib.service.manager

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import coil.Coil
import coil.size.Scale
import com.google.common.collect.ImmutableList
import com.flammky.mediaplayer.helper.Preconditions.checkState
import com.flammky.mediaplayer.helper.image.CoilHelper
import com.flammky.musicplayer.common.android.bitmap.bitmapfactory.BitmapSampler
import com.flammky.musicplayer.common.android.environment.DeviceInfo
import com.flammky.android.common.kotlin.coroutines.AndroidCoroutineDispatchers
import com.flammky.common.kotlin.coroutines.AutoCancelJob
import com.flammky.common.kotlin.comparable.clamp
import com.flammky.common.kotlin.coroutine.ensureCancellation
import com.flammky.musicplayer.core.sdk.VersionHelper
import com.flammky.musicplayer.domain.musiclib.media3.mediaitem.MediaItemFactory
import com.flammky.musicplayer.domain.musiclib.media3.mediaitem.MediaItemFactory.orEmpty
import com.flammky.musicplayer.domain.musiclib.media3.mediaitem.MediaItemInfo
import com.flammky.musicplayer.domain.musiclib.media3.mediaitem.MediaItemPropertyHelper.getDebugDescription
import com.flammky.musicplayer.domain.musiclib.media3.mediaitem.MediaItemPropertyHelper.mediaUri
import com.flammky.musicplayer.domain.musiclib.player.exoplayer.PlayerExtension.isOngoing
import com.flammky.musicplayer.domain.musiclib.player.exoplayer.PlayerExtension.isRepeatAll
import com.flammky.musicplayer.domain.musiclib.player.exoplayer.PlayerExtension.isRepeatOff
import com.flammky.musicplayer.domain.musiclib.player.exoplayer.PlayerExtension.isRepeatOne
import com.flammky.musicplayer.domain.musiclib.player.exoplayer.PlayerExtension.isStateEnded
import com.flammky.musicplayer.domain.musiclib.player.exoplayer.PlayerExtension.isStateIdle
import com.flammky.musicplayer.domain.musiclib.service.MusicLibraryService
import com.flammky.musicplayer.domain.musiclib.service.provider.MediaNotificationProvider
import com.flammky.musicplayer.ui.main.MainActivity
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.File
import kotlin.coroutines.coroutineContext

class MediaNotificationManager(
	private val notificationId: Int
) : MusicLibraryService.ServiceComponent() {
	private lateinit var provider: Provider
	private lateinit var dispatcher: Dispatcher

	private lateinit var appDispatchers: AndroidCoroutineDispatchers
	private lateinit var mainScope: CoroutineScope

	private lateinit var coilHelper: CoilHelper
	private lateinit var deviceInfo: DeviceInfo
	private lateinit var notificationManagerService: NotificationManager
	private lateinit var activityManagerService: ActivityManager

	private val eventListener = this.PlayerEventListener()
	val itemInfoIntentConverter = MediaItemInfo.IntentConverter()

	val isForegroundCondition: (
		MusicLibraryService.ComponentDelegate, MediaSession
	) -> Boolean = { delegate: MusicLibraryService.ComponentDelegate, session: MediaSession ->
		delegate.stateInteractor.getServiceForegroundCondition(session)
	}

	val isOnGoingCondition: (MediaSession) -> Boolean = { it.player.playbackState.isOngoing() }

	val interactor = Interactor()

	override fun create(serviceDelegate: MusicLibraryService.ServiceDelegate) {
		super.create(serviceDelegate)
		createImpl(serviceDelegate)
	}

	override fun serviceContextAttached(context: Context) {
		super.serviceContextAttached(context)
		initializeComponents(context)
	}

	override fun start(componentDelegate: MusicLibraryService.ComponentDelegate) {
		super.start(componentDelegate)
		startImpl(componentDelegate)
	}

	override fun release() {
		if (isReleased) return
		releaseImpl()
	}

	private fun createImpl(serviceDelegate: MusicLibraryService.ServiceDelegate) {

		val serviceJob = serviceDelegate.property.serviceMainJob
		appDispatchers = serviceDelegate.property.serviceDispatchers
		mainScope = CoroutineScope(appDispatchers.main + serviceJob)
	}

	private fun startImpl(componentDelegate: MusicLibraryService.ComponentDelegate) {
		componentDelegate.sessionInteractor.registerPlayerEventListener(eventListener)
	}

	private fun releaseImpl() {
		if (::mainScope.isInitialized) mainScope.cancel()
		if (isComponentInitialized) {
			dispatcher.release()
			provider.release()
		}
		if (isStarted) {
			componentDelegate.sessionInteractor.removePlayerEventListener(eventListener)
		}
	}

	fun getNotification(
		session: MediaSession,
		onGoing: Boolean
	): Notification = provider.fromMediaSession(session, onGoing, ChannelName)

	fun startForegroundService(
		session: MediaSession,
		onGoingNotification: Boolean
	) {
		if (!isStarted) return
		val notification = getNotification(session, onGoingNotification)
		componentDelegate.stateInteractor.startForegroundService(notification)
	}

	fun stopForegroundService(removeNotification: Boolean) {
		if (!isStarted) return
		componentDelegate.stateInteractor.stopForegroundService(removeNotification)
	}

	fun onUpdateNotification(session: MediaSession) {
		if (!isStarted) return
		val onGoing = isOnGoingCondition(session)
		val notification = provider.fromMediaSession(session, onGoing, ChannelName)
		dispatcher.updateNotification(notificationId, notification)
	}

	private var isComponentInitialized = false

	private fun initializeComponents(context: Context) {
		notificationManagerService = context.getSystemService(NotificationManager::class.java)!!
		activityManagerService = context.getSystemService(ActivityManager::class.java)!!
		coilHelper =
			CoilHelper(context.applicationContext, Coil.imageLoader(context.applicationContext))
		deviceInfo = DeviceInfo(context)
		initializeProvider(context)
		initializeDispatcher(context)
		isComponentInitialized = true
	}

	private fun initializeProvider(context: Context) {
		checkState(!::provider.isInitialized)
		provider = Provider(context)
	}

	private fun initializeDispatcher(context: Context) {
		checkState(!::dispatcher.isInitialized)
		dispatcher = Dispatcher()
	}

	private inner class Dispatcher {

		var isReleased: Boolean = false
			private set

		init {
			if (VersionHelper.hasOreo()) {
				createNotificationChannel(notificationManagerService)
			}
		}

		fun release() {
			if (isReleased) {
				return
			}

			isReleased = true
			Timber.d("MediaNotificationManager.Dispatcher released")
		}

		fun updateNotification(id: Int, notification: Notification) {
			if (isReleased) return

			notificationManagerService.notify(id, notification)
		}

		suspend fun suspendUpdateNotification(id: Int, notification: Notification) {
			if (isReleased) return

			coroutineContext.ensureActive()
			notificationManagerService.notify(id, notification)
		}

		private var validatorJob by AutoCancelJob()
		suspend fun dispatchNotificationValidator(
			id: Int,
			delay: Long = 500,
			repeat: Int = 2,
			onUpdate: suspend () -> Notification
		) {
			if (isReleased) return
			coroutineContext.ensureActive()
			validatorJob = mainScope.launch {
				repeat(repeat) {
					delay(delay)
					coroutineContext.ensureActive()
					notificationManagerService.notify(id, onUpdate())
				}
			}
		}

		fun cancelValidatorJob(): Unit {
			if (isReleased) return
			validatorJob.cancel()
		}

		@RequiresApi(VERSION_CODES.O)
		private fun createNotificationChannel(manager: NotificationManager) {
			val importance = NotificationManager.IMPORTANCE_LOW
			val channel = NotificationChannel(ChannelName, NotificationName, importance)
			manager.createNotificationChannel(channel)
		}
	}


	private inner class Provider(private val context: Context) : MediaNotification.Provider {

		private val NO_BITMAP = Bitmap.createBitmap(1,1, Bitmap.Config.ALPHA_8)

		private val MediaItem.embedSizeMB
			get() = mediaMetadata.extras?.getFloat("embedSizeMB") ?: -1f

		private fun MediaItem.putEmbedSize(mb: Float) {
			mediaMetadata.extras?.putFloat("embedSizeMB", mb.clamp(0f, Float.MAX_VALUE))
		}

		private val emptyItem = MediaItem.fromUri("empty")

		private val lruCache: com.flammky.android.medialib.temp.cache.lru.LruCache<String, Bitmap> = com.flammky.android.medialib.temp.MediaLibrary.API.imageRepository.sharedBitmapLru
		private var localStored: Pair<String, Bitmap> = "" to NO_BITMAP

		// config later
		private val cacheConfig
			get() = true

		private var currentItemBitmap: Pair<MediaItem, Bitmap?> = MediaItem.EMPTY to null

		private val notificationProvider = MediaNotificationProvider(context, itemInfoIntentConverter)

		init {
			notificationProvider.setActionReceiver(NotificationActionReceiver())
		}

		var isReleased: Boolean = false
			private set

		fun release() {
			if (isReleased) {
				return
			}

			notificationProvider.release()
			currentItemBitmap = MediaItem.EMPTY to null

			isReleased = true
			Timber.d("MediaNotificationManager.Provider released()")
		}

		override fun createNotification(
			session: MediaSession,
			customLayout: ImmutableList<CommandButton>,
			actionFactory: MediaNotification.ActionFactory,
			onNotificationChangedCallback: MediaNotification.Provider.Callback
		): MediaNotification = createNotificationInternalImpl(session)

		override fun handleCustomCommand(
			session: MediaSession,
			action: String,
			extras: Bundle
		): Boolean = false

		fun fromMediaSession(
			session: MediaSession,
			onGoing: Boolean,
			channelName: String
		): Notification = getNotificationFromMediaSession(session, onGoing, channelName)

		private fun getNotificationFromPlayer(
			player: Player,
			onGoing: Boolean,
			channelName: String
		): Notification {

			Timber.d(
				"getNotificationFromPlayer for "
					+ player.currentMediaItem.orEmpty().getDebugDescription()
			)

			val largeIcon = getItemBitmap(player)
			return notificationProvider.buildMediaStyleNotification(
				player,
				largeIcon,
				onGoing,
				channelName
			)
		}

		private fun getNotificationFromMediaSession(
			session: MediaSession,
			onGoing: Boolean,
			channelName: String
		): Notification {

			Timber.d(
				"getNotificationFromMediaSession for " +
					session.player.currentMediaItem.orEmpty().getDebugDescription()
			)

			val largeIcon = getItemBitmap(session.player)
			return notificationProvider.buildMediaStyleNotification(
				session,
				largeIcon,
				onGoing,
				channelName
			)
		}

		suspend fun ensureCurrentItemBitmap(player: Player) {
			if (currentItemBitmap.first === emptyItem) updateItemBitmap(player)
		}

		suspend fun updateItemBitmap(
			player: Player,
			currentCompleted: suspend () -> Unit = {}
		): Unit = withContext(this@MediaNotificationManager.appDispatchers.main) {
			val currentItem = player.currentMediaItem.orEmpty()
			val bitmap = (getItemBitmap(currentItem, true)?.second ?: NO_BITMAP)
			localStored = currentItem.mediaId to bitmap
			currentCompleted()
		}

		private suspend fun getItemBitmap(item: MediaItem, cache: Boolean): Pair<MediaItem, Bitmap?>? = withContext(this@MediaNotificationManager.appDispatchers.io) {

			try {

				if (cache) {
					lruCache.get(item.mediaId + "500")?.let { return@withContext item to it }
				}

				val reqSize = 500
				val scale = Scale.FILL

				val bytes = MediaItemFactory.getEmbeddedImage(context, item)
					?: return@withContext item to null

				if (bytes.isEmpty()) {
					item.putEmbedSize(0f)
				} else {
					item.putEmbedSize(bytes.size.toFloat() / 1000000)
				}

				ensureActive()

				// if possible find sources that provide media style notification width and height
				// 128 to 1024 is ideal


				val source: Any = bytes

				// maybe create Fitter Class for some APIs version or Device that require some modification
				// to have proper display
				val squaredBitmap = coilHelper.loadSquaredBitmap(source, reqSize, scale)

				if (squaredBitmap != null) {
					lruCache.put(item.mediaId + "500", squaredBitmap)
				}

				item to squaredBitmap
			} catch (oom: OutOfMemoryError) {
				null
			}
		}

		private fun getItemBitmap(player: Player): Bitmap? {
			return player.currentMediaItem?.mediaId?.let { id ->
				when (id) {
					localStored.first -> localStored.second
					else -> null
				}
			}
		}

		private fun createNotificationInternalImpl(
			session: MediaSession
		): MediaNotification {
			Timber.d("createNotificationInternalImpl")

			val notification =
				getNotificationFromMediaSession(
					session,
					isForegroundCondition(componentDelegate, session), ChannelName
				)

			return MediaNotification(notificationId, notification)
		}

		inner class NotificationActionReceiver : MediaNotificationProvider.ActionReceiver {

			override fun actionAny(context: Context, intent: Intent): Int {
				if (!isStarted) return -1

				componentDelegate.sessionInteractor.mediaSession
					?.let {
						val player = it.player

						if (player.mediaItemCount == 0) {
							notificationManagerService.cancel(notificationId)

							val itemInfo: MediaItemInfo? =
								if (itemInfoIntentConverter.isConvertible(intent)) {
									itemInfoIntentConverter.toMediaItemInfo(intent)
								} else {
									null
								}

							if (itemInfo != null && itemInfo.mediaItem.mediaUri != null) {
								// TODO: handle this
								Timber.w(
									"received Notification action when playback item is empty (convertible)"
								)
							} else {
								// TODO: handle this
								Timber.w(
									"received Notification action when playback item is empty"
								)
							}
							return -1
						}
					}
				return 0
			}

			override fun actionPlay(context: Context, intent: Intent) {
				componentDelegate.sessionInteractor.mediaSession
					?.let {
						val player = it.player

						when {
							player.playbackState.isStateIdle() -> player.prepare()

							player.playbackState.isStateEnded() -> {
								if (player.hasNextMediaItem()) player.seekToNextMediaItem()
								player.seekTo(0)
							}

							player.playbackState.isOngoing() && player.playWhenReady -> {
								val notification = provider
									.getNotificationFromMediaSession(it, true, ChannelName)
								return dispatcher.updateNotification(notificationId, notification)
							}
						}

						player.play()
					}
					?: throw IllegalStateException("Received Intent: $intent on Released State")
			}

			override fun actionPause(context: Context, intent: Intent) {
				componentDelegate.sessionInteractor.mediaSession
					?.let {
						val player = it.player

						when {
							player.playbackState.isStateIdle() -> player.prepare()
							player.playbackState.isOngoing() && !player.playWhenReady -> {
								val notification = provider
									.getNotificationFromMediaSession(it, true, ChannelName)
								return dispatcher.updateNotification(notificationId, notification)
							}
						}

						player.pause()
					}
					?: throw IllegalStateException("Received Intent: $intent on Released State")
			}

			override fun actionNext(context: Context, intent: Intent) {
				componentDelegate.sessionInteractor.mediaSession
					?.let {
						val player = it.player

						when {
							!player.hasNextMediaItem() -> {
								val notification = provider
									.getNotificationFromMediaSession(it, true, ChannelName)
								return dispatcher.updateNotification(notificationId, notification)
							}
						}

						player.seekToNextMediaItem()
					}
					?: throw IllegalStateException("Received Intent: $intent on Released State")
			}

			override fun actionPrevious(context: Context, intent: Intent) {
				componentDelegate.sessionInteractor.mediaSession
					?.let {
						val player = it.player

						when {
							!player.hasPreviousMediaItem() -> {
								val notification = provider
									.getNotificationFromMediaSession(it, true, ChannelName)
								return dispatcher.updateNotification(notificationId, notification)
							}
						}

						player.seekToPreviousMediaItem()
					}
					?: throw IllegalStateException("Received Intent: $intent on Released State")
			}

			override fun actionRepeatOffToOne(context: Context, intent: Intent) {
				componentDelegate.sessionInteractor.mediaSession
					?.let {
						val player = it.player

						when {
							!player.repeatMode.isRepeatOff() -> {
								val notification = provider
									.getNotificationFromMediaSession(it, true, ChannelName)
								return dispatcher.updateNotification(notificationId, notification)
							}
						}

						player.repeatMode = Player.REPEAT_MODE_ONE
					}
					?: throw IllegalStateException("Received Intent: $intent on Released State")
			}

			override fun actionRepeatOneToAll(context: Context, intent: Intent) {
				componentDelegate.sessionInteractor.mediaSession
					?.let {
						val player = it.player

						when {
							!player.repeatMode.isRepeatOne() -> {
								val notification = provider
									.getNotificationFromMediaSession(it, true, ChannelName)
								return dispatcher.updateNotification(notificationId, notification)
							}
						}

						player.repeatMode = Player.REPEAT_MODE_ALL
					}
					?: throw IllegalStateException("Received Intent: $intent on Released State")
			}

			override fun actionRepeatAllToOff(context: Context, intent: Intent) {
				componentDelegate.sessionInteractor.mediaSession
					?.let {
						val player = it.player

						when {
							!player.repeatMode.isRepeatAll() -> {
								val notification = provider
									.getNotificationFromMediaSession(it, true, ChannelName)
								return dispatcher.updateNotification(notificationId, notification)
							}
						}

						player.repeatMode = Player.REPEAT_MODE_OFF
					}
					?: throw IllegalStateException("Received Intent: $intent on Released State")
			}

			override fun actionNoNext(context: Context, intent: Intent) = Unit

			override fun actionNoPrevious(context: Context, intent: Intent) = Unit

			override fun actionStop(context: Context, intent: Intent) {
				if (!isStarted) return

				componentDelegate.sessionInteractor.mediaSession
					?.let {

						when {
							!isOnGoingCondition(it) -> {
								val notification = provider
									.getNotificationFromMediaSession(it, false, ChannelName)
								return dispatcher.updateNotification(notificationId, notification)
							}
						}

						dispatcher.cancelValidatorJob()

						it.player.pause()
						it.player.stop()
					}
					?: throw IllegalStateException("Received Intent: $intent on Released State")
			}

			override fun actionCancel(context: Context, intent: Intent) {
				if (!isStarted) return
				val stateInteractor = componentDelegate.stateInteractor

				componentDelegate.sessionInteractor.mediaSession
					?.let {

						when {
							isOnGoingCondition(it) -> {
								val notification = provider
									.getNotificationFromMediaSession(it, true, ChannelName)
								return dispatcher.updateNotification(notificationId, notification)
							}
						}

						dispatcher.cancelValidatorJob()

						stateInteractor.stopForegroundService(true)
						stateInteractor.stopService()

						if (!MainActivity.Companion.Info.state.isAlive()) stateInteractor.releaseService()
					}
					?: throw IllegalStateException("Received Intent: $intent on Released State")
			}

			override fun actionDismiss(context: Context, intent: Intent) =
				actionCancel(context, intent) // placeholder
		}

	}

	private inner class PlayerEventListener : Player.Listener {

		private var mediaItemTransitionJob by AutoCancelJob()
		override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
			if (!isStarted) return

			mediaItemTransitionJob = mainScope.launch {
				componentDelegate.sessionInteractor.mediaSession
					?.let {
						val getNotification = suspend {
							provider.ensureCurrentItemBitmap(it.player)
							provider.fromMediaSession(it, isOnGoingCondition(it), ChannelName)
						}
						provider.updateItemBitmap(it.player) {
							dispatcher.suspendUpdateNotification(notificationId, getNotification())
						}
						dispatcher.dispatchNotificationValidator(
							notificationId,
							onUpdate = getNotification
						)
					}
			}
		}

		private var playerRepeatModeJob by AutoCancelJob()
		override fun onRepeatModeChanged(repeatMode: Int) {
			if (!isStarted) return

			playerRepeatModeJob = mainScope.launch {

				componentDelegate.sessionInteractor.mediaSession
					?.let {
						val getNotification = suspend {
							provider.ensureCurrentItemBitmap(it.player)
							provider.fromMediaSession(it, isOnGoingCondition(it), ChannelName)
						}
						dispatcher.suspendUpdateNotification(notificationId, getNotification())
						dispatcher.dispatchNotificationValidator(
							notificationId,
							onUpdate = getNotification
						)
					}
			}
		}

		private var playWhenReadyChangedJob by AutoCancelJob()
		override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
			if (!isStarted) return

			playWhenReadyChangedJob = mainScope.launch {

				componentDelegate.sessionInteractor.mediaSession
					?.let {
						val getNotification = suspend {
							provider.ensureCurrentItemBitmap(it.player)
							provider.fromMediaSession(
								it,
								isOnGoingCondition(it),
								ChannelName
							)
						}
						dispatcher.dispatchNotificationValidator(
							notificationId,
							onUpdate = getNotification
						)
					}
			}
		}

		private var playbackStateChangedJob by AutoCancelJob()
		override fun onPlaybackStateChanged(playbackState: Int) {
			if (!isStarted) return

			playbackStateChangedJob = mainScope.launch {

				componentDelegate.sessionInteractor.mediaSession
					?.let {
						val getNotification = suspend {
							provider.ensureCurrentItemBitmap(it.player)
							provider.fromMediaSession(it, isOnGoingCondition(it), ChannelName)
						}
						dispatcher.dispatchNotificationValidator(
							notificationId,
							onUpdate = getNotification
						)
					}
			}
		}

	}

	inner class Interactor {

		fun getNotification(
			session: MediaSession,
			onGoing: Boolean
		): Notification = this@MediaNotificationManager.getNotification(session, onGoing)

		fun startForegroundService(
			mediaSession: MediaSession,
			onGoingNotification: Boolean
		) {
			this@MediaNotificationManager
				.startForegroundService(mediaSession, onGoingNotification)
		}

		fun stopForegroundService(removeNotification: Boolean) {
			this@MediaNotificationManager
				.stopForegroundService(removeNotification)
		}
	}

	companion object {
		const val ChannelName = "MusicLibrary Channel"
		const val NotificationName = "Playback"
	}
}