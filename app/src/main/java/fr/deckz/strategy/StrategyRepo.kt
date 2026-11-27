package fr.deckz.strategy

import android.content.Context
import fr.deckz.data.db.AppDb
import fr.deckz.data.db.Card
import fr.deckz.data.db.LeaderStats
import fr.deckz.data.db.UsageRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OpponentRow(val leader: Card, val stats: LeaderStats?, val verdict: Verdict, val mirror: Boolean, val hasProfile: Boolean)

/** Assemble les données des deux camps et calcule les plans de bataille (hors ligne). */
class StrategyRepo(context: Context, db: AppDb) {
    private val dao = db.dao()
    val book: ProfileBook by lazy { ProfileBook.load(context) }

    fun guidesFor(numbers: List<String>) = dao.guidesFor(numbers)

    private fun weighted(rows: List<UsageRow>) = rows.map { WeightedCard(it.card, it.usage.inclusion * it.usage.avgCopies, it.usage.inclusion) }

    /** Adversaires possibles : leaders de la méta (ou tous), avec verdict rapide. */
    suspend fun opponents(meUid: Int, all: Boolean): List<OpponentRow> = withContext(Dispatchers.Default) {
        val leaders = dao.leadersNow()
        val usage = dao.allUsageNow().groupBy { it.usage.leaderUid }
        val guides = dao.guidesNow()
        val meRow = leaders.firstOrNull { it.card.uid == meUid } ?: dao.cardNow(meUid)?.let { fr.deckz.data.db.LeaderRow(it, null) }
            ?: return@withContext emptyList()
        val me = Side(meRow.card, book.profile(meRow.card.number), meRow.stats, weighted(usage[meUid].orEmpty()))
        leaders.filter { all || it.stats != null }.map { r ->
            val opp = Side(r.card, book.profile(r.card.number), r.stats, weighted(usage[r.card.uid].orEmpty()))
            OpponentRow(r.card, r.stats, MatchupEngine.verdictOnly(me, opp, book, guides), r.card.number == meRow.card.number, opp.profile != null)
        }
    }

    suspend fun side(uid: Int, variant: Variant? = null): Side? = withContext(Dispatchers.Default) {
        val card = dao.cardNow(uid) ?: return@withContext null
        val stats = dao.leadersNow().firstOrNull { it.card.uid == uid }?.stats
        val cards = variant?.cards ?: weighted(dao.allUsageNow().filter { it.usage.leaderUid == uid })
        Side(card, book.profile(card.number), stats, cards, variant?.let { "${it.name} (${it.deckCount} decks)" })
    }

    /** Camp construit à partir d'un deck perso (exemplaires réels, pas de moyenne). */
    suspend fun sideFromDeck(uid: Int, deck: fr.deckz.data.MyDeck): Side? = withContext(Dispatchers.Default) {
        val card = dao.cardNow(uid) ?: return@withContext null
        val stats = dao.leadersNow().firstOrNull { it.card.uid == uid }?.stats
        val byNumber = dao.cardsByNumbers(deck.cards.keys.toList()).groupBy { it.number }.mapValues { it.value.first() }
        val cards = deck.cards.mapNotNull { (n, q) -> byNumber[n]?.let { WeightedCard(it, q.toDouble(), 1.0) } }
            .sortedByDescending { it.weight }
        Side(card, book.profile(card.number), stats, cards, "Mon deck : ${deck.name}")
    }

    suspend fun cardsByNumbers(numbers: List<String>): Map<String, Card> = withContext(Dispatchers.Default) {
        if (numbers.isEmpty()) emptyMap() else dao.cardsByNumbers(numbers).groupBy { it.number }.mapValues { it.value.first() }
    }

    suspend fun searchCards(q: String): List<Card> = withContext(Dispatchers.Default) { dao.searchCards(q.trim()) }

    suspend fun leaderByNumber(number: String): Card? = withContext(Dispatchers.Default) {
        dao.uidByNumber(number)?.let { dao.cardNow(it) }
    }

    suspend fun variants(uid: Int): List<Variant> = withContext(Dispatchers.Default) {
        val card = dao.cardNow(uid) ?: return@withContext emptyList()
        Variants.of(dao.leaderDeckListsNow(uid), card.number)
    }

    suspend fun plan(me: Side, opp: Side): MatchupPlan = withContext(Dispatchers.Default) {
        MatchupEngine.build(me, opp, book, dao.guidesNow())
    }
}
