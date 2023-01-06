package com.flammky.musicplayer.core.app.startup

import android.app.Application
import android.content.Context
import androidx.startup.Initializer
import com.flammky.musicplayer.base.activity.ActivityWatcher
import com.flammky.musicplayer.app.ApplicationDelegate

class AppDelegateInitializer : Initializer<Unit> {

	override fun create(context: Context): Unit {
		ApplicationDelegate provides context as Application
		ActivityWatcher provides context as Application
	}

	override fun dependencies(): MutableList<Class<out Initializer<*>>> = ArrayList(0)
}
