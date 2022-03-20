package com.kylentt.mediaplayer.ui.mainactivity.compose.screen.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kylentt.mediaplayer.ui.mainactivity.compose.components.util.StatusBarSpacer
import com.kylentt.mediaplayer.ui.mainactivity.compose.theme.md3.DefaultColor
import timber.log.Timber

@Composable
fun SearchScreen(
    vm: SearchViewModel = hiltViewModel()
) {
    Timber.d("ComposeDebug SearchScreen")
    val songList by vm.songList.collectAsState()
    val textColor = DefaultColor.getDNTextColor()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StatusBarSpacer()
        Text(text = "Search Screen", color = textColor)
        Text(text = "Local Song Found: ${songList.size}", color = textColor)
        Spacer(modifier = Modifier.height(1000.dp))
        Text(text = "End Of Column", color = textColor)
    }
}