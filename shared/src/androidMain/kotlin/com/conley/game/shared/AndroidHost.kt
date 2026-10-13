package com.conley.game.shared

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Hosts the shared Compose UI; compiled in this module where the Compose compiler plugin is applied. */
fun ComponentActivity.setGameContent() {
    setContent { App(PrefsStore(this@setGameContent), AndroidSoundPlayer()) }
}



