package com.kylentt.musicplayer.ui.activity.musicactivity.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kylentt.mediaplayer.ui.mainactivity.disposed.compose.FakeRoot
import com.kylentt.musicplayer.domain.MediaViewModel
import com.kylentt.musicplayer.ui.activity.musicactivity.compose.environtment.PermissionDefaults
import com.kylentt.musicplayer.ui.activity.musicactivity.compose.environtment.RequireStoragePermission
import com.kylentt.musicplayer.ui.activity.musicactivity.compose.musicroot.MusicRoot

@Composable
internal inline fun MusicComposeDefault(
  whenGranted: () -> Unit
) {
  RequireStoragePermission(
    whenShowRationale = PermissionDefaults.NotDeniedOptions.RequestPermission(
      backgroundComposable = { MusicCompose(hasPermission = false) }
    )
  ) {
    whenGranted()
    MusicCompose(hasPermission = true)
  }
}

@Composable
internal fun MusicCompose(
  vm: MediaViewModel = viewModel(),
  hasPermission: Boolean
) {
  if (hasPermission) {
    MusicRoot(true)
  } else {
    FakeRoot()
  }
}
