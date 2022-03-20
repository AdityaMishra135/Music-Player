package com.kylentt.mediaplayer.domain.mediaSession.service

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
import com.kylentt.mediaplayer.core.exoplayer.ControllerCommand
import com.kylentt.mediaplayer.core.exoplayer.ExoController
import com.kylentt.mediaplayer.core.exoplayer.util.ExoUtil
import com.kylentt.mediaplayer.core.util.handler.CoilHandler
import com.kylentt.mediaplayer.core.util.handler.MediaItemHandler
import com.kylentt.mediaplayer.disposed.domain.presenter.ServiceConnectorImpl
import com.kylentt.mediaplayer.disposed.domain.presenter.util.State
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.ACTION_CANCEL
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.ACTION_NEXT
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.ACTION_PAUSE
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.ACTION_PLAY
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.ACTION_PREV
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.ACTION_REPEAT_ALL_TO_OFF
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.ACTION_REPEAT_OFF_TO_ONE
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.ACTION_REPEAT_ONE_TO_ALL
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.MEDIA_SESSION_ID
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.NEW_SESSION_PLAYER
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.NEW_SESSION_PLAYER_RECOVER
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.NOTIFICATION_ID
import com.kylentt.mediaplayer.domain.mediaSession.service.MusicServiceConstants.PLAYBACK_INTENT
import com.kylentt.mediaplayer.ui.mainactivity.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import timber.log.Timber
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class  MusicService : MediaLibraryService() {

    companion object {
        val TAG: String = MusicService::class.simpleName ?: "Music Service"
        var isActive: Boolean? = null
            get() = field ?: false
            private set
    }

    @Inject
    lateinit var exo: ExoPlayer
    @Inject
    lateinit var coilHandler: CoilHandler
    @Inject
    lateinit var mediaItemHandler: MediaItemHandler
    @Inject
    lateinit var serviceConnectorImpl: ServiceConnectorImpl

    private lateinit var exoController: ExoController

    private var libSession: MediaLibrarySession? = null

    private val playbackReceiver = PlaybackReceiver()

    var isForegroundService = false

    val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onUpdateNotification(session: MediaSession): MediaNotification? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("$TAG onCreate")
        initializeSession()
        registerReceiver()

    }

    private fun initializeSession() {
        libSession = makeLibrarySession(makeActivityIntent()!!)
        isActive = true
    }

    private fun makeActivityIntent() = packageManager?.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(this, 444, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
            ServiceConnectorImpl.setServiceState(serviceConnectorImpl, State.ServiceState.Connected)
            super.onPostConnect(session, controller)
        }

        override fun onDisconnected(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ) {
            Timber.d("MusicService onDisconnected")
            ServiceConnectorImpl.setServiceState(serviceConnectorImpl, State.ServiceState.Disconnected)
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
        ) = mediaItemHandler.rebuildMediaItem(mediaItem)
    }

    inner class PlaybackReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if (!::exoController.isInitialized) {
                libSession?.let { releaseSession(it) }
                exitProcess(1)
            }

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
                    exoController.commandController(
                        ControllerCommand.CommandWithFade(ControllerCommand.StopCancel {
                            libSession?.let {
                                considerReleasing(it)
                            }
                        }, flush = true)
                    )
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
        if (!MainActivity.isActive) {
            releaseSession(session)
            Timber.d("MusicService considerRelease released, reason = MainActivity.isActive == ${MainActivity.isActive}")
        } else {
            Timber.d("MusicService considerRelease not released, reason = MainActivity.isActive == ${MainActivity.isActive}, resetting Player")
            sendBroadcast(
                Intent(PLAYBACK_INTENT).apply {
                    putExtra("SESSION", NEW_SESSION_PLAYER_RECOVER)
                    setPackage(packageName)
                }
            )
        }
    }

    private fun releaseSession(session: MediaLibrarySession) {
        Timber.d("$TAG releaseSession")
        serviceScope.cancel()
        ExoController.releaseInstance(exoController)
        unregisterReceiver(playbackReceiver)
        session.player.release()
        stopSelf()
        stopForeground(true).also { isForegroundService = false }
        serviceConnectorImpl.releaseSession()
        session.release()
        libSession = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("$TAG onDestroy")
        libSession?.let { releaseSession(it) }
        isActive = false
    }
}