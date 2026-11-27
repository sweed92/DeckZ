package fr.deckz.ui

import fr.deckz.data.LeaderArt
import kotlinx.coroutines.flow.flowOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.border
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.deckz.AppContainer
import fr.deckz.data.db.Card
import fr.deckz.data.db.Deck
import fr.deckz.data.db.LeaderStats
import fr.deckz.data.db.UsageRow
import fr.deckz.ui.theme.GameColors
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Orange
import fr.deckz.ui.theme.Panel
import fr.deckz.ui.theme.tierColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderScreen(
    c: AppContainer, uid: Int, onBack: () -> Unit, onCard: (Int) -> Unit, onDeck: (String) -> Unit, onMatchups: (Int) -> Unit,
) {
    val repo = c.repository
    val card by repo.card(uid).collectAsStateWithLifecycle(null)
    val stats by repo.stats(uid).collectAsStateWithLifecycle(null)
    val usage by repo.usage(uid).collectAsStateWithLifecycle(emptyList())
    val decks by repo.decksOf(uid).collectAsStateWithLifecycle(emptyList())
    val listed by repo.leaderDeckCards(uid).collectAsStateWithLifecycle(emptyList())
    val sync by repo.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val favs by c.user.favorites.collectAsStateWithLifecycle()
    val art by c.user.art.collectAsStateWithLifecycle()
    val baseUid = card?.let { it.variantOf ?: it.uid }
    val versions by remember(baseUid) { baseUid?.let { repo.leaderVersions(it) } ?: flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())
    // Garde à jour les adresses d'images de l'illustration choisie (au cas où la source les change).
    LaunchedEffect(versions, art) {
        val number = card?.artKey ?: return@LaunchedEffect
        val chosen = art[number] ?: return@LaunchedEffect
        versions.firstOrNull { it.uid == chosen.uid }?.let { v ->
            if (v.imageUrl != chosen.imageUrl || v.backImageUrl != chosen.backImageUrl) c.user.setArt(number, LeaderArt(v.uid, v.imageUrl, v.backImageUrl))
        }
    }

    Scaffold(
        containerColor = Night,
        topBar = {
            TopAppBar(
                title = { Text(card?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    card?.let { l ->
                        val fav = l.number in favs
                        IconButton(onClick = { c.user.toggleFavorite(l.number) }) {
                            Icon(
                                if (fav) Icons.Filled.Star else Icons.Outlined.Star,
                                if (fav) "Retirer des favoris" else "Ajouter aux favoris",
                                tint = if (fav) Gold else Muted,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
            )
        },
    ) { pad ->
        val leader = card ?: return@Scaffold
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { StaleBanner(sync.lastSuccess, sync.lastFailed && !sync.running) }
            item { LeaderHeader(leader, stats) }
            if (versions.size > 1) item {
                VersionsRow(versions, selectedUid = art[leader.artKey]?.uid ?: versions.first().uid) { v ->
                    c.user.setArt(leader.artKey, if (v.variantOf == null) null else LeaderArt(v.uid, v.imageUrl, v.backImageUrl))
                }
            }
            item { BattlePlanButton { onMatchups(uid) } }
            item {
                PrimaryTabRow(
                    selectedTabIndex = tab, containerColor = Night, contentColor = Orange,
                    indicator = { TabRowDefaults.PrimaryIndicator(Modifier.tabIndicatorOffset(tab), color = Orange, width = 48.dp) },
                ) {
                    listOf("Cartes clés", "Decks gagnants", "Courbe").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i },
                            text = { Text(t, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                            unselectedContentColor = Muted)
                    }
                }
            }
            when (tab) {
                0 -> if (usage.isEmpty()) item { Empty("Pas encore de données d'usage pour ce leader") }
                else itemsIndexed(usage, key = { _, u -> "u" + u.card.uid }) { i, u -> UsageItem(u, Modifier.appear(i)) { onCard(u.card.uid) } }
                1 -> if (decks.isEmpty()) item { Empty("Aucun deck classé récemment") }
                else itemsIndexed(decks, key = { _, d -> "d" + d.id }) { i, d -> DeckItem(d, Modifier.appear(i)) { onDeck(d.id) } }
                else -> item { CurveTab(usage, listed, decks.count { it.hasList }) }
            }
        }
    }
}

/** Rangée « Versions » : normale + alternatives ; la version touchée devient l'illustration du leader partout. */
@Composable
private fun VersionsRow(versions: List<Card>, selectedUid: Int, onSelect: (Card) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
        Text("Versions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Text("Touche une illustration pour l'utiliser dans toute l'appli", style = MaterialTheme.typography.labelSmall,
            color = Muted, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(versions, key = { _, v -> v.uid }) { i, v ->
                val sel = v.uid == selectedUid
                Column(Modifier.width(84.dp).clickable { onSelect(v) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    CardImage(
                        v.backImageUrl ?: v.imageUrl,
                        Modifier.fillMaxWidth().aspectRatio(63f / 88f)
                            .border(if (sel) 3.dp else 1.dp, if (sel) Orange else Muted.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        corner = 8.dp, contentDescription = v.name,
                    )
                    Text(
                        (if (i == 0) "Normale" else if (versions.size == 2) "Alternative" else "Alternative $i") +
                            (v.rarity?.takeIf { '★' in it || '*' in it }?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall, color = if (sel) Orange else Muted,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderHeader(card: Card, stats: LeaderStats?) {
    val front = card.frontArt()
    val back = card.backArt()
    // Le leader s'affiche d'abord sur sa face éveillée ; un toucher le retourne.
    var flipped by remember(card.uid) { mutableStateOf(back != null) }
    val rot by animateFloatAsState(if (flipped) 180f else 0f, tween(600, easing = FastOutSlowInEasing), label = "flip")
    val col = GameColors.of(card.color)
    val showBack = rot > 90f && back != null
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(col.copy(alpha = 0.28f), Night))).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(220.dp).aspectRatio(63f / 88f)
                .graphicsLayer { rotationY = rot; cameraDistance = 14f * density }
                .shadow(18.dp, RoundedCornerShape(14.dp), ambientColor = col, spotColor = col)
                .clickable(enabled = back != null) { flipped = !flipped }
        ) {
            CardImage(
                if (showBack) back else front,
                Modifier.fillMaxSize().graphicsLayer { if (showBack) rotationY = 180f }, corner = 14.dp, contentDescription = card.name,
            )
        }
        if (back != null) {
            Text(if (flipped) "Face éveillée — touchez pour voir la face normale" else "Face normale — touchez pour voir la face éveillée",
                style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(if (flipped) card.backName ?: card.name else card.name, style = MaterialTheme.typography.headlineMedium)
        Text("${card.number} · ${GameColors.label(card.color)} · ${if (flipped) card.backPower ?: "" else card.power ?: ""}",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox(stats?.tier ?: "—", "Tier", Modifier.weight(1f), color = tierColor(stats?.tier))
            StatBox(stats?.decks?.toString() ?: "0", "Decks", Modifier.weight(1f))
            StatBox(stats?.topCuts?.toString() ?: "0", "Top cuts", Modifier.weight(1f))
            StatBox(stats?.wins?.toString() ?: "0", "Victoires", Modifier.weight(1f), color = Gold)
        }
        stats?.let {
            Text("Rang #${it.rank} · ${"%.1f".format(it.share * 100)} % de la méta", color = Muted,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
        }
        card.awakenCondition?.let { cond ->
            EffectBox("Condition d'éveil", cond, Orange)
        }
        EffectBox("Effet", card.effect, col)
        card.backEffect?.let { EffectBox("Effet éveillé", it, Gold) }
    }
}

@Composable
private fun EffectBox(title: String, text: String, accent: androidx.compose.ui.graphics.Color) {
    if (text.isBlank()) return
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(Panel).padding(12.dp)
    ) {
        Text(title.uppercase(), color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
    }
}

@Composable
private fun UsageItem(u: UsageRow, modifier: Modifier, onClick: () -> Unit) {
    val col = GameColors.of(u.card.color)
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(u.card.imageUrl, Modifier.width(48.dp).aspectRatio(63f / 88f), corner = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CostDot(u.card.cost)
                Spacer(Modifier.width(6.dp))
                Text(u.card.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("×%.1f".format(u.usage.avgCopies), color = Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(u.card.number, style = MaterialTheme.typography.labelSmall, color = Muted)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { u.usage.inclusion.toFloat() }, color = col, trackColor = col.copy(alpha = 0.15f),
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)), drawStopIndicator = {},
                )
                Spacer(Modifier.width(8.dp))
                Text("${(u.usage.inclusion * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
            }
        }
    }
}

@Composable
fun CostDot(cost: Int?) {
    Box(Modifier.size(20.dp).clip(RoundedCornerShape(50)).background(Orange), contentAlignment = Alignment.Center) {
        Text((cost ?: 0).toString(), color = Night, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DeckItem(d: Deck, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(12.dp))
            .background(Panel).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val p = d.placement
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                .background(if (p == 1) Brush.verticalGradient(listOf(Gold, Orange)) else Brush.verticalGradient(listOf(Orange.copy(alpha = 0.25f), Orange.copy(alpha = 0.1f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(placementLabel(p), fontWeight = FontWeight.Bold, color = if (p == 1) Night else Gold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.event ?: d.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(fmtIsoDate(d.date) + if (d.hasList) " · liste dispo" else "", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

@Composable
private fun CurveTab(usage: List<UsageRow>, listed: List<fr.deckz.data.db.DeckCardRow>, deckCount: Int) {
    val buckets = DoubleArray(8)
    val fromDecks = deckCount > 0
    if (fromDecks) {
        listed.filter { !it.card.isLeader }.forEach { buckets[costBucket(it.card.cost)] += it.qty.toDouble() / deckCount }
    } else {
        usage.forEach { buckets[costBucket(it.card.cost)] += it.usage.inclusion * it.usage.avgCopies }
    }
    Column(Modifier.padding(16.dp)) {
        SectionTitle("Courbe de coûts moyenne")
        CostCurve(buckets.toList())
        Text(
            if (fromDecks) "Moyenne sur $deckCount liste(s) de decks classés." else "Estimation à partir des cartes clés (inclusion × exemplaires).",
            style = MaterialTheme.typography.labelSmall, color = Muted, modifier = Modifier.padding(top = 8.dp),
        )
        Text("Total moyen : %.1f cartes".format(buckets.sum()), style = MaterialTheme.typography.labelMedium, color = Gold)
    }
}

@Composable
fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { Text(text, color = Muted) }
}

@Composable
private fun BattlePlanButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = Orange, spotColor = Orange)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Orange, Gold)))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚔", fontSize = 28.sp, color = Night)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("PLANS DE BATAILLE", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp), color = Night)
            Text("Comment jouer contre chaque leader de la méta", color = Night.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        }
        Text("›", fontSize = 34.sp, color = Night)
    }
}
