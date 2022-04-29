package com.kylentt.disposed.disposed.domain.mediaSession.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.google.common.util.concurrent.ListenableFuture
import com.kylentt.disposed.disposed.core.exoplayer.ControllerCommand
import com.kylentt.disposed.disposed.core.exoplayer.ExoController
import com.kylentt.disposed.disposed.core.exoplayer.NotifProvider
import com.kylentt.disposed.disposed.core.exoplayer.util.ExoUtil
import com.kylentt.disposed.disposed.core.util.handler.CoilHandler
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.ACTION_CANCEL
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.ACTION_NEXT
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.ACTION_PAUSE
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.ACTION_PLAY
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.ACTION_PREV
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.ACTION_REPEAT_ALL_TO_OFF
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.ACTION_REPEAT_OFF_TO_ONE
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.ACTION_REPEAT_ONE_TO_ALL
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.MEDIA_SESSION_ID
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.NEW_SESSION_PLAYER
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.NEW_SESSION_PLAYER_RECOVER
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.NOTIFICATION_ID
import com.kylentt.disposed.disposed.domain.mediaSession.service.MusicServiceConstants.PLAYBACK_INTENT
import com.kylentt.mediaplayer.domain.mediasession.MediaSessionManager
import com.kylentt.mediaplayer.domain.mediasession.service.connector.MediaServiceState
import com.kylentt.mediaplayer.ui.activity.mainactivity.MainActivity
import com.kylentt.disposed.musicplayer.core.helper.MediaItemHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
internal class MusicService : MediaLibraryService() {

  @Inject
  lateinit var exo: ExoPlayer

  @Inject
  lateinit var coilHandler: CoilHandler

  @Inject
  lateinit var mediaItemHandler: MediaItemHelper

  @Inject
  lateinit var mediaSessionManager: MediaSessionManager

  lateinit var exoController: ExoController

  private lateinit var libSession: MediaLibrarySession

  private val playbackReceiver = PlaybackReceiver()

  var isForegroundService = false

  val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

  /*override fun onUpdateNotification(session: MediaSession): MediaNotification? {
      return null
  }*/

  override fun onCreate() {
    super.onCreate()
    validateCreation()

    Timber.d("$TAG onCreate")
    initializeSession()
    registerReceiver()
    setMediaNotificationProvider(NotifProvider { controller: MediaController, callback ->
      MediaNotification(NOTIFICATION_ID, exoController.getNotification(controller, callback))
    })
  }

  private fun validateCreation() {
    if (mediaSessionManager.serviceState.value is MediaServiceState.NOTHING) {
      if (!MainActivity.wasLaunched) exitProcess(0)
      if (!MainActivity.isAlive) {
        stopForeground(true)
        stopSelf()
        releaseSession()
      }
    }
  }

  private fun initializeSession() {
    libSession = makeLibrarySession(makeActivityIntent()!!)
    isActive = true

  }

  private fun makeActivityIntent() = packageManager?.getLaunchIntentForPackage(packageName)?.let {
    PendingIntent.getActivity(
      this,
      444,
      it,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun registerReceiver() =
    registerReceiver(playbackReceiver, IntentFilter(PLAYBACK_INTENT))

  private fun makeLibrarySession(
    intent: PendingIntent
  ) = MediaLibrarySession.Builder(this, exo, SessionCallback())
    .setId(MEDIA_SESSION_ID)
    .setSessionActivity(intent)
    .setMediaItemFiller(RepoItemFiller())
    .build()

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
    Timber.d("$TAG onGetSession")
    return libSession!!
  }

  inner class SessionCallback : MediaLibrarySession.MediaLibrarySessionCallback {
    override fun onConnect(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
      Timber.d("MusicService onConnect")
      // any exception here just Log.w()'d
      exoController = ExoController.getInstance(this@MusicService, session)
      return super.onConnect(session, controller)
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
      Timber.d("MusicService onPostConnect")
      super.onPostConnect(session, controller)
    }

    override fun onDisconnected(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
    ) {
      Timber.d("MusicService onDisconnected")
      super.onDisconnected(session, controller)
    }

    override fun onCustomCommand(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      customCommand: SessionCommand,
      args: Bundle,
    ): ListenableFuture<SessionResult> {
      Timber.d("ExoController ${customCommand.customAction} onCustomCommand")
      return super.onCustomCommand(session, controller, customCommand, args)
    }
  }

  inner class RepoItemFiller : MediaSession.MediaItemFiller {
    override fun fillInLocalConfiguration(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      mediaItem: MediaItem,
    ): MediaItem = with(mediaItemHandler) { mediaItem.rebuild() }
  }

  inner class PlaybackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {

      Timber.d("onReceive BroadcastReceiver")

      when (intent?.getStringExtra("ACTION")) {
        ACTION_NEXT -> exoController.commandController(
          ControllerCommand.CommandWithFade(ControllerCommand.SkipToNext, false)
        )
        ACTION_PREV -> exoController.commandController(
          ControllerCommand.CommandWithFade(ControllerCommand.SkipToPrevMedia, false)
        )
        ACTION_PLAY -> exoController.commandController(
          ControllerCommand.SetPlayWhenReady(true)
        )
        ACTION_PAUSE -> exoController.commandController(
          ControllerCommand.SetPlayWhenReady(false)
        )
        ACTION_REPEAT_OFF_TO_ONE -> exoController.commandController(
          ControllerCommand.SetRepeatMode(Player.REPEAT_MODE_ONE)
        )
        ACTION_REPEAT_ONE_TO_ALL -> exoController.commandController(
          ControllerCommand.SetRepeatMode(Player.REPEAT_MODE_ALL)
        )
        ACTION_REPEAT_ALL_TO_OFF -> exoController.commandController(
          ControllerCommand.SetRepeatMode(Player.REPEAT_MODE_OFF)
        )
        ACTION_CANCEL -> {
          handleActionCancelDefault()
        }
      }
      when (intent?.getStringExtra("SESSION")) {
        NEW_SESSION_PLAYER -> {
          libSession?.let {
            newSessionPlayer(it, false)
          }
        }
        NEW_SESSION_PLAYER_RECOVER -> {
          libSession?.let {
            newSessionPlayer(it, true)
          }
        }
      }
    }
  }

  fun handleActionCancelDefault() {
    exoController.commandController(
      ControllerCommand.CommandWithFade(ControllerCommand.StopCancel {
        libSession?.let {
          considerReleasing(it)
        }
      }, flush = true)
    )
  }

  private fun newSessionPlayer(session: MediaSession, recover: Boolean = true) {
    val context = this
    with(ExoUtil) {
      synchronized(playbackReceiver) {
        val p = session.player as ExoPlayer
        val new = newExo(context)
        if (recover) {
          new.copyFrom(p, seekToi = true, seekToPos = true)
        }
        p.release()
        session.player = new
        ExoController.updateSession(session)
      }
    }
  }

  // there's no need to release the Player, just playing around
  private fun considerReleasing(session: MediaLibrarySession) {
    Timber.d("MusicService considerRelease called")
    if (!MainActivity.isAlive) {
      releaseSession()
      Timber.d("MusicService considerRelease released, reason = MainActivity.isActive == ${MainActivity.isAlive}")
    } else {
      Timber.d("MusicService considerRelease not released, reason = MainActivity.isActive == ${MainActivity.isAlive}, resetting Player")
      sendBroadcast(
        Intent(PLAYBACK_INTENT).apply {
          putExtra("SESSION", NEW_SESSION_PLAYER_RECOVER)
          setPackage(packageName)
        }
      )
    }
  }

  private fun releaseSession() {
    Timber.d("$TAG releaseSession")
    if (::exoController.isInitialized) ExoController.releaseInstance(exoController)
    unregisterReceiver(playbackReceiver)
    stopForeground(true).also { isForegroundService = false }
    stopSelf()

  }

  override fun onDestroy() {
    Timber.d("$TAG onDestroy")
    serviceScope.cancel()
    this.sessions.forEach { it.release() }
    if (::libSession.isInitialized) {
      libSession.player.release()
      libSession.release()
    }
    isActive = false
    super.onDestroy()

    // for whatever reason MediaLibrarySessionImpl is leaking this service context?
    if (!MainActivity.isAlive) {
      Timber.d("MusicService ExitProcess, current Activity State: ${MainActivity.stateStr}")
      exitProcess(0)
    }
  }

  companion object {
    val TAG: String = MusicService::class.simpleName ?: "Music Service"
    var isActive = false
      private set
  }
}