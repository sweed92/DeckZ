package fr.deckz.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.deckz.AppContainer
import fr.deckz.data.db.Card
import fr.deckz.strategy.MatchupPlan
import fr.deckz.strategy.Side
import fr.deckz.strategy.Variant
import fr.deckz.ui.theme.Bangers
import fr.deckz.ui.theme.GameColors
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Orange
import fr.deckz.ui.theme.Panel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchupScreen(c: AppContainer, meUid: Int, oppUid: Int, onBack: () -> Unit, onCard: (Int) -> Unit) {
    var variants by remember { mutableStateOf<List<Variant>>(emptyList()) }
    var variantIdx by rememberSaveable { mutableIntStateOf(-1) }
    var me by remember { mutableStateOf<Side?>(null) }
    var opp by remember { mutableStateOf<Side?>(null) }
    var plan by remember { mutableStateOf<MatchupPlan?>(null) }

    LaunchedEffect(oppUid) { variants = c.strategy.variants(oppUid) }
    LaunchedEffect(meUid, oppUid, variantIdx, variants) {
        val m = c.strategy.side(meUid) ?: return@LaunchedEffect
        val o = c.strategy.side(oppUid, variants.getOrNull(variantIdx)) ?: return@LaunchedEffect
        me = m; opp = o
        plan = c.strategy.plan(m, o)
    }

    Scaffold(
        containerColor = Night,
        topBar = {
            TopAppBar(
                title = { Text("Plan de bataille") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
            )
        },
    ) { pad ->
        val m = me
        val o = opp
        val p = plan
        if (m == null || o == null || p == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Orange) }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { VersusHeader(m.leader, o.leader, onCard) }
            item { VerdictCard(p) }
            if (variants.isNotEmpty()) item {
                VariantChips(variants, variantIdx) { variantIdx = it }
            }
            item { Section("⚡", "Plan express", Orange, listOf(p.role) + p.express.drop(1), expanded = true) }
            item { Section("🌅", "Early game · tours 1-2", Color(0xFF6FA8FF), p.early) }
            item { Section("🔥", "Mid game · tours 3-4", Orange, p.mid) }
            item { Section("🐉", "End game · tour 5 et +", Color(0xFFFF5A5A), p.end) }
            item { Section("⚠", "Pièges à éviter", Gold, p.pitfalls) }
            if (p.watch.isNotEmpty()) item { WatchRow(p, onCard) }
            item { Section("🃏", "Mulligan", Color(0xFF3DDC84), p.mulligan) }
            item(key = "player_notes") { PlayerNotesCard(c, m.leader.number, o.leader.number) }
            p.curatedNote?.let { note -> item { Section("📝", "Note d'analyse", Orange, listOf(note), expanded = true) } }
            if (p.community.isNotEmpty()) item { CommunityCard(p) }
            item { GlossaryCard() }
        }
    }
}

@Composable
private fun VersusHeader(me: Card, opp: Card, onCard: (Int) -> Unit) {
    val cm = GameColors.of(me.color)
    val co = GameColors.of(opp.color)
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(cm.copy(alpha = 0.35f), Night, co.copy(alpha = 0.35f))))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Fighter(me, "TOI", cm, Modifier.weight(1f), -4f) { onCard(me.uid) }
            Text(
                "VS", fontFamily = Bangers, fontSize = 44.sp, color = Gold, modifier = Modifier.padding(horizontal = 8.dp),
                style = TextStyle(shadow = Shadow(Orange, blurRadius = 24f)),
            )
            Fighter(opp, "ADVERSAIRE", co, Modifier.weight(1f), 4f) { onCard(opp.uid) }
        }
    }
}

@Composable
private fun Fighter(card: Card, label: String, col: Color, modifier: Modifier, tilt: Float, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = col, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth(0.9f).aspectRatio(63f / 88f).rotate(tilt)
                .shadow(14.dp, RoundedCornerShape(12.dp), ambientColor = col, spotColor = col)
                .clickable(onClick = onClick)
        ) { CardImage(card.imageUrl, Modifier.fillMaxSize(), corner = 12.dp, contentDescription = card.name) }
        Spacer(Modifier.height(8.dp))
        Text(card.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(card.number, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun VerdictCard(p: MatchupPlan) {
    val col = if (p.mirror) Muted else verdictColor(p.verdict)
    Column(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp).clip(RoundedCornerShape(16.dp)).background(Panel)
            .border(1.dp, col.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Matchup", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp))
            Spacer(Modifier.width(10.dp))
            VerdictPill(p.verdict, mirror = p.mirror)
            Spacer(Modifier.weight(1f))
            Text(if (p.sourced) "sources" else "estimation", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        Spacer(Modifier.height(6.dp))
        Text("Toi : ${p.myArch.label} (tour fort ${p.mySpike}) · Lui : ${p.oppArch.label} (tour fort ${p.oppSpike})",
            style = MaterialTheme.typography.labelMedium, color = Gold)
        Text("${p.oppArch.label} : ${p.oppArch.blurb}.", style = MaterialTheme.typography.labelSmall, color = Muted)
        Spacer(Modifier.height(6.dp))
        p.reasons.take(3).forEach { Bullet(it, small = true) }
    }
}

@Composable
private fun VariantChips(variants: List<Variant>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Type de deck adverse", style = MaterialTheme.typography.labelMedium, color = Muted)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Orange, selectedLabelColor = Night)
            FilterChip(selected = selected == -1, onClick = { onSelect(-1) }, label = { Text("Liste méta (moyenne)") }, colors = colors)
            variants.forEachIndexed { i, v ->
                FilterChip(selected = selected == i, onClick = { onSelect(i) }, label = { Text("${v.name} · ${v.deckCount}") }, colors = colors)
            }
        }
    }
}

@Composable
private fun Section(icon: String, title: String, accent: Color, lines: List<String>, expanded: Boolean = false) {
    if (lines.isEmpty()) return
    var open by rememberSaveable(title) { mutableStateOf(expanded) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Column(
        Modifier.fillMaxWidth().padding(16.dp, 5.dp).clip(RoundedCornerShape(14.dp)).background(Panel)
            .animateContentSize()
    ) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text(icon, fontSize = 17.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Muted, modifier = Modifier.rotate(rot))
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(accent.copy(alpha = 0.25f)))
                Spacer(Modifier.height(8.dp))
                lines.forEach { Bullet(it, accent) }
            }
        }
    }
}

@Composable
private fun Bullet(text: String, accent: Color = Orange, small: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•", color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(14.dp))
        Text(text, style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            lineHeight = if (small) 17.sp else 20.sp)
    }
}

/** Bloc « Stratégie de joueurs » : un texte libre par matchup, sauvegardé automatiquement sur le téléphone. */
@Composable
private fun PlayerNotesCard(c: AppContainer, meNumber: String, oppNumber: String) {
    val accent = Color(0xFFB388FF)
    val saved = remember(meNumber, oppNumber) { c.notes.get(meNumber, oppNumber) }
    var text by rememberSaveable(meNumber, oppNumber) { mutableStateOf(saved) }
    var open by rememberSaveable("player_notes") { mutableStateOf(saved.isNotBlank()) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "chevron")

    // Sauvegarde automatique, une demi-seconde après la dernière frappe.
    LaunchedEffect(meNumber, oppNumber, text) {
        delay(500)
        c.notes.set(meNumber, oppNumber, text)
    }
    // Et à la sortie de l'écran, pour ne rien perdre si on quitte juste après avoir tapé.
    val latest by rememberUpdatedState(text)
    DisposableEffect(meNumber, oppNumber) {
        onDispose { c.notes.set(meNumber, oppNumber, latest) }
    }

    Column(
        Modifier.fillMaxWidth().padding(16.dp, 5.dp).clip(RoundedCornerShape(14.dp)).background(Panel)
            .animateContentSize()
    ) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text("🧠", fontSize = 17.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Stratégie de joueurs", style = MaterialTheme.typography.titleMedium)
                if (!open && text.isNotBlank()) {
                    Text(text.lineSequence().first(), style = MaterialTheme.typography.labelSmall, color = Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Muted, modifier = Modifier.rotate(rot))
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(accent.copy(alpha = 0.25f)))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    placeholder = { Text("Tes notes sur ce matchup : ce qui a marché, les cartes à garder, les habitudes des joueurs…", color = Muted) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = accent.copy(alpha = 0.35f),
                        cursorColor = accent,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text("Enregistré automatiquement sur ce téléphone, pour ce matchup uniquement.",
                    style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}

@Composable
private fun WatchRow(p: MatchupPlan, onCard: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("👁  Cartes adverses à surveiller", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(p.watch, key = { it.card.uid }) { w ->
                Column(
                    Modifier.width(150.dp).clip(RoundedCornerShape(12.dp)).background(Panel).clickable { onCard(w.card.uid) }.padding(8.dp)
                ) {
                    CardImage(w.card.imageUrl, Modifier.fillMaxWidth().aspectRatio(63f / 88f), corner = 8.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(w.card.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(w.card.number, style = MaterialTheme.typography.labelSmall, color = Muted)
                    Text(w.reason, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CommunityCard(p: MatchupPlan) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(16.dp, 5.dp).clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
        Text("👥  Ce qu'en dit la communauté", style = MaterialTheme.typography.titleMedium)
        p.community.forEach { n ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.title, style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                when (n.stance) {
                    "good" -> VerdictPill(fr.deckz.strategy.Verdict.FAVORABLE)
                    "bad" -> VerdictPill(fr.deckz.strategy.Verdict.DIFFICILE)
                }
            }
            if (n.text.isNotBlank()) Text(n.text, style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
            TextButton(onClick = { runCatching { uri.openUri(n.url) } }) { Text("Lire le guide complet (en anglais)", color = Orange) }
        }
        Text("Source : db-fusionworld.com (deck profiles).", style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

@Composable
private fun GlossaryCard() {
    Section(
        "📖", "Lexique débutant", Muted,
        listOf(
            "Éveil (Awaken) : quand sa condition est remplie, le leader se retourne sur sa face éveillée, plus puissante (souvent 20000 et de nouveaux effets).",
            "Combo : pendant l'attaque adverse, tu peux défausser des cartes de ta main pour ajouter leur puissance de combo à ton défenseur.",
            "Ki : marqueur d'énergie posé sous une carte. Beaucoup de decks récents le cumulent puis le dépensent pour des effets.",
            "Mode repos : une carte qui a attaqué (ou qu'on a « reposée ») est tournée ; une carte en repos peut être attaquée par l'adversaire.",
            "Evolve : se joue par-dessus une carte précise déjà en jeu, souvent pour un coût réduit.",
            "Double Strike / Critical : l'attaque retire 2 vies, ou envoie la vie touchée dans la défausse (l'adversaire ne la récupère pas en main).",
            "Blocker : peut se mettre en repos pour devenir la cible d'une attaque à la place.",
            "Tour fort : le tour où un deck fait son jeu le plus puissant ; prépare ta défense le tour d'avant.",
        ),
    )
}
