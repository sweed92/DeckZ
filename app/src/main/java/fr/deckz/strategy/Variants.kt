package fr.deckz.strategy

import fr.deckz.data.db.Card
import fr.deckz.data.db.DeckCardOfDeck

/** Carte d'un deck pondérée : nombre moyen d'exemplaires par deck (inclusion × copies). */
data class WeightedCard(val card: Card, val weight: Double, val inclusion: Double)

/** Type de deck d'un leader, trouvé en regroupant les listes classées qui se ressemblent. */
data class Variant(val name: String, val deckCount: Int, val signature: List<Card>, val cards: List<WeightedCard>)

object Variants {
    private const val MIN_DECKS = 4
    private const val SIMILARITY = 0.55

    fun of(rows: List<DeckCardOfDeck>, leaderNumber: String): List<Variant> {
        val decks = rows.filter { it.card.number != leaderNumber && !it.card.isLeader }.groupBy { it.deckId }
        if (decks.size < MIN_DECKS) return emptyList()
        val sets = decks.mapValues { (_, r) -> r.map { it.card.number }.toSet() }

        // Regroupement glouton : un deck rejoint le groupe dont le "noyau" (cartes présentes dans >= 50 % des decks) lui ressemble le plus.
        val clusters = mutableListOf<MutableList<String>>()
        fun core(c: List<String>): Set<String> {
            val counts = c.flatMap { sets.getValue(it) }.groupingBy { it }.eachCount()
            return counts.filterValues { it * 2 >= c.size }.keys
        }
        for ((id, set) in sets.entries.sortedByDescending { it.value.size }) {
            val best = clusters.maxByOrNull { jaccard(core(it), set) }
            if (best != null && jaccard(core(best), set) >= SIMILARITY) best += id else clusters += mutableListOf(id)
        }
        val kept = clusters.filter { it.size >= 2 }
        if (kept.size < 2) return emptyList()

        val overall = frequency(decks.keys.toList(), decks)
        return kept.sortedByDescending { it.size }.map { ids ->
            val freq = frequency(ids, decks)
            val signature = freq.entries.sortedByDescending { (n, f) -> f.first - (overall[n]?.first ?: 0.0) }
                .take(2).map { it.value.third }
            Variant(
                name = "Variante ${signature.firstOrNull()?.name ?: "?"}",
                deckCount = ids.size,
                signature = signature,
                cards = freq.values.map { (inc, copies, card) -> WeightedCard(card, inc * copies, inc) }.sortedByDescending { it.weight },
            )
        }
    }

    /** numéro -> (inclusion, copies moyennes quand jouée, carte). */
    private fun frequency(ids: List<String>, decks: Map<String, List<DeckCardOfDeck>>): Map<String, Triple<Double, Double, Card>> {
        val byNumber = ids.flatMap { decks.getValue(it) }.groupBy { it.card.number }
        return byNumber.mapValues { (_, r) ->
            val inDecks = r.map { it.deckId }.distinct().size
            Triple(inDecks.toDouble() / ids.size, r.sumOf { it.qty }.toDouble() / inDecks, r.first().card)
        }
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() && b.isEmpty()) 1.0 else a.intersect(b).size.toDouble() / a.union(b).size
}
