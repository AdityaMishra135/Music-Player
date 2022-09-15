package com.flammky.musicplayer.domain.musiclib.core.public

import android.content.Context
import com.flammky.musicplayer.domain.musiclib.core.internal.ComponentModule
import com.flammky.musicplayer.domain.musiclib.interactor.LibraryAgent

class MusicLibraryAPI @JvmOverloads internal constructor(
	private val context: Context = ComponentModule.requireContext()
) {
	private val agent: LibraryAgent = LibraryAgent(context)

	// Singleton Interactor, hide whatever mess happen behind
	@Deprecated("TODO")
	val localAgent: LibraryAgent.Mask
		get() = agent.mask
}