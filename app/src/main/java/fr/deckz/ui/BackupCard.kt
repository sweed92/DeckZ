package fr.deckz.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.deckz.AppContainer
import fr.deckz.data.UserBackup
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Orange
import fr.deckz.ui.theme.Panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Réglages > Mes données : exporter / importer notes, parties, decks perso et favoris. */
@Composable
fun BackupCard(c: AppContainer) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val notes by c.user.notes.collectAsStateWithLifecycle()
    val games by c.user.games.collectAsStateWithLifecycle()
    val decks by c.user.decks.collectAsStateWithLifecycle()
    val favs by c.user.favorites.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<UserBackup?>(null) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = c.user.exportJson()
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }.isSuccess
            }
            toast(if (ok) "Sauvegarde enregistrée" else "Impossible d'écrire le fichier")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val b = withContext(Dispatchers.IO) {
                runCatching {
                    val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    c.user.parseBackup(text)
                }.getOrNull()
            }
            if (b == null) toast("Ce fichier n'est pas une sauvegarde DeckZ") else pending = b
        }
    }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(16.dp)) {
        Text("Mes données", style = MaterialTheme.typography.titleMedium)
        Text(
            "${notes.size} note(s) · ${games.size} partie(s) · ${decks.size} deck(s) · ${favs.size} favori(s)",
            color = Muted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Elles ne sont que sur ce téléphone. Exporte-les de temps en temps (Google Drive, e-mail…) pour les retrouver " +
                "si tu changes de téléphone ou réinstalles l'appli.",
            color = Muted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        Row {
            Button(
                onClick = {
                    val day = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
                    exporter.launch("deckz-sauvegarde-$day.json")
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Night),
            ) { Text("Exporter") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.weight(1f)) { Text("Importer") }
        }
    }

    pending?.let { b ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Importer cette sauvegarde ?") },
            text = {
                Text(
                    "Sauvegarde du ${SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(b.exportedAt))} : " +
                        "${b.notes.size} note(s), ${b.games.size} partie(s), ${b.decks.size} deck(s), ${b.favorites.size} favori(s).\n\n" +
                        "Elle remplacera toutes tes données actuelles."
                )
            },
            confirmButton = {
                TextButton(onClick = { c.user.restore(b); pending = null; toast("Sauvegarde importée") }) { Text("Remplacer", color = Orange) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } },
        )
    }
}
