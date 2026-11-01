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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.deckz.AppContainer
import fr.deckz.data.db.DeckCardRow
import fr.deckz.ui.theme.GameColors
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Orange
import fr.deckz.ui.theme.Panel
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScreen(c: AppContainer, id: String, onBack: () -> Unit, onCard: (Int) -> Unit) {
    val deck by c.repository.deck(id).collectAsStateWithLifecycle(null)
    val cards by c.repository.deckCards(id).collectAsStateWithLifecycle(emptyList())
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(id) { failed = !c.repository.ensureDeckList(id); loading = false }
    val clipboard = LocalClipboardManager.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val leader = cards.filter { it.card.isLeader }
    val main = cards.filterNot { it.card.isLeader }
    val groups = main.groupBy { costBucket(it.card.cost) }.toSortedMap()
    val total = main.sumOf { it.qty }

    Scaffold(
        containerColor = Night,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(deck?.event ?: "Deck", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
            )
        },
        floatingActionButton = {
            if (cards.isNotEmpty()) ExtendedFloatingActionButton(
                onClick = {
                    clipboard.setText(AnnotatedString(deckText(deck?.name, leader, main)))
                    scope.launch { snack.showSnackbar("Liste copiée dans le presse-papiers") }
                },
                containerColor = Orange, contentColor = Night,
                icon = { Icon(Icons.Default.Share, null) }, text = { Text("Copier la liste", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
                    Text(deck?.name ?: "", style = MaterialTheme.typography.titleMedium)
                    Text("${placementLabel(deck?.placement)} · ${fmtIsoDate(deck?.date)} · $total cartes", color = Gold,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            if (cards.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        if (loading) CircularProgressIndicator(color = Orange)
                        else Text(if (failed) "Liste indisponible hors ligne" else "Liste vide", color = Muted)
                    }
                }
            } else {
                item {
                    SectionTitle("Courbe de coûts")
                    val b = DoubleArray(8).also { arr -> main.forEach { arr[costBucket(it.card.cost)] += it.qty.toDouble() } }
                    CostCurve(b.toList(), height = 110.dp)
                }
                leader.forEach { l -> item(key = "L${l.card.uid}") { SectionTitle("Leader"); DeckRow(l, Modifier) { onCard(l.card.uid) } } }
                groups.forEach { (cost, rows) ->
                    item(key = "h$cost") { SectionTitle("Coût ${if (cost == 7) "7+" else cost} · ${rows.sumOf { it.qty }}") }
                    itemsIndexed(rows, key = { _, r -> "c${r.card.uid}" }) { i, r -> DeckRow(r, Modifier.appear(i)) { onCard(r.card.uid) } }
                }
            }
        }
    }
}

@Composable
private fun DeckRow(r: DeckCardRow, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Panel)
            .clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${r.qty}×", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium)
        CardImage(r.card.imageUrl, Modifier.width(40.dp).aspectRatio(63f / 88f), corner = 5.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${r.card.number} · ${r.card.type.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        Box(Modifier.width(4.dp).aspectRatio(0.1f).clip(RoundedCornerShape(2.dp)).background(GameColors.of(r.card.color)))
    }
}

private fun deckText(name: String?, leader: List<DeckCardRow>, main: List<DeckCardRow>) = buildString {
    name?.let { appendLine("// $it") }
    leader.forEach { appendLine("${it.qty} ${it.card.number} ${it.card.name}") }
    main.sortedWith(compareBy({ it.card.cost ?: 0 }, { it.card.number })).forEach { appendLine("${it.qty} ${it.card.number} ${it.card.name}") }
}.trimEnd()
