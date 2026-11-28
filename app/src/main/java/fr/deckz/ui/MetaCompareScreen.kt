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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.deckz.AppContainer
import fr.deckz.strategy.CompareRow
import fr.deckz.strategy.MetaComparison
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Orange
import fr.deckz.ui.theme.Panel
import kotlin.math.roundToInt

private val MissingRed = Color(0xFFFF5A5A)
private val FewerOrange = Color(0xFFFFA94D)
private val PersonalBlue = Color(0xFF4FC3F7)

/** Comparer un deck perso à la méta : ressemblance, cartes clés manquantes, cartes en moins, choix perso. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaCompareScreen(c: AppContainer, deckId: String, onBack: () -> Unit, onCard: (Int) -> Unit) {
    val decks by c.user.decks.collectAsStateWithLifecycle()
    val deck = decks.firstOrNull { it.id == deckId }
    var cmp by remember { mutableStateOf<MetaComparison?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(deck) {
        cmp = deck?.let { c.strategy.compareToMeta(it) }
        loaded = true
    }

    Scaffold(
        containerColor = Night,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Comparer à la méta")
                        deck?.let {
                            Text(it.name + (cmp?.decks?.let { n -> " vs $n decks gagnants" } ?: ""),
                                style = MaterialTheme.typography.labelMedium, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
            )
        },
    ) { pad ->
        val r = cmp
        when {
            deck == null -> Empty("Deck supprimé")
            !loaded -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Orange) }
            r == null || !r.hasData -> Box(Modifier.padding(pad).fillMaxSize()) {
                Text(
                    "Pas encore de données méta pour ce leader : il n'apparaît pas dans les decks gagnants récents.",
                    color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
                        Text("Ressemblance avec la méta", style = MaterialTheme.typography.labelMedium, color = Muted)
                        Text("${r.similarity} %", style = MaterialTheme.typography.headlineMedium, color = Gold)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { r.similarity / 100f }, color = Orange, trackColor = Night,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Part des cartes jouées par la majorité des gagnants que tu as dans ton deck. Ton deck : ${deck.size} / $DECK_SIZE cartes.",
                            style = MaterialTheme.typography.labelSmall, color = Muted,
                        )
                    }
                }
                section(
                    "Cartes clés qui te manquent", MissingRed, r.missing,
                    empty = "Tu as toutes les cartes jouées par la majorité des gagnants.",
                ) { row ->
                    CompareItem(row, "Dans ${pct(row.inclusion)} des decks · ${copies(row.avgCopies)} ex. en moyenne", onCard) {
                        OutlinedButton(onClick = { c.user.saveDeck(deck.copy(cards = deck.cards + (row.card.number to row.target))) }) {
                            Text("+ Ajouter ${row.target}")
                        }
                    }
                }
                section("Tu en joues moins que la méta", FewerOrange, r.fewer, empty = null) { row ->
                    CompareItem(row, "Toi : ${row.mine} ex. · Méta : ${row.target} ex.", onCard) {
                        OutlinedButton(onClick = {
                            c.user.saveDeck(deck.copy(cards = deck.cards + (row.card.number to (row.mine + 1).coerceAtMost(MAX_COPIES))))
                        }) { Text("+1") }
                    }
                }
                section("Tes choix perso", PersonalBlue, r.personal, empty = null) { row ->
                    CompareItem(
                        row,
                        "Toi : ${row.mine} ex. · " + if (row.inclusion > 0) "Dans ${pct(row.inclusion)} des decks seulement" else "Jamais jouée en méta",
                        onCard,
                    ) {}
                }
                item {
                    Text(
                        "Comparaison avec la liste moyenne des decks gagnants récents de ce leader (DeckPlanet). " +
                            "Ce sont des repères, pas des obligations : à toi de voir ce qui colle à ton style.",
                        style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String, color: Color, rows: List<CompareRow>, empty: String?, row: @Composable (CompareRow) -> Unit,
) {
    if (rows.isEmpty() && empty == null) return
    item(key = "h$title") {
        Text(title, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
        if (rows.isEmpty() && empty != null) Text(empty, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
    items(rows, key = { title + it.card.number }) { row(it) }
}

@Composable
private fun CompareItem(r: CompareRow, subtitle: String, onCard: (Int) -> Unit, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Panel)
            .clickable { onCard(r.card.uid) }.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(r.card.imageUrl, Modifier.width(40.dp).aspectRatio(63f / 88f), corner = 5.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        action()
    }
}

private fun pct(x: Double) = "${(x * 100).roundToInt()} %"
private fun copies(x: Double) = if (x >= 3.95 || x == x.roundToInt().toDouble()) x.roundToInt().toString() else "%.1f".format(x)
