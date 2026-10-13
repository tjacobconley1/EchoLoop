package com.conley.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.conley.game.shared.setGameContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setGameContent()
    }
}


