package fr.deckz.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import fr.deckz.data.LeaderArt
import fr.deckz.data.db.Card

/** Illustrations de leaders choisies par l'utilisateur, fournies à tout l'arbre d'écrans par DeckZRoot. */
val LocalLeaderArt = staticCompositionLocalOf<Map<String, LeaderArt>> { emptyMap() }

/** Clé de l'illustration : numéro de base du leader (FB09-001_PR -> FB09-001), commun à toutes ses versions. */
val Card.artKey: String get() = number.substringBefore('_')

/** Recto : l'illustration choisie si c'est un leader personnalisé, sinon la sienne. */
@Composable
fun Card.frontArt(): String = if (isLeader) LocalLeaderArt.current[artKey]?.imageUrl ?: imageUrl else imageUrl

/** Image affichée partout : les leaders apparaissent sur leur face éveillée quand elle existe, sinon le recto. */
@Composable
fun Card.art(): String = backArt() ?: frontArt()

/** Verso (face éveillée) à afficher, en suivant l'illustration choisie. */
@Composable
fun Card.backArt(): String? = if (isLeader) LocalLeaderArt.current[artKey]?.let { it.backImageUrl ?: backImageUrl } ?: backImageUrl else backImageUrl
