package com.flammky.android.medialib.async

import com.flammky.android.medialib.player.PlayerController
import kotlinx.coroutines.Deferred

interface AsyncPlayerController : PlayerController {
	fun connectAsync(): Deferred<Boolean>
}