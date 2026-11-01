package fr.deckz.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import fr.deckz.ui.theme.Bangers
import fr.deckz.ui.theme.Gold
import fr.deckz.ui.theme.Muted
import fr.deckz.ui.theme.Orange
import fr.deckz.ui.theme.Panel
import fr.deckz.ui.theme.PanelHigh
import fr.deckz.ui.theme.tierColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Placeholder shimmer animé. */
@Composable
fun Modifier.shimmer(): Modifier {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-400f, 1200f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "x")
    return background(
        Brush.linearGradient(listOf(Panel, PanelHigh, Panel), start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x + 400f, 400f))
    )
}

/** Image de carte (ratio 63x88) avec shimmer pendant le chargement. */
@Composable
fun CardImage(url: String?, modifier: Modifier = Modifier, corner: Dp = 10.dp, contentDescription: String? = null) {
    var loading by remember(url) { mutableStateOf(true) }
    Box(modifier.clip(RoundedCornerShape(corner)).then(if (loading) Modifier.shimmer() else Modifier.background(Panel))) {
        AsyncImage(
            model = url, contentDescription = contentDescription, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onState = { loading = it is AsyncImagePainter.State.Loading || it is AsyncImagePainter.State.Empty },
        )
    }
}

@Composable
fun TierBadge(tier: String?, modifier: Modifier = Modifier, big: Boolean = false) {
    if (tier == null) return
    val c = tierColor(tier)
    Box(
        modifier.shadow(4.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(c, c.copy(alpha = 0.75f))))
            .padding(horizontal = if (big) 14.dp else 8.dp, vertical = if (big) 4.dp else 1.dp),
    ) {
        Text(tier, fontFamily = Bangers, color = Color(0xFF14100A), fontSize = if (big) 26.sp else 16.sp)
    }
}

@Composable
fun StaleBanner(lastSuccess: Long, failed: Boolean, modifier: Modifier = Modifier) {
    if (!failed || lastSuccess == 0L) return
    Text(
        "Données du ${fmtDay(lastSuccess)} — mise à jour échouée",
        modifier.fillMaxWidth().background(Orange.copy(alpha = 0.14f)).padding(horizontal = 16.dp, vertical = 6.dp),
        color = Gold, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
    )
}

/** Apparition en fondu + glissement, décalée selon l'index. */
@Composable
fun Modifier.appear(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index.coerceAtMost(12) * 35).toLong())
        a.animateTo(1f, tween(320))
    }
    return graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 40f }
}

@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Panel).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Bangers, fontSize = 26.sp, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}

/** Histogramme de courbe de coûts : buckets 0..7+ -> valeur (nb moyen de cartes). */
@Composable
fun CostCurve(buckets: List<Double>, modifier: Modifier = Modifier, height: Dp = 150.dp) {
    val max = (buckets.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Row(modifier.fillMaxWidth().height(height + 40.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        buckets.forEachIndexed { i, v ->
            val anim = remember { Animatable(0f) }
            LaunchedEffect(v) { anim.animateTo((v / max).toFloat(), tween(500 + i * 60)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (v >= 10) "%.0f".format(v) else "%.1f".format(v), style = MaterialTheme.typography.labelSmall, color = Gold)
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier.fillMaxWidth().height(height * anim.value + 2.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(Brush.verticalGradient(listOf(Gold, Orange)))
                )
                Spacer(Modifier.height(4.dp))
                Text(if (i == buckets.lastIndex) "$i+" else "$i", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun costBucket(cost: Int?) = (cost ?: 0).coerceIn(0, 7)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(Orange))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp))
    }
}

fun fmtDay(ms: Long): String = SimpleDateFormat("dd/MM", Locale.FRANCE).format(Date(ms))
fun fmtDateTime(ms: Long): String = SimpleDateFormat("dd/MM/yyyy à HH:mm", Locale.FRANCE).format(Date(ms))
fun fmtIsoDate(iso: String?): String = iso?.take(10)?.split('-')?.takeIf { it.size == 3 }?.let { "${it[2]}/${it[1]}/${it[0]}" } ?: "—"
fun placementLabel(p: Int?): String = when (p) {
    null -> "—"
    1 -> "1er"
    else -> "${p}e"
}
