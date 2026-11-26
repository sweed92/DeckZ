package fr.deckz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import fr.deckz.AppContainer
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Orange
import fr.deckz.ui.theme.Panel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(c: AppContainer, onBack: () -> Unit) {
    val s by c.repository.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Scaffold(
        containerColor = Night,
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(16.dp)) {
                Text("Dernière mise à jour", color = Muted, style = MaterialTheme.typography.labelMedium)
                Text(if (s.lastSuccess > 0) fmtDateTime(s.lastSuccess) else "Jamais", style = MaterialTheme.typography.titleMedium)
                if (s.lastFailed) Text("La dernière tentative a partiellement échoué : ${s.error ?: "source indisponible"}",
                    color = Gold, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Mise à jour automatique chaque semaine (Wi-Fi + en charge).", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            if (s.running) {
                LinearProgressIndicator(progress = { s.progress }, color = Orange, modifier = Modifier.fillMaxWidth())
                Text(s.step, color = Gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = { c.scope.launch { c.repository.refresh() } }, enabled = !s.running,
                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Night),
            ) {
                Icon(Icons.Default.Refresh, null); Text("  Rafraîchir maintenant")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { confirm = true }, enabled = !s.running, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, null); Text("  Vider le cache")
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Sources : DeckPlanet (méta, decks, base de cartes), db-fusionworld.com (profils de decks). Application personnelle non officielle, " +
                    "sans lien avec Bandai. Requêtes limitées à 1 par seconde.",
                color = Muted, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Vider le cache ?") },
        text = { Text("Toutes les données et images seront supprimées puis re-téléchargées.") },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                SingletonImageLoader.get(ctx).let { it.memoryCache?.clear(); it.diskCache?.clear() }
                c.scope.launch { c.repository.clearCache() }
            }) { Text("Vider", color = Orange) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
    )
}

