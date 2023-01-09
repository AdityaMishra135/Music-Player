package com.flammky.musicplayer.main

import com.flammky.musicplayer.main.ui.MainPresenter
import com.flammky.musicplayer.main.ui.r.RealMainPresenter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object HiltModule {

	@Provides
	fun provideMainPresenter(
		r: RealMainPresenter
	): MainPresenter {
		return r
	}
}