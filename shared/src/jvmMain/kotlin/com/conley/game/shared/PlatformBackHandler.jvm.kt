package com.conley.game.shared

import androidx.compose.runtime.Composable

/** The JVM target only runs tests and the level generator, so there is no back button to handle. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

