package fr.deckz.data

import fr.deckz.strategy.MatchupEngine
import fr.deckz.strategy.ProfileBook
import fr.deckz.strategy.Side
import fr.deckz.strategy.WeightedCard
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ParserTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun parsesFixtures() {
        // Fixtures non versionnées : ./tools/fetch-fixtures.sh
        assumeTrue(javaClass.classLoader!!.getResource("meta_leaders.html") != null)

        val leaders = Parsers.parseLeaders(res("meta_leaders.html"))
        assertTrue(leaders.leaders.isNotEmpty() && leaders.deckCount > 0)

        val decks = Parsers.parseDecks(res("meta_decks.html"))
        assertTrue(decks.isNotEmpty() && decks.all { it.leaderUid > 0 })

        val events = Parsers.parseEvents(res("meta_results.html"))
        assertTrue(events.isNotEmpty() && events.any { it.placements.isNotEmpty() && it.date != null })

        val page = Parsers.parseDeckPage(res("deck_page.html"), "c4d767c5-2d41-4cd3-a80b-691671c5f7e0")
        assertTrue(page.cards.isNotEmpty() && page.embeddedCards.isNotEmpty() && page.leaderNumber != null)

        val api = Parsers.parseDeckApi(res("api_deck.json"))
        assertTrue(api.cards.isNotEmpty())

        val cards = Parsers.parseCards(res("api_cards.json"))
        assertTrue(cards.isNotEmpty() && cards.any { it.isLeader && it.backImageUrl != null && it.awakenCondition != null })

        assertTrue(Parsers.parseUsage(res("api_usage.json")).isNotEmpty())

        val guide = Parsers.parseGuide(res("guide_profile.html"), "https://db-fusionworld.com/en/articles/x")!!
        assertTrue(guide.leaderNumber == "FS11-01" && guide.good.isNotEmpty() && guide.gameplan.isNotEmpty() && guide.matchups.isNotEmpty())

        // Profils embarqués + moteur de matchup : un plan complet entre deux decks des fixtures.
        val book = ProfileBook.parse(java.io.File("src/main/assets/strategy/profiles.json").readText())
        assertTrue(book.leaders.size >= 20)
        val leader = cards.first { it.isLeader }
        val deck = cards.filterNot { it.isLeader }.map { WeightedCard(it, 3.0, 0.9) }
        val plan = MatchupEngine.build(
            Side(leader, book.profile(leader.number), null, deck),
            Side(leader.copy(number = "FS11-01"), book.profile("FS11-01"), null, deck),
            book, listOf(guide),
        )
        assertTrue(plan.early.isNotEmpty() && plan.mid.isNotEmpty() && plan.end.isNotEmpty() && plan.express.isNotEmpty())
    }
}
