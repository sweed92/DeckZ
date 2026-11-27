package fr.deckz.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.deckz.AppContainer
import fr.deckz.strategy.OpponentRow
import fr.deckz.strategy.Verdict
import fr.deckz.ui.theme.GameColors
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Orange
import fr.deckz.ui.theme.Panel

fun verdictColor(v: Verdict) = when (v) {
    Verdict.FAVORABLE -> Color(0xFF3DDC84)
    Verdict.EQUILIBRE -> Gold
    Verdict.DIFFICILE -> Color(0xFFFF5A5A)
}

@Composable
fun VerdictPill(v: Verdict, modifier: Modifier = Modifier, mirror: Boolean = false) {
    val col = if (mirror) Muted else verdictColor(v)
    Text(
        if (mirror) "Miroir" else v.label,
        modifier.clip(RoundedCornerShape(50)).background(col.copy(alpha = 0.16f))
            .border(1.dp, col.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
        color = col, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchupPickerScreen(c: AppContainer, meUid: Int, onBack: () -> Unit, onOpponent: (Int) -> Unit) {
    val me by c.repository.card(meUid).collectAsStateWithLifecycle(null)
    var all by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<OpponentRow>?>(null) }
    LaunchedEffect(meUid, all) { rows = c.strategy.opponents(meUid, all) }
    val games by c.user.games.collectAsStateWithLifecycle()
    // Mon bilan contre chaque adversaire (numéro du leader adverse -> victoires / défaites).
    val records = remember(games, me?.number) {
        games.filter { it.me == me?.number }.groupBy { it.opp }.mapValues { Record.of(it.value) }
    }

    val shown = remember(rows, query, color) {
        rows.orEmpty().filter { r ->
            (color == null || r.leader.color?.contains(color!!) == true) &&
                (query.isBlank() || r.leader.name.contains(query, true) || r.leader.number.contains(query, true))
        }
    }

    Scaffold(
        containerColor = Night,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Plans de bataille")
                        me?.let { Text("${it.name} (${it.number}) contre…", style = MaterialTheme.typography.labelMedium, color = Muted) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Rechercher un adversaire") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Effacer") } },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedContainerColor = Panel, focusedContainerColor = Panel),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !all, onClick = { all = !all }, label = { Text(if (all) "Tous les leaders" else "Méta") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Orange, selectedLabelColor = Night),
                )
                GameColors.all.forEach { (key, col) ->
                    val sel = color == key
                    FilterChip(
                        selected = sel, onClick = { color = if (sel) null else key },
                        label = { Text(GameColors.labels[key]!!) },
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(col)) },
                        border = BorderStroke(1.dp, if (sel) col else col.copy(alpha = 0.45f)),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = col.copy(alpha = 0.28f), selectedLabelColor = Color.White),
                    )
                }
            }
            Text(
                "Verdict indicatif, d'après les analyses DeckZ, la communauté et les cartes jouées.",
                color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp),
            )
            when {
                rows == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Orange) }
                shown.isEmpty() -> Empty("Aucun adversaire ne correspond")
                else -> LazyColumn(contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 32.dp)) {
                    itemsIndexed(shown, key = { _, r -> r.leader.uid }) { i, r ->
                        OpponentItem(r, records[r.leader.number], Modifier.appear(i)) { onOpponent(r.leader.uid) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpponentItem(r: OpponentRow, record: Record?, modifier: Modifier, onClick: () -> Unit) {
    val col = GameColors.of(r.leader.color)
    Row(
        modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Panel)
            .border(1.dp, col.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(r.leader.imageUrl, Modifier.width(54.dp).aspectRatio(63f / 88f), corner = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.leader.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                TierBadge(r.stats?.tier)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(6.dp))
                Text(r.leader.number, style = MaterialTheme.typography.labelSmall, color = Muted)
                r.stats?.let {
                    Text("  ·  ${"%.1f".format(it.share * 100)} % de la méta", style = MaterialTheme.typography.labelSmall, color = Gold)
                }
            }
            if (record != null && record.total > 0) Text(
                "Mon bilan : ${record.label}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                color = if (record.pct >= 50) WinGreen else LossRed,
            )
            if (r.hasProfile) Text("Guide détaillé disponible", style = MaterialTheme.typography.labelSmall, color = Orange)
        }
        VerdictPill(r.verdict, mirror = r.mirror)
    }
}
