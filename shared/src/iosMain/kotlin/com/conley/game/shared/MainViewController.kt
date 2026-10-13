package com.conley.game.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Entry point called from the iOS app (see iosApp/iosApp/ContentView.swift). */
fun MainViewController(): UIViewController = ComposeUIViewController {
    App(UserDefaultsStore(), IosSoundPlayer())
}

