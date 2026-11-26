package fr.deckz.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import fr.deckz.ui.theme.GameColors
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Night
import fr.deckz.ui.theme.Orange
import fr.deckz.ui.theme.Panel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(c: AppContainer, uid: Int, onBack: () -> Unit) {
    val card by c.repository.card(uid).collectAsStateWithLifecycle(null)
    Scaffold(
        containerColor = Night,
        topBar = {
            TopAppBar(
                title = { Text(card?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Night),
            )
        },
    ) { pad ->
        val k = card ?: return@Scaffold
        val col = GameColors.of(k.color)
        var flipped by remember { mutableStateOf(false) }
        val rot by animateFloatAsState(if (flipped) 180f else 0f, tween(600), label = "flip")
        val back = rot > 90f && k.backImageUrl != null
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .background(Brush.verticalGradient(listOf(col.copy(alpha = 0.25f), Night, Night))).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth(0.82f).aspectRatio(63f / 88f)
                    .graphicsLayer { rotationY = rot; cameraDistance = 14f * density }
                    .shadow(20.dp, RoundedCornerShape(16.dp), ambientColor = col, spotColor = col)
                    .clickable(enabled = k.backImageUrl != null) { flipped = !flipped }
            ) {
                CardImage(if (back) k.backImageUrl else k.imageUrl, Modifier.fillMaxSize().graphicsLayer { if (back) rotationY = 180f },
                    corner = 16.dp, contentDescription = k.name)
            }
            Spacer(Modifier.height(16.dp))
            Text(k.name, style = MaterialTheme.typography.headlineMedium)
            Text(k.number + (k.rarity?.let { " · $it" } ?: ""), color = Muted)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
                Info("Type", when (k.type) { "LEADER" -> "Leader"; "BATTLE" -> "Combat"; "EXTRA" -> "Extra"; else -> k.type.lowercase().replaceFirstChar { it.uppercase() } })
                Info("Couleur", GameColors.label(k.color))
                k.cost?.let { Info("Coût", it.toString()) }
                k.power?.let { Info("Puissance", it) }
                k.combo?.let { Info("Combo", it) }
                if (k.traits.isNotBlank()) Info("Traits", k.traits)
                Info("Extension", k.set)
            }
            Effect("Effet", k.effect)
            k.backName?.let {
                Effect("Face éveillée — $it" + (k.backPower?.let { p -> " ($p)" } ?: ""), k.backEffect.orEmpty())
            }
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Muted, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Effect(title: String, text: String) {
    if (text.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp)).background(Panel).padding(14.dp)) {
        Text(title.uppercase(), color = Orange, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        TranslatedText(text, lineHeight = 21.sp)
        Spacer(Modifier.height(2.dp))
        Box(Modifier.width(40.dp).height(2.dp).background(Gold.copy(alpha = 0.4f)))
    }
}
