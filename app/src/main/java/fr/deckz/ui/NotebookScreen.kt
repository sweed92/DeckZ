package fr.deckz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.deckz.AppContainer
import fr.deckz.data.UserData
import fr.deckz.data.db.Card
import fr.deckz.ui.theme.Bangers
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Orange
import fr.deckz.ui.theme.Panel

private data class NotebookEntry(val me: String, val opp: String, val note: String, val record: Record, val lastGame: Long?)

/** Mon carnet : tous les affrontements où j'ai écrit une note ou enregistré des parties. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(c: AppContainer, onBack: () -> Unit, onMatchup: (Int, Int) -> Unit) {
    val leaders by c.repository.leaders().collectAsStateWithLifecycle(initialValue = emptyList())
    val notes by c.user.notes.collectAsStateWithLifecycle()
    val games by c.user.games.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    val byNumber = remember(leaders) {
        leaders.map { it.card }.groupBy { it.number }.mapValues { (_, l) -> l.firstOrNull { it.variantOf == null } ?: l.first() }
    }
    val entries = remember(notes, games) {
        val keys = notes.keys + games.map { UserData.key(it.me, it.opp) }
        val gamesByKey = games.groupBy { UserData.key(it.me, it.opp) }
        keys.mapNotNull { k ->
            val (me, opp) = UserData.splitKey(k) ?: return@mapNotNull null
            val g = gamesByKey[k].orEmpty()
            NotebookEntry(me, opp, notes[k].orEmpty(), Record.of(g), g.maxOfOrNull { it.time })
        }.sortedWith(compareByDescending<NotebookEntry> { it.lastGame ?: 0L }.thenBy { it.me }.thenBy { it.opp })
    }
    val shown = entries.filter { e ->
        query.isBlank() || listOf(e.me, e.opp, e.note, byNumber[e.me]?.name.orEmpty(), byNumber[e.opp]?.name.orEmpty())
            .any { it.contains(query.trim(), ignoreCase = true) }
    }
    val total = Record.of(games)
    // Affrontements à travailler / points forts : à partir de MIN_GAMES parties jouées.
    val ranked = entries.filter { it.record.total >= MIN_GAMES }
    val worst = ranked.filter { it.record.pct < 50 }
        .sortedWith(compareBy<NotebookEntry> { it.record.pct }.thenByDescending { it.record.total }).take(3)
    val best = ranked.filter { it.record.pct >= 50 && it !in worst }
        .sortedWith(compareByDescending<NotebookEntry> { it.record.pct }.thenByDescending { it.record.total }).take(3)

    Scaffold(
        containerColor = Night,
        topBar = {
            TopAppBar(
                title = { Text("Mon carnet") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp)) {
            if (total.total > 0) item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
                    Text("Bilan global", style = MaterialTheme.typography.labelMedium, color = Muted)
                    Text(total.label, style = MaterialTheme.typography.titleLarge, color = if (total.pct >= 50) WinGreen else LossRed)
                    Text("${total.total} partie(s) enregistrée(s)", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Spacer(Modifier.padding(4.dp))
            }
            if (query.isBlank() && entries.any { it.record.total > 0 }) item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
                    if (ranked.isEmpty()) {
                        Text("Affrontements à travailler", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Joue au moins $MIN_GAMES parties contre un même leader : tes affrontements les plus durs et tes points forts apparaîtront ici.",
                            style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        RankBlock("À travailler", LossRed, worst, byNumber, onMatchup,
                            empty = "Aucun affrontement perdant pour l'instant, bravo !")
                        Spacer(Modifier.padding(5.dp))
                        RankBlock("Tes points forts", WinGreen, best, byNumber, onMatchup,
                            empty = "Pas encore d'affrontement gagnant avec $MIN_GAMES parties ou plus.")
                    }
                }
                Spacer(Modifier.padding(4.dp))
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Rechercher un leader ou un mot dans tes notes") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Effacer") } },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedContainerColor = Panel, focusedContainerColor = Panel),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            if (shown.isEmpty()) item {
                Text(
                    if (entries.isEmpty()) "Ton carnet est vide.\n\nOuvre un plan de bataille pour écrire une note dans « Stratégie de joueurs » " +
                        "ou enregistrer une partie dans « Mes parties » : elles apparaîtront ici."
                    else "Rien ne correspond à ta recherche.",
                    color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            items(shown, key = { UserData.key(it.me, it.opp) }) { e ->
                val me = byNumber[e.me]
                val opp = byNumber[e.opp]
                EntryRow(e, me, opp, Modifier.padding(vertical = 5.dp)) {
                    if (me != null && opp != null) onMatchup(me.uid, opp.uid)
                }
            }
        }
    }
}

private const val MIN_GAMES = 3

@Composable
private fun RankBlock(
    title: String, color: androidx.compose.ui.graphics.Color, list: List<NotebookEntry>, byNumber: Map<String, Card>,
    onMatchup: (Int, Int) -> Unit, empty: String,
) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold)
    if (list.isEmpty()) Text(empty, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 2.dp))
    list.forEach { e ->
        val me = byNumber[e.me]
        val opp = byNumber[e.opp]
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .clickable(enabled = me != null && opp != null) { if (me != null && opp != null) onMatchup(me.uid, opp.uid) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardImage(opp?.art(), Modifier.width(30.dp).aspectRatio(63f / 88f), corner = 4.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("contre ${opp?.name ?: e.opp}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("avec ${me?.name ?: e.me}", style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(e.record.label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EntryRow(e: NotebookEntry, me: Card?, opp: Card?, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)
            .clickable(enabled = me != null && opp != null, onClick = onClick).padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardImage(me?.art(), Modifier.width(42.dp).aspectRatio(63f / 88f), corner = 6.dp)
            Text("VS", fontFamily = Bangers, color = Gold, modifier = Modifier.padding(horizontal = 6.dp))
            CardImage(opp?.art(), Modifier.width(42.dp).aspectRatio(63f / 88f), corner = 6.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(me?.name ?: e.me, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("contre ${opp?.name ?: e.opp}", style = MaterialTheme.typography.bodySmall, color = Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.record.total > 0) Text(e.record.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = if (e.record.pct >= 50) WinGreen else LossRed)
            }
        }
        if (e.note.isNotBlank()) {
            Box(Modifier.padding(top = 8.dp)) {
                Text("🧠 " + e.note, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
