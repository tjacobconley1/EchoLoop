package com.conley.game.shared

import androidx.compose.runtime.Composable

/** Handles the system "back" gesture/button where the platform has one (Android). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

