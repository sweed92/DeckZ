package fr.deckz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.deckz.R

val Orange = Color(0xFFFF8A00)
val Gold = Color(0xFFFFC400)
val Night = Color(0xFF0A0E18)
val Panel = Color(0xFF131A2B)
val PanelHigh = Color(0xFF1B2440)
val Muted = Color(0xFF8A94B0)

/** Couleurs des 5 couleurs du jeu. */
object GameColors {
    val Red = Color(0xFFE53935)
    val Blue = Color(0xFF1E88E5)
    val Green = Color(0xFF2E9E4F)
    val Yellow = Color(0xFFFDD835)
    val Black = Color(0xFF5A5F6E)
    val all = listOf("Red" to Red, "Blue" to Blue, "Green" to Green, "Yellow" to Yellow, "Black" to Black)
    val labels = mapOf("Red" to "Rouge", "Blue" to "Bleu", "Green" to "Vert", "Yellow" to "Jaune", "Black" to "Noir")

    fun of(color: String?): Color = all.firstOrNull { color?.startsWith(it.first) == true }?.second ?: Orange
    fun label(color: String?) = color?.split('/')?.joinToString("/") { labels[it] ?: it } ?: "—"
}

fun tierColor(tier: String?) = when (tier) {
    "S" -> Color(0xFFFF4D4D)
    "A" -> Orange
    "B" -> Gold
    "C" -> Color(0xFF6FA8FF)
    else -> Muted
}

val Bangers = FontFamily(Font(R.font.bangers))
val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val base = Typography()
private fun TextStyle.inter() = copy(fontFamily = Inter)
private fun TextStyle.bangers() = copy(fontFamily = Bangers, letterSpacing = 1.sp)

private val typography = Typography(
    displayLarge = base.displayLarge.bangers(), displayMedium = base.displayMedium.bangers(), displaySmall = base.displaySmall.bangers(),
    headlineLarge = base.headlineLarge.bangers(), headlineMedium = base.headlineMedium.bangers(), headlineSmall = base.headlineSmall.bangers(),
    titleLarge = base.titleLarge.bangers().copy(fontSize = 26.sp), titleMedium = base.titleMedium.inter().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.inter(), bodyLarge = base.bodyLarge.inter(), bodyMedium = base.bodyMedium.inter(),
    bodySmall = base.bodySmall.inter(), labelLarge = base.labelLarge.inter(), labelMedium = base.labelMedium.inter(),
    labelSmall = base.labelSmall.inter(),
)

private val colors = darkColorScheme(
    primary = Orange, onPrimary = Color(0xFF1A0E00),
    secondary = Gold, onSecondary = Color(0xFF1A1400),
    tertiary = Color(0xFF6FA8FF),
    background = Night, onBackground = Color(0xFFE8ECF6),
    surface = Night, onSurface = Color(0xFFE8ECF6),
    surfaceVariant = Panel, onSurfaceVariant = Muted,
    surfaceContainer = Panel, surfaceContainerHigh = PanelHigh, surfaceContainerHighest = PanelHigh,
    surfaceContainerLow = Panel, surfaceContainerLowest = Night,
    outline = Color(0xFF2C3654), outlineVariant = Color(0xFF222B45),
    error = Color(0xFFFF5A5A),
)

@Composable
fun DeckZTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
