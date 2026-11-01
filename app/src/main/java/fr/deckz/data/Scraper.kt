package fr.deckz.data

import fr.deckz.data.db.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Accès réseau poli (UA explicite, 1 requête/s max) + parsing. Sans dépendance Android : testable en JVM. */
class Scraper(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(ScrapeConfig.TIMEOUT_S, TimeUnit.SECONDS)
        .build(),
) {
    private val gate = Mutex()
    private var lastRequest = 0L

    suspend fun get(url: String): String = gate.withLock {
        val wait = lastRequest + ScrapeConfig.MIN_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            withContext(Dispatchers.IO) {
                val req = Request.Builder().url(url).header("User-Agent", ScrapeConfig.USER_AGENT).build()
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code} sur $url")
                    r.body.string()
                }
            }
        } finally {
            lastRequest = System.currentTimeMillis()
        }
    }

    suspend fun leaders() = Parsers.parseLeaders(get(ScrapeConfig.META_LEADERS_URL))
    suspend fun decks() = Parsers.parseDecks(get(ScrapeConfig.META_DECKS_URL))
    suspend fun events() = Parsers.parseEvents(get(ScrapeConfig.META_RESULTS_URL))
    suspend fun usage(leaderUid: Int, color: String) = Parsers.parseUsage(get(ScrapeConfig.usageUrl(leaderUid, color)))
    suspend fun cardIds() = Parsers.parseCardIds(get(ScrapeConfig.CARD_IDS_URL))
    suspend fun cards(ids: List<Int>): List<Card> = Parsers.parseCards(get(ScrapeConfig.cardsByIdsUrl(ids)))

    suspend fun guideUrls() = Parsers.parseGuideUrls(get(ScrapeConfig.GUIDES_SITEMAP))
    suspend fun guide(url: String) = Parsers.parseGuide(get(url), url)

    /** Liste d'un deck : API JSON d'abord, page HTML en secours. */
    suspend fun deckList(deckId: String): DeckList =
        runCatching { Parsers.parseDeckApi(get(ScrapeConfig.deckApiUrl(deckId))) }
            .getOrElse { Parsers.parseDeckPage(get(ScrapeConfig.deckPageUrl(deckId)), deckId) }
}
