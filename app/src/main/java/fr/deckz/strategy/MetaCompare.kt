package fr.deckz.strategy

import fr.deckz.data.MyDeck
import fr.deckz.data.db.Card
import kotlin.math.roundToInt

/** Une carte comparée : mes exemplaires, la part des decks gagnants qui la jouent et leur nombre moyen d'exemplaires. */
data class CompareRow(val card: Card, val mine: Int, val inclusion: Double, val avgCopies: Double) {
    /** Exemplaires conseillés d'après la méta (1 à 4). */
    val target get() = avgCopies.roundToInt().coerceIn(1, 4)
}

data class MetaComparison(
    /** Nombre de decks méta derrière la moyenne (null si inconnu). */
    val decks: Int?,
    /** Part du « noyau méta » (cartes jouées dans au moins la moitié des decks) présente dans mon deck, en %. */
    val similarity: Int,
    val missing: List<CompareRow>,
    val fewer: List<CompareRow>,
    val personal: List<CompareRow>,
) {
    val hasData get() = missing.isNotEmpty() || fewer.isNotEmpty() || personal.isNotEmpty() || similarity > 0
}

object MetaCompare {
    /** Carte du « noyau » : jouée dans au moins la moitié des decks gagnants. */
    const val CORE = 0.5
    /** En dessous, la carte est un choix perso (rarement jouée en méta). */
    const val RARE = 0.2

    /** [meta] = cartes de la liste méta moyenne (inclusion = part des decks, weight = inclusion × exemplaires). */
    fun of(deck: MyDeck, meta: List<WeightedCard>, myCards: Map<String, Card>, decks: Int?): MetaComparison {
        val metaByNumber = meta.filter { !it.card.isLeader }.associateBy { it.card.number }
        val rows = metaByNumber.values.map { w ->
            CompareRow(w.card, deck.cards[w.card.number] ?: 0, w.inclusion, if (w.inclusion > 0) w.weight / w.inclusion else 0.0)
        }
        val core = rows.filter { it.inclusion >= CORE }
        val coreTotal = core.sumOf { it.target }
        val coreMine = core.sumOf { minOf(it.mine, it.target) }
        val personal = deck.cards.mapNotNull { (n, q) ->
            val m = metaByNumber[n]
            if (m != null && m.inclusion >= RARE) null
            else myCards[n]?.let { CompareRow(it, q, m?.inclusion ?: 0.0, m?.let { w -> w.weight / w.inclusion } ?: 0.0) }
        }
        return MetaComparison(
            decks = decks,
            similarity = if (coreTotal == 0) 0 else (coreMine * 100.0 / coreTotal).roundToInt(),
            missing = core.filter { it.mine == 0 }.sortedByDescending { it.inclusion },
            fewer = core.filter { it.mine in 1 until it.target }.sortedByDescending { it.inclusion },
            personal = personal.sortedBy { it.inclusion },
        )
    }
}
