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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.deckz.AppContainer
import fr.deckz.data.db.LeaderRow
import fr.deckz.ui.theme.GameColors
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Orange
import fr.deckz.ui.theme.Panel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderListScreen(c: AppContainer, onLeader: (Int) -> Unit, onSettings: () -> Unit, onNotebook: () -> Unit, onMyDecks: () -> Unit) {
    val leaders by c.repository.leaders().collectAsStateWithLifecycle(initialValue = emptyList())
    val sync by c.repository.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf<String?>(null) }
    var metaOnly by rememberSaveable { mutableStateOf(true) }
    val favs by c.user.favorites.collectAsStateWithLifecycle()

    // Favoris épinglés en haut (toujours affichés, même hors méta), puis l'ordre habituel.
    val shown = remember(leaders, query, color, metaOnly, favs) {
        leaders.filter { r ->
            (!metaOnly || r.stats != null || r.card.number in favs) &&
                (color == null || r.card.color?.contains(color!!) == true) &&
                (query.isBlank() || r.card.name.contains(query, true) || r.card.number.contains(query, true) ||
                    r.card.backName?.contains(query, true) == true)
        }.sortedByDescending { it.card.number in favs }
    }

    Scaffold(
        containerColor = Night,
        topBar = {
            TopAppBar(
                title = { Text("DeckZ", color = Orange, style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    if (sync.running) CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), color = Gold, strokeWidth = 2.dp)
                    else IconButton(onClick = { c.scope.launch { c.repository.refresh() } }) { Icon(Icons.Default.Refresh, "Rafraîchir") }
                    IconButton(onClick = onNotebook) { Text("📓", fontSize = 20.sp) }
                    IconButton(onClick = onMyDecks) { Text("🗂", fontSize = 20.sp) }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Réglages") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            StaleBanner(sync.lastSuccess, sync.lastFailed && !sync.running)
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Rechercher un leader") },
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
                    selected = metaOnly, onClick = { metaOnly = !metaOnly }, label = { Text("Méta") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Orange, selectedLabelColor = Night),
                )
                GameColors.all.forEach { (key, col) ->
                    val sel = color == key
                    FilterChip(
                        selected = sel, onClick = { color = if (sel) null else key },
                        label = { Text(GameColors.labels[key]!!) },
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(col)) },
                        border = BorderStroke(1.dp, if (sel) col else col.copy(alpha = 0.45f)),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = col.copy(alpha = 0.28f), selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (leaders.isEmpty()) "Aucune donnée pour l'instant" else "Aucun leader ne correspond", color = Muted)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(shown, key = { _, r -> r.card.uid }) { i, r ->
                        LeaderTile(r, r.card.number in favs, Modifier.appear(i).animateItem()) { onLeader(r.card.uid) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderTile(r: LeaderRow, favorite: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val col = GameColors.of(r.card.color)
    Column(
        modifier.shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = col, spotColor = col)
            .clip(RoundedCornerShape(14.dp)).background(Panel)
            .border(1.5.dp, Brush.verticalGradient(listOf(col, col.copy(alpha = 0.2f))), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box {
            CardImage(r.card.art(), Modifier.fillMaxWidth().aspectRatio(63f / 88f), corner = 0.dp, contentDescription = r.card.name)
            TierBadge(r.stats?.tier, Modifier.align(Alignment.TopStart).padding(6.dp))
            if (favorite) Text(
                "★", Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(CircleShape).background(Night.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                color = Gold, fontSize = 18.sp,
            )
            r.stats?.let {
                Text(
                    "#${it.rank}", Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                        .background(Night.copy(alpha = 0.75f)).padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall, color = Gold,
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(r.card.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(6.dp))
                Text(r.card.number, style = MaterialTheme.typography.labelSmall, color = Muted)
                Spacer(Modifier.weight(1f))
                r.stats?.let { Text("${it.decks} decks", style = MaterialTheme.typography.labelSmall, color = Gold) }
            }
        }
    }
}
