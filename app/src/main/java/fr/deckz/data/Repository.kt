package fr.deckz.data

import android.content.Context
import fr.deckz.data.db.AppDb
import fr.deckz.data.db.CardUsage
import fr.deckz.data.db.Deck
import fr.deckz.data.db.DeckCard
import fr.deckz.data.db.LeaderStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

data class SyncState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val step: String = "",
    val lastSuccess: Long = 0L,
    val lastFailed: Boolean = false,
    val error: String? = null,
)

class Repository(context: Context, private val db: AppDb, private val scraper: Scraper) {
    private val dao = db.dao()
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(
        SyncState(lastSuccess = prefs.getLong(K_SUCCESS, 0L), lastFailed = prefs.getBoolean(K_FAILED, false))
    )
    val state = _state.asStateFlow()
    val hasData get() = _state.value.lastSuccess > 0L

    fun leaders() = dao.leaders()
    fun card(uid: Int) = dao.card(uid)
    fun stats(uid: Int) = dao.stats(uid)
    fun leaderVersions(number: String) = dao.leaderVersions(number)
    fun usage(uid: Int) = dao.usage(uid)
    fun decksOf(uid: Int) = dao.decksOf(uid)
    fun deck(id: String) = dao.deck(id)
    fun deckCards(id: String) = dao.deckCards(id)
    fun leaderDeckCards(uid: Int) = dao.leaderDeckCards(uid)

    private fun progress(p: Float, step: String) = _state.value.let { _state.value = it.copy(progress = p, step = step) }

    /** Rafraîchissement complet. Chaque source est indépendante : un échec garde le cache de cette source. */
    suspend fun refresh(): Boolean {
        if (!mutex.tryLock()) return false
        val errors = mutableListOf<String>()
        suspend fun step(name: String, block: suspend () -> Unit) {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Throwable) { errors += "$name : ${e.message}" }
        }
        try {
            _state.value = _state.value.copy(running = true, progress = 0f, error = null)

            step("Cartes") {
                progress(0.02f, "Base de cartes…")
                val known = dao.cardUids().toHashSet()
                val missing = scraper.cardIds().filterNot { it in known }
                missing.chunked(ScrapeConfig.CARD_BATCH).forEachIndexed { i, ids ->
                    progress(0.05f + 0.25f * i / maxOf(1, missing.size / ScrapeConfig.CARD_BATCH + 1), "Cartes ${i * ScrapeConfig.CARD_BATCH}/${missing.size}…")
                    dao.upsertCards(scraper.cards(ids))
                }
            }

            var leaders: MetaLeaders? = null
            step("Leaders") {
                progress(0.32f, "Méta des leaders…")
                leaders = scraper.leaders()
            }

            var metaDecks: List<MetaDeck> = emptyList()
            step("Decks") {
                progress(0.36f, "Résultats des tournois…")
                val events = runCatching { scraper.events() }.getOrDefault(emptyList())
                progress(0.40f, "Decks gagnants…")
                val listed = runCatching { scraper.decks() }.getOrDefault(emptyList())
                if (events.isEmpty() && listed.isEmpty()) error("aucun deck")
                val eventById = events.associateBy { it.id }
                val byId = LinkedHashMap<String, MetaDeck>()
                events.flatMap { it.placements }.forEach { byId[it.id] = it }
                listed.forEach { d ->
                    val prev = byId[d.id]
                    byId[d.id] = d.copy(date = prev?.date ?: eventById[d.eventId]?.date)
                }
                metaDecks = byId.values.toList()
                dao.upsertDecks(metaDecks.map { Deck(it.id, it.name.ifBlank { it.event.orEmpty() }, it.leaderUid, it.eventId, it.event, it.date, it.placement) })
            }

            leaders?.let { ml ->
                val cuts = metaDecks.groupBy { it.leaderUid }
                val total = ml.deckCount.coerceAtLeast(1)
                dao.replaceStats(ml.leaders.map { e ->
                    val share = e.score.toDouble() / total
                    LeaderStats(
                        leaderUid = e.uid, rank = e.rank, decks = e.score, share = share, tier = tierOf(share),
                        topCuts = cuts[e.uid]?.size ?: 0, wins = cuts[e.uid]?.count { it.placement == 1 } ?: 0,
                    )
                })
                // Leaders absents de la base (nouveau set) : on les récupère.
                val known = dao.cardUids().toHashSet()
                ml.leaders.map { it.uid }.filterNot { it in known }.takeIf { it.isNotEmpty() }
                    ?.let { step("Cartes leaders") { dao.upsertCards(scraper.cards(it)) } }

                val active = ml.leaders.filter { it.score > 0 }
                val colors = dao.cardsNow(active.map { it.uid }).associate { it.uid to it.color }
                active.forEachIndexed { i, e ->
                    progress(0.45f + 0.35f * i / active.size, "Cartes clés ${i + 1}/${active.size}…")
                    val color = colors[e.uid]?.split('/')?.firstOrNull() ?: return@forEachIndexed
                    step("Usage ${e.uid}") {
                        val usage = scraper.usage(e.uid, color)
                        val cardKnown = dao.cardUids().toHashSet()
                        usage.map { it.cardUid }.filterNot { it in cardKnown }.takeIf { it.isNotEmpty() }
                            ?.let { dao.upsertCards(scraper.cards(it)) }
                        dao.replaceUsage(e.uid, usage.map {
                            CardUsage(e.uid, it.cardUid, it.rank, (it.score.toDouble() / e.score.coerceAtLeast(1)).coerceAtMost(1.0), it.avgCopies)
                        })
                    }
                }
            }

            // Deck profiles communautaires (matchups) : incrémental, seulement les nouveaux articles.
            step("Guides") {
                progress(0.8f, "Guides de la communauté…")
                val known = dao.guideUrls().toHashSet()
                val todo = scraper.guideUrls().filterNot { it in known }.take(ScrapeConfig.GUIDES_PER_SYNC)
                todo.forEachIndexed { i, url ->
                    progress(0.8f + 0.05f * i / maxOf(1, todo.size), "Guides de la communauté ${i + 1}/${todo.size}…")
                    scraper.guide(url)?.let { dao.upsertGuides(listOf(it)) }
                }
            }

            // Listes de decks : incrémental, uniquement celles qu'on n'a pas encore.
            val todo = dao.decksWithoutList(ScrapeConfig.DECK_LISTS_PER_SYNC)
            todo.forEachIndexed { i, d ->
                progress(0.85f + 0.15f * i / maxOf(1, todo.size), "Listes de decks ${i + 1}/${todo.size}…")
                step("Deck ${d.id}") { saveDeckList(d.id) }
            }

            val ok = errors.isEmpty()
            val now = System.currentTimeMillis()
            val success = if (ok || dao.cardCount() > 0 && leaders != null) now else _state.value.lastSuccess
            prefs.edit().putLong(K_SUCCESS, success).putBoolean(K_FAILED, !ok).apply()
            _state.value = SyncState(lastSuccess = success, lastFailed = !ok, error = errors.firstOrNull(), progress = 1f)
            return ok
        } finally {
            if (_state.value.running) _state.value = _state.value.copy(running = false)
            mutex.unlock()
        }
    }

    private suspend fun saveDeckList(id: String) {
        val list = scraper.deckList(id)
        if (list.embeddedCards.isNotEmpty()) dao.upsertCards(list.embeddedCards)
        val known = dao.cardUids().toHashSet()
        list.cards.map { it.first }.filterNot { it in known }.takeIf { it.isNotEmpty() }?.let { dao.upsertCards(scraper.cards(it)) }
        val rows = list.cards.toMutableList()
        // L'API ne met pas le leader dans deck_cards : on l'ajoute depuis deck_type.
        val leaderUid = dao.deckNow(id)?.leaderUid ?: list.leaderNumber?.let { dao.uidByNumber(it) }
        if (leaderUid != null && rows.none { it.first == leaderUid }) rows.add(0, leaderUid to 1)
        dao.upsertDeckCards(rows.groupBy({ it.first }, { it.second }).map { (uid, q) -> DeckCard(id, uid, q.sum()) })
        dao.markListed(id, list.date?.take(10))
    }

    /** Liste à la demande (écran détail deck). */
    suspend fun ensureDeckList(id: String): Boolean = try {
        if (dao.deckNow(id)?.hasList != true) saveDeckList(id)
        true
    } catch (e: CancellationException) { throw e } catch (_: Throwable) { false }

    suspend fun clearCache() {
        mutex.lock()
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
            prefs.edit().clear().apply()
            _state.value = SyncState()
        } finally { mutex.unlock() }
    }

    private fun tierOf(share: Double) = when {
        share >= ScrapeConfig.TIER_S -> "S"
        share >= ScrapeConfig.TIER_A -> "A"
        share >= ScrapeConfig.TIER_B -> "B"
        else -> "C"
    }

    private companion object {
        const val K_SUCCESS = "last_success"
        const val K_FAILED = "last_failed"
    }
}
