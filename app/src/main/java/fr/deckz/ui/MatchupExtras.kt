package fr.deckz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.deckz.AppContainer
import fr.deckz.data.GameResult
import fr.deckz.data.MyDeck
import fr.deckz.strategy.Verdict
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Panel
import kotlin.math.roundToInt

val WinGreen = Color(0xFF3DDC84)
val LossRed = Color(0xFFFF5A5A)

/** Bilan victoires / défaites, ex. « 3 V – 1 D · 75 % ». */
data class Record(val wins: Int, val losses: Int) {
    val total get() = wins + losses
    val pct get() = if (total == 0) 0 else (wins * 100.0 / total).roundToInt()
    val label get() = "$wins V – $losses D" + if (total > 0) " · $pct %" else ""

    companion object {
        fun of(games: List<GameResult>) = Record(games.count { it.win }, games.count { !it.win })
    }
}

/** Choix de la liste utilisée pour MON camp : liste méta moyenne ou un de mes decks perso. */
@Composable
fun MyDeckChips(decks: List<MyDeck>, selectedId: String?, onSelect: (String?) -> Unit) {
    if (decks.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Ma liste", style = MaterialTheme.typography.labelMedium, color = Muted)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Gold, selectedLabelColor = Night)
            FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text("Liste méta (moyenne)") }, colors = colors)
            decks.forEach { d ->
                FilterChip(selected = selectedId == d.id, onClick = { onSelect(d.id) }, label = { Text("🗂 ${d.name}") }, colors = colors)
            }
        }
    }
}

/** Journal de parties d'un affrontement : boutons Victoire / Défaite, bilan et dernières parties. */
@Composable
fun GamesCard(c: AppContainer, me: String, opp: String, deckId: String?, verdict: Verdict, mirror: Boolean) {
    val all by c.user.games.collectAsStateWithLifecycle()
    val decks by c.user.decks.collectAsStateWithLifecycle()
    val games = all.filter { it.me == me && it.opp == opp }
    val r = Record.of(games)
    val accent = Color(0xFF4FC3F7)

    Column(Modifier.fillMaxWidth().padding(16.dp, 5.dp).clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text("📊", fontSize = 17.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mes parties", style = MaterialTheme.typography.titleMedium)
                Text(if (r.total == 0) "Aucune partie enregistrée" else r.label, style = MaterialTheme.typography.labelMedium,
                    color = if (r.total == 0) Muted else if (r.pct >= 50) WinGreen else LossRed, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Estimation de l'appli : ", style = MaterialTheme.typography.labelSmall, color = Muted)
            VerdictPill(verdict, mirror = mirror)
        }
        compare(r, verdict, mirror)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Gold, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Button(
                onClick = { c.user.addGame(me, opp, true, deckId) }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = WinGreen, contentColor = Night),
            ) { Text("Victoire", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { c.user.addGame(me, opp, false, deckId) }, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = LossRed, contentColor = Night),
            ) { Text("Défaite", fontWeight = FontWeight.Bold) }
        }
        if (games.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Dernières parties", style = MaterialTheme.typography.labelMedium, color = Muted)
            games.sortedByDescending { it.time }.take(5).forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (g.win) "✅" else "❌", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    val deckName = g.deckId?.let { id -> decks.firstOrNull { it.id == id }?.name }
                    Text(
                        "${fmtDateTime(g.time)} · ${if (g.win) "Victoire" else "Défaite"}" + (deckName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { c.user.removeGame(g) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Supprimer cette partie", tint = Muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** Petit commentaire quand le bilan réel s'écarte de l'estimation (à partir de 5 parties). */
private fun compare(r: Record, v: Verdict, mirror: Boolean): String? {
    if (r.total < 5 || mirror) return null
    return when {
        v == Verdict.DIFFICILE && r.pct >= 55 -> "Tu fais mieux que prévu dans cet affrontement : ta façon de le jouer marche, note-la !"
        v == Verdict.FAVORABLE && r.pct <= 45 -> "Moins bien que prévu : relis le plan et tes notes, quelque chose coince."
        else -> "Ton bilan colle à peu près à l'estimation."
    }
}
