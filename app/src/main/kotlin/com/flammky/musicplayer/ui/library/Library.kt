package com.flammky.musicplayer.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Library() {
	ColumnScrollRoot()
}

@Composable
private fun ColumnScrollRoot() {

	val rootScrollState = rememberScrollState()

	Column(
		modifier = Modifier
			.verticalScroll(rootScrollState)
	) {

	}
}
