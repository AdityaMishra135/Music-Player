package com.kylentt.musicplayer.ui.util.compose

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import timber.log.Timber

@Composable
fun Lifecycle.eventAsState(onEvent: Lifecycle.Event = Lifecycle.Event.ON_ANY): State<Lifecycle.Event> {
	val event = remember { mutableStateOf(onEvent, policy = neverEqualPolicy()) }

	DisposableEffect(key1 = this) {
		val observer = LifecycleEventObserver { _, newEvent ->
			if (onEvent == newEvent) {
				Timber.d("RecomposeOnEvent, recomposing $onEvent")
				event.value = newEvent
			}
		}
		addObserver(observer)
		onDispose { removeObserver(observer) }
	}
	return event
}

@Composable
fun Lifecycle.RepeatOnEvent(
	onEvent: Lifecycle.Event,
	block: @Composable (State<Lifecycle.Event>) -> Unit
) = block(eventAsState(onEvent))

@Composable
fun Lifecycle.RecomposeOnEvent(
	onEvent: Lifecycle.Event,
	block: @Composable (Lifecycle.Event) -> Unit
) = RepeatOnEvent(onEvent) { block(it.value) }
