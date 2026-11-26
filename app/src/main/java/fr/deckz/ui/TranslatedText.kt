package fr.deckz.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import fr.deckz.data.CardTranslator
import fr.deckz.ui.theme.Muted

/**
 * Texte de carte (anglais d'origine) affiché traduit en français.
 * Tant que la traduction n'est pas prête, l'original s'affiche ; une petite ligne permet de basculer.
 */
@Composable
fun TranslatedText(text: String, style: TextStyle = MaterialTheme.typography.bodyMedium, lineHeight: TextUnit = TextUnit.Unspecified) {
    var fr by remember(text) { mutableStateOf(CardTranslator.cached(text)) }
    var failed by remember(text) { mutableStateOf(false) }
    var showOriginal by rememberSaveable(text) { mutableStateOf(false) }
    LaunchedEffect(text) {
        if (fr == null) {
            val t = CardTranslator.translate(text)
            if (t == null) failed = true else fr = t
        }
    }
    val translated = fr
    Column {
        Text(if (translated != null && !showOriginal) translated else text, style = style, lineHeight = lineHeight)
        val hint = when {
            translated != null && showOriginal -> "Texte original · voir la traduction"
            translated != null -> "Traduction automatique · voir l'original"
            failed -> "Traduction indisponible (connexion nécessaire la première fois)"
            else -> "Traduction en cours…"
        }
        Text(
            hint, style = MaterialTheme.typography.labelSmall, color = Muted,
            modifier = Modifier.padding(top = 4.dp).then(
                if (translated != null) Modifier.clickable { showOriginal = !showOriginal } else Modifier
            ),
        )
    }
}
