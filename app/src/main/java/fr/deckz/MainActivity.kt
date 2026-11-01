package fr.deckz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.deckz.ui.DeckZRoot
import fr.deckz.ui.theme.DeckZTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DeckZApp).container
        setContent { DeckZTheme { DeckZRoot(container) } }
    }
}
