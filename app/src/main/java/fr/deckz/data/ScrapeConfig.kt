package fr.deckz.data

/**
 * Toutes les URL, clés JSON et réglages de scraping. Si DeckPlanet change, c'est ici qu'on répare.
 *
 * DeckPlanet est une app Next.js (App Router) : pas de __NEXT_DATA__, les données sont dans le flux RSC
 * (`self.__next_f.push([1,"..."])`) des pages méta. On y cherche des tableaux JSON par leur clé.
 * Les cartes et listes de decks viennent de l'API publique Directus (api.deckplanet.net).
 */
object ScrapeConfig {
    const val USER_AGENT = "DeckZ/1.0 (app Android perso, un seul utilisateur; 1 req/s max)"
    const val MIN_INTERVAL_MS = 1_000L
    const val TIMEOUT_S = 30L

    const val SITE = "https://www.deckplanet.net"
    const val GAME = "fusion_world"
    const val FORMAT = "standard"
    const val SOURCE = "community"

    // Pages méta (flux RSC)
    const val META_LEADERS_URL = "$SITE/en/$GAME/meta?meta_section=leaders"
    const val META_DECKS_URL = "$SITE/en/$GAME/meta?meta_section=decks"
    const val META_RESULTS_URL = "$SITE/en/$GAME/meta?meta_section=results"
    fun deckPageUrl(deckId: String) = "$SITE/en/$GAME/deck/$deckId"

    // Marqueur des scripts RSC et clés des tableaux à extraire
    const val RSC_SCRIPT_MARKER = "self.__next_f.push"
    const val RSC_PUSH_PREFIX = "self.__next_f.push([1,"   // suivi d'un littéral chaîne JSON
    const val KEY_LEADERS = "leaders"          // [{entry:{id,score,rank}, card:{...}}]
    const val KEY_LEADER_ENTRY = "entry"
    const val KEY_META = "meta"                // {deck_count, computed_at, ...}
    const val KEY_DECKS = "decks"              // [{deckId, deckName, leaderId, placement, eventId, eventName}]
    const val KEY_EVENTS = "events"            // [{id, name, date, deckCount, placements:[{deckId, leaderId, placement}]}]
    const val KEY_DECK_CARDS = "deck_cards"    // [{amount_in_deck, card_id:(int|{card})}]

    // API JSON appelée par le front (usage des cartes par leader)
    const val USAGE_LIMIT = 40
    fun usageUrl(leaderId: Int, color: String) =
        "$SITE/api/meta/$GAME/$FORMAT/$SOURCE/leaders/$leaderId/colors/${enc(color)}/cards?limit=$USAGE_LIMIT"

    // API Directus (base de cartes + listes de decks)
    const val API = "https://api.deckplanet.net/items"
    const val CARD_FIELDS = "id,card_number,card_name,card_color,card_type,card_energy_cost,card_power," +
        "card_combo_power,card_skill_unstyled,card_traits,card_series,card_rarity,img_link," +
        "card_back_name,card_back_power,card_back_skill_unstyled,card_back_traits,variant_of"
    const val CARD_BATCH = 300
    val CARD_IDS_URL = "$API/${GAME}_cards?limit=-1&fields=id&filter%5Bstatus%5D%5B_eq%5D=published"
    fun cardsByIdsUrl(ids: List<Int>) =
        "$API/${GAME}_cards?limit=-1&fields=$CARD_FIELDS&filter%5Bid%5D%5B_in%5D=${ids.joinToString(",")}"
    fun deckApiUrl(deckId: String) =
        "$API/${GAME}_decks/$deckId?fields=id,deck_name,deck_type,date_created,deck_cards.amount_in_deck,deck_cards.card_id"

    /** Nombre max de listes de decks téléchargées par rafraîchissement (les autres à la demande). */
    const val DECK_LISTS_PER_SYNC = 60

    // Images
    const val IMG_BASE = "https://multi-deckplanet.us-southeast-1.linodeobjects.com/$GAME"
    fun imageUrl(imgLink: String) = "$IMG_BASE/$imgLink.webp"
    fun backImageUrl(imgLink: String) = "$IMG_BASE/${imgLink}_b.webp"

    // Deck profiles communautaires (db-fusionworld.com, Next.js aussi) : liste via le sitemap,
    // matchups dans {"good":[{slug}],"bad":[...]}, texte de l'article dans une ligne RSC "id:T<taille hex>,<html>".
    const val GUIDES_SITEMAP = "https://db-fusionworld.com/sitemap.xml"
    val GUIDE_URL_REGEX = Regex("""<loc>(https://db-fusionworld\.com/en/articles/deck-profile[^<]+)</loc>""")
    val GUIDE_LEADER_REGEX = Regex(""""cardNumber":"([A-Z]+\d*-\d+)","name":"[^"]*","cardType":"Leader"""")
    val GUIDE_SLUG_REGEX = Regex("""^([a-z]+\d+)-(\d+)""")   // "fb09-049-son-gohan-sh" -> FB09-049
    val RSC_TEXT_ROW_REGEX = Regex("""([0-9a-f]+):T([0-9a-f]+),""")
    const val KEY_GUIDE_GOOD = "good"
    const val KEY_GUIDE_BAD = "bad"
    val GUIDE_PLAN_HEADINGS = listOf("game plan", "gameplan", "plan de jeu", "the game plan")
    val GUIDE_MATCHUP_HEADINGS = listOf("matchup")
    const val GUIDES_PER_SYNC = 15

    // Seuils de tier (part des decks de la méta)
    const val TIER_S = 0.08
    const val TIER_A = 0.04
    const val TIER_B = 0.015

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
