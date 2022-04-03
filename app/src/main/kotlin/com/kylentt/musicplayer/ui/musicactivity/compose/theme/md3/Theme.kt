package com.kylentt.musicplayer.ui.musicactivity.compose.theme.md3

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.android.material.color.DynamicColors
import com.kylentt.mediaplayer.ui.mainactivity.disposed.compose.theme.ThemeHelper.getDynamicM3Color
import timber.log.Timber

object Md3Defaults {

	fun defaultLightThemeColors() = this.LightThemeColors
	private val LightThemeColors = lightColorScheme(

		primary = md_theme_light_primary,
		onPrimary = md_theme_light_onPrimary,
		primaryContainer = md_theme_light_primaryContainer,
		onPrimaryContainer = md_theme_light_onPrimaryContainer,
		secondary = md_theme_light_secondary,
		onSecondary = md_theme_light_onSecondary,
		secondaryContainer = md_theme_light_secondaryContainer,
		onSecondaryContainer = md_theme_light_onSecondaryContainer,
		tertiary = md_theme_light_tertiary,
		onTertiary = md_theme_light_onTertiary,
		tertiaryContainer = md_theme_light_tertiaryContainer,
		onTertiaryContainer = md_theme_light_onTertiaryContainer,
		error = md_theme_light_error,
		errorContainer = md_theme_light_errorContainer,
		onError = md_theme_light_onError,
		onErrorContainer = md_theme_light_onErrorContainer,
		background = md_theme_light_background,
		onBackground = md_theme_light_onBackground,
		surface = md_theme_light_surface,
		onSurface = md_theme_light_onSurface,
		surfaceVariant = md_theme_light_surfaceVariant,
		onSurfaceVariant = md_theme_light_onSurfaceVariant,
		outline = md_theme_light_outline,
		inverseOnSurface = md_theme_light_inverseOnSurface,
		inverseSurface = md_theme_light_inverseSurface,
		inversePrimary = md_theme_light_inversePrimary,
	)

	fun defaultDarkThemeColors() = this.DarkThemeColors
	private val DarkThemeColors = darkColorScheme(

		primary = md_theme_dark_primary,
		onPrimary = md_theme_dark_onPrimary,
		primaryContainer = md_theme_dark_primaryContainer,
		onPrimaryContainer = md_theme_dark_onPrimaryContainer,
		secondary = md_theme_dark_secondary,
		onSecondary = md_theme_dark_onSecondary,
		secondaryContainer = md_theme_dark_secondaryContainer,
		onSecondaryContainer = md_theme_dark_onSecondaryContainer,
		tertiary = md_theme_dark_tertiary,
		onTertiary = md_theme_dark_onTertiary,
		tertiaryContainer = md_theme_dark_tertiaryContainer,
		onTertiaryContainer = md_theme_dark_onTertiaryContainer,
		error = md_theme_dark_error,
		errorContainer = md_theme_dark_errorContainer,
		onError = md_theme_dark_onError,
		onErrorContainer = md_theme_dark_onErrorContainer,
		background = md_theme_dark_background,
		onBackground = md_theme_dark_onBackground,
		surface = md_theme_dark_surface,
		onSurface = md_theme_dark_onSurface,
		surfaceVariant = md_theme_dark_surfaceVariant,
		onSurfaceVariant = md_theme_dark_onSurfaceVariant,
		outline = md_theme_dark_outline,
		inverseOnSurface = md_theme_dark_inverseOnSurface,
		inverseSurface = md_theme_dark_inverseSurface,
		inversePrimary = md_theme_dark_inversePrimary,
	)
}




@Composable
fun MaterialTheme3(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
	val color = LocalContext.current.getDynamicM3Color(darkTheme = darkTheme)
	val systemUiController = rememberSystemUiController()
	MaterialTheme(
		colorScheme = color,
		typography = AppTypography,
	) {
		with(systemUiController) {
			setStatusBarColor(ColorHelper.getStatusBarColor())
			setNavigationBarColor(ColorHelper.getBottomNavigatorColor())
			isNavigationBarContrastEnforced = false
			navigationBarDarkContentEnabled = !darkTheme
			statusBarDarkContentEnabled = !darkTheme
		}
		content()
	}
}
