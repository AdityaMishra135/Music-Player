package com.flammky.musicplayer.ui.library


import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flammky.android.medialib.temp.image.ArtworkProvider
import com.flammky.musicplayer.ui.library.local.LocalSongModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

class LibraryViewModel constructor(
	private val session: SessionInteractor,
	private val mediaProvider: MediaProvider
) : ViewModel() {

	private val _localSongs: SnapshotStateList<LocalSongModel> = mutableStateListOf()
	val localSongsState: SnapshotStateList<LocalSongModel> = _localSongs

	fun playSong(model: LocalSongModel) = viewModelScope.launch { session.play(model) }

	interface SessionInteractor {
		suspend fun play(model: LocalSongModel)
	}

	interface MediaProvider {
		val artwork: ArtworkProvider
		suspend fun getLocalSongs(): Deferred<LocalSongModel>
	}
}
