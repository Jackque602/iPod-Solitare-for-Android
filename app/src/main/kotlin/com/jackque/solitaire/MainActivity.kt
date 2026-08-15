package com.jackque.solitaire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.jackque.solitaire.data.DataStoreSolitaireStore
import com.jackque.solitaire.ui.SolitaireApp
import com.jackque.solitaire.ui.theme.SolitaireTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels {
        GameViewModel.Factory(DataStoreSolitaireStore(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw into the status-bar strip and the cutout area so the game's
        // own bar can sit on the true top row of the screen.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SolitaireTheme {
                SolitaireApp(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onWindowActiveChanged(true)
    }

    override fun onStop() {
        // Persist immediately so a swipe-kill right after backgrounding
        // still finds the latest game on disk.
        viewModel.onWindowActiveChanged(false)
        super.onStop()
    }
}
