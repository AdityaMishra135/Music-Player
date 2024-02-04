package com.flammky.musicplayer.library.presentation.entry

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import com.flammky.musicplayer.base.compose.rememberLocalContextHelper
import dev.dexsr.klio.core.sdk.AndroidAPI
import dev.dexsr.klio.core.sdk.AndroidBuildVersion.hasLevel
import com.flammky.musicplayer.core.sdk.tiramisu
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Composable
internal fun PermGuard(
	onPermChanged: (Boolean?) -> Unit
) {
	val contextHelper = rememberLocalContextHelper()

	val allowState = remember {
		mutableStateOf<Boolean?>(
			if (AndroidAPI.hasLevel(AndroidAPI.tiramisu.BUILD_CODE_INT)) {
				contextHelper.permissions.hasPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
			} else {
				contextHelper.permissions.common.hasReadExternalStorage ||
					contextHelper.permissions.common.hasWriteExternalStorage
			}
		)
	}

	val lo = LocalLifecycleOwner.current
	DisposableEffect(key1 = lo, effect = {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				allowState.value = if (AndroidAPI.hasLevel(AndroidAPI.tiramisu.BUILD_CODE_INT)) {
					contextHelper.permissions.hasPermission(android.Manifest.permission.READ_MEDIA_AUDIO)
				} else {
					contextHelper.permissions.common.hasReadExternalStorage ||
						contextHelper.permissions.common.hasWriteExternalStorage
				}
			}
		}
		lo.lifecycle.addObserver(observer)
		onDispose { lo.lifecycle.removeObserver(observer) }
	})

	val allow = allowState.value
	LaunchedEffect(key1 = allow, block = {
		onPermChanged(allow)
	})
}


@HiltViewModel
private class PermGuardViewModel @Inject constructor() : ViewModel() {

}
