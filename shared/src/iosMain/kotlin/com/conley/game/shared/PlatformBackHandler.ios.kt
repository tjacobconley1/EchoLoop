package com.conley.game.shared

import androidx.compose.runtime.Composable

/** iOS has no system back button; swipe-back isn't used by this app. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}

