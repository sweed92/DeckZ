package fr.deckz.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.deckz.AppContainer
import fr.deckz.data.MyDeck
import fr.deckz.data.db.Card
import fr.deckz.ui.theme.GameColors
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Orange
import fr.deckz.ui.theme.Panel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val DECK_SIZE = 50
const val MAX_COPIES = 4

/** Liste de mes decks perso. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDecksScreen(c: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val decks by c.user.decks.collectAsStateWithLifecycle()
    val leaders by c.repository.leaders().collectAsStateWithLifecycle(initialValue = emptyList())
    val favs by c.user.favorites.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val byNumber = remember(leaders) {
        leaders.map { it.card }.groupBy { it.number }.mapValues { (_, l) -> l.firstOrNull { it.variantOf == null } ?: l.first() }
    }

    Scaffold(
        containerColor = Night,
        topBar = {
            TopAppBar(
                title = { Text("Mes decks") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picking = true }, containerColor = Orange, contentColor = Night,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nouveau deck", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp)) {
            if (decks.isEmpty()) item {
                Text(
                    "Aucun deck pour l'instant.\n\nCrée ton deck avec « Nouveau deck », ou copie un deck gagnant depuis la fiche d'un leader " +
                        "(onglet « Decks gagnants », bouton « + Mes decks »).\n\nDans un plan de bataille, tu pourras ensuite choisir ton deck " +
                        "pour que les conseils se basent sur tes cartes.",
                    color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            items(decks, key = { it.id }) { d ->
                val leader = byNumber[d.leader]
                Row(
                    Modifier.padding(vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel)
                        .clickable { onOpen(d.id) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CardImage(leader?.imageUrl, Modifier.width(48.dp).aspectRatio(63f / 88f), corner = 6.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(leader?.name ?: d.leader, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1)
                        Text("${d.size} / $DECK_SIZE cartes", style = MaterialTheme.typography.labelMedium,
                            color = if (d.size == DECK_SIZE) WinGreen else Gold)
                    }
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(GameColors.of(leader?.color)))
                }
            }
        }
    }

    if (picking) {
        val ordered = remember(leaders, favs) {
            leaders.map { it.card }.filter { it.variantOf == null }.sortedByDescending { it.number in favs }
        }
        LeaderPickerDialog(ordered, favs, onDismiss = { picking = false }) { leader ->
            picking = false
            val d = MyDeck(name = "Deck ${leader.name}", leader = leader.number)
            c.user.saveDeck(d)
            onOpen(d.id)
        }
    }
}

@Composable
private fun LeaderPickerDialog(leaders: List<Card>, favs: Set<String>, onDismiss: () -> Unit, onPick: (Card) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = leaders.filter { q.isBlank() || it.name.contains(q, true) || it.number.contains(q, true) || it.backName?.contains(q, true) == true }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisis le leader") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Rechercher") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(shown, key = { it.uid }) { l ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(l) }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardImage(l.imageUrl, Modifier.width(36.dp).aspectRatio(63f / 88f), corner = 4.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text((if (l.number in favs) "★ " else "") + l.name, style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${l.number} · ${GameColors.label(l.color)}", style = MaterialTheme.typography.labelSmall, color = Muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Éditeur d'un deck perso : nom, cartes (+ / −), ajout par recherche ou en collant une liste. */
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDeckEditorScreen(c: AppContainer, id: String, onBack: () -> Unit, onCard: (Int) -> Unit) {
    val decks by c.user.decks.collectAsStateWithLifecycle()
    val deck = decks.firstOrNull { it.id == id }
    var leader by remember { mutableStateOf<Card?>(null) }
    var info by remember { mutableStateOf<Map<String, Card>>(emptyMap()) }
    var query by rememberSaveable { mutableStateOf("") }
    var allColors by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Card>>(emptyList()) }
    var name by rememberSaveable(id) { mutableStateOf(deck?.name.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(deck?.leader) { deck?.let { leader = c.strategy.leaderByNumber(it.leader) } }
    LaunchedEffect(deck?.cards?.keys) {
        val missing = deck?.cards?.keys.orEmpty() - info.keys
        if (missing.isNotEmpty()) info = info + c.strategy.cardsByNumbers(missing.toList())
    }
    LaunchedEffect(query, allColors, leader) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(250)
        val colors = leader?.color?.split('/').orEmpty()
        results = c.strategy.searchCards(query).filter { card ->
            allColors || colors.isEmpty() || card.color == null || colors.any { card.color.contains(it) }
        }.take(60)
        info = info + results.associateBy { it.number }
    }
    // Le nom est enregistré une fois la saisie finie.
    LaunchedEffect(name) {
        delay(500)
        deck?.takeIf { it.name != name && name.isNotBlank() }?.let { c.user.saveDeck(it.copy(name = name.trim())) }
    }

    if (deck == null) {
        Box(Modifier.fillMaxSize().background(Night), contentAlignment = Alignment.Center) { Text("Deck supprimé", color = Muted) }
        return
    }
    fun setQty(number: String, qty: Int) {
        val q = qty.coerceIn(0, MAX_COPIES)
        c.user.saveDeck(deck.copy(cards = if (q == 0) deck.cards - number else deck.cards + (number to q)))
    }
    val rows = deck.cards.entries.map { (n, q) -> Triple(n, q, info[n]) }
        .sortedWith(compareBy({ it.third?.cost ?: 99 }, { it.first }))
    val size = deck.size

    Scaffold(
        containerColor = Night,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(deck.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(myDeckText(deck, leader, info)))
                        scope.launch { snack.showSnackbar("Liste copiée dans le presse-papiers") }
                    }) { Icon(Icons.Default.Share, "Copier la liste") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Supprimer le deck") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardImage(leader?.imageUrl, Modifier.width(64.dp).aspectRatio(63f / 88f).clickable { leader?.let { onCard(it.uid) } }, corner = 8.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nom du deck") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange), modifier = Modifier.fillMaxWidth())
                        Text(leader?.name ?: deck.leader, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "$size / $DECK_SIZE cartes" + when {
                        size < DECK_SIZE -> " · il en manque ${DECK_SIZE - size}"
                        size > DECK_SIZE -> " · ${size - DECK_SIZE} de trop"
                        else -> " · deck complet ✓"
                    },
                    color = if (size == DECK_SIZE) WinGreen else Gold, fontWeight = FontWeight.SemiBold,
                )
                if (rows.isNotEmpty()) {
                    val b = DoubleArray(8).also { arr -> rows.forEach { (_, q, card) -> arr[costBucket(card?.cost)] += q.toDouble() } }
                    CostCurve(b.toList(), height = 90.dp)
                }
            }
            if (rows.isNotEmpty()) item { SectionTitle("Mes cartes") }
            items(rows, key = { "m" + it.first }) { (n, q, card) ->
                QtyRow(card, n, q, onCard, onMinus = { setQty(n, q - 1) }, onPlus = { setQty(n, q + 1) })
            }
            item {
                SectionTitle("Ajouter des cartes")
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Nom, numéro ou trait (ex. Goku, FB01, Saiyan)") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Effacer") } },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange, unfocusedContainerColor = Panel, focusedContainerColor = Panel),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = allColors, onClick = { allColors = !allColors },
                        label = { Text(if (allColors) "Toutes les couleurs" else "Couleur du leader : ${GameColors.label(leader?.color)}") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Orange, selectedLabelColor = Night),
                    )
                    OutlinedButton(onClick = { pasting = true }) { Text("Coller une liste") }
                }
                if (query.isNotBlank() && results.isEmpty()) Text("Aucune carte trouvée", color = Muted, modifier = Modifier.padding(8.dp))
            }
            items(results, key = { "r" + it.number }) { card ->
                val q = deck.cards[card.number] ?: 0
                QtyRow(card, card.number, q, onCard, onMinus = { setQty(card.number, q - 1) }, onPlus = { setQty(card.number, q + 1) })
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Supprimer « ${deck.name} » ?") },
        text = { Text("Le deck sera effacé. Les parties déjà enregistrées avec lui sont conservées.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; c.user.deleteDeck(deck.id); onBack() }) { Text("Supprimer", color = Orange) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
    )

    if (pasting) PasteDialog(onDismiss = { pasting = false }) { text ->
        pasting = false
        scope.launch {
            val parsed = parseDeckList(text)
            val known = c.strategy.cardsByNumbers(parsed.keys.toList())
            val kept = parsed.filterKeys { n -> known[n]?.isLeader == false }.mapValues { it.value.coerceIn(1, MAX_COPIES) }
            val unknown = parsed.keys.filter { it !in known }
            info = info + known
            c.user.saveDeck(deck.copy(cards = kept))
            snack.showSnackbar(
                "${kept.values.sum()} cartes importées" + if (unknown.isNotEmpty()) " · inconnues : ${unknown.joinToString()}" else ""
            )
        }
    }
}

@Composable
private fun QtyRow(card: Card?, number: String, qty: Int, onCard: (Int) -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Panel).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(card?.imageUrl, Modifier.width(40.dp).aspectRatio(63f / 88f).clickable(enabled = card != null) { card?.let { onCard(it.uid) } }, corner = 5.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(card?.name ?: number, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$number · coût ${card?.cost ?: "?"}", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        TextButton(onClick = onMinus, enabled = qty > 0) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text("$qty", color = if (qty > 0) Gold else Muted, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp), textAlign = TextAlign.Center)
        TextButton(onClick = onPlus, enabled = qty < MAX_COPIES) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Coller une liste") },
        text = {
            Column {
                Text("Une carte par ligne, par exemple « 4 FB01-025 » ou « FB01-025 x4 ». La liste remplace les cartes actuelles du deck.",
                    style = MaterialTheme.typography.bodySmall, color = Muted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Orange))
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text) }, enabled = text.isNotBlank()) { Text("Importer", color = Orange) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private val numberRx = Regex("""\b([A-Z]{1,4}\d{0,3}-\d{2,3}[A-Za-z_]*)\b""")
private val qtyBefore = Regex("""^\s*(\d{1,2})\s*[xX×]?\s+""")
private val qtyAfter = Regex("""[xX×]\s*(\d{1,2})\b""")

/** « 4 FB01-025 Nom », « FB01-025 x4 »… -> numéro -> exemplaires (lignes sans numéro ignorées). */
fun parseDeckList(text: String): Map<String, Int> {
    val out = linkedMapOf<String, Int>()
    for (line in text.lines()) {
        if (line.trimStart().startsWith("//")) continue
        val number = numberRx.find(line)?.groupValues?.get(1) ?: continue
        val qty = qtyBefore.find(line)?.groupValues?.get(1)?.toInt()
            ?: qtyAfter.find(line.substringAfter(number))?.groupValues?.get(1)?.toInt() ?: 1
        out[number] = (out[number] ?: 0) + qty
    }
    return out
}

private fun myDeckText(d: MyDeck, leader: Card?, info: Map<String, Card>) = buildString {
    appendLine("// ${d.name}")
    appendLine("1 ${d.leader} ${leader?.name.orEmpty()}".trimEnd())
    d.cards.entries.sortedWith(compareBy({ info[it.key]?.cost ?: 99 }, { it.key }))
        .forEach { (n, q) -> appendLine("$q $n ${info[n]?.name.orEmpty()}".trimEnd()) }
}.trimEnd()
