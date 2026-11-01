package fr.deckz.data

import fr.deckz.data.db.Card
import fr.deckz.data.db.Guide
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup

data class LeaderEntry(val uid: Int, val score: Int, val rank: Int)
data class MetaLeaders(val leaders: List<LeaderEntry>, val deckCount: Int, val computedAt: String?)
data class MetaDeck(
    val id: String, val name: String, val leaderUid: Int, val placement: Int?,
    val eventId: String?, val event: String?, val date: String?,
)
data class MetaEvent(val id: String, val name: String, val date: String?, val placements: List<MetaDeck>)
data class UsageEntry(val cardUid: Int, val score: Int, val rank: Int, val avgCopies: Double)
data class DeckList(val id: String, val leaderNumber: String?, val date: String?, val cards: List<Pair<Int, Int>>, val embeddedCards: List<Card>)

/** Parseurs purs (sans Android) : HTML/RSC et JSON -> modèles. */
object Parsers {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---------- Flux RSC Next.js ----------

    /** Concatène les chaînes poussées dans `self.__next_f` par les scripts de la page. */
    fun decodeFlight(html: String): String {
        val sb = StringBuilder()
        for (script in Jsoup.parse(html).select("script")) {
            val data = script.data()
            if (!data.contains(ScrapeConfig.RSC_SCRIPT_MARKER)) continue
            var i = data.indexOf(ScrapeConfig.RSC_PUSH_PREFIX)
            while (i >= 0) {
                val start = i + ScrapeConfig.RSC_PUSH_PREFIX.length
                if (data.getOrNull(start) != '"') { i = data.indexOf(ScrapeConfig.RSC_PUSH_PREFIX, start); continue }
                var j = start + 1
                while (j < data.length && data[j] != '"') j += if (data[j] == '\\') 2 else 1
                if (j >= data.length) break
                sb.append(json.parseToJsonElement(data.substring(start, j + 1)).jsonPrimitive.content)
                i = data.indexOf(ScrapeConfig.RSC_PUSH_PREFIX, j)
            }
        }
        return sb.toString()
    }

    /** Toutes les valeurs JSON (objet ou tableau) associées à `"key":` dans le flux. */
    fun findValues(flight: String, key: String): List<JsonElement> {
        val out = mutableListOf<JsonElement>()
        val needle = "\"$key\":"
        var i = flight.indexOf(needle)
        while (i >= 0) {
            val start = i + needle.length
            val c = flight.getOrNull(start)
            if (c == '[' || c == '{') {
                val end = matchingEnd(flight, start)
                if (end > start) runCatching { out += json.parseToJsonElement(flight.substring(start, end + 1)) }
            }
            i = flight.indexOf(needle, start)
        }
        return out
    }

    private fun matchingEnd(s: String, start: Int): Int {
        var depth = 0
        var inStr = false
        var i = start
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                if (c == '\\') i++ else if (c == '"') inStr = false
            } else when (c) {
                '"' -> inStr = true
                '[', '{' -> depth++
                ']', '}' -> if (--depth == 0) return i
            }
            i++
        }
        return -1
    }

    private fun arrayOfObjectsWith(flight: String, key: String, field: String): JsonArray? =
        findValues(flight, key).filterIsInstance<JsonArray>()
            .firstOrNull { a -> a.isNotEmpty() && (a.first() as? JsonObject)?.containsKey(field) == true }

    fun parseLeaders(html: String): MetaLeaders {
        val flight = decodeFlight(html)
        val arr = arrayOfObjectsWith(flight, ScrapeConfig.KEY_LEADERS, ScrapeConfig.KEY_LEADER_ENTRY)
            ?: error("leaders introuvables")
        val leaders = arr.mapNotNull { el ->
            val e = (el as? JsonObject)?.obj(ScrapeConfig.KEY_LEADER_ENTRY) ?: return@mapNotNull null
            LeaderEntry(e.int("id") ?: return@mapNotNull null, e.int("score") ?: 0, e.int("rank") ?: 0)
        }
        val meta = findValues(flight, ScrapeConfig.KEY_META).filterIsInstance<JsonObject>()
            .firstOrNull { it.containsKey("deck_count") }
        return MetaLeaders(leaders, meta?.int("deck_count") ?: leaders.sumOf { it.score }, meta?.str("computed_at"))
    }

    fun parseDecks(html: String): List<MetaDeck> {
        val arr = arrayOfObjectsWith(decodeFlight(html), ScrapeConfig.KEY_DECKS, "deckId") ?: error("decks introuvables")
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            MetaDeck(
                id = o.str("deckId") ?: return@mapNotNull null,
                name = o.str("deckName") ?: "",
                leaderUid = o.int("leaderId") ?: return@mapNotNull null,
                placement = o.int("placement"),
                eventId = o.str("eventId"),
                event = o.str("eventName"),
                date = null,
            )
        }
    }

    fun parseEvents(html: String): List<MetaEvent> {
        val arr = arrayOfObjectsWith(decodeFlight(html), ScrapeConfig.KEY_EVENTS, "placements") ?: error("events introuvables")
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val name = o.str("name") ?: ""
            val date = o.str("date")
            val placements = (o["placements"] as? JsonArray).orEmpty().mapNotNull { p ->
                val po = p as? JsonObject ?: return@mapNotNull null
                MetaDeck(
                    id = po.str("deckId") ?: return@mapNotNull null,
                    name = "",
                    leaderUid = po.int("leaderId") ?: return@mapNotNull null,
                    placement = po.int("placement"),
                    eventId = id, event = name, date = date,
                )
            }
            MetaEvent(id, name, date, placements)
        }
    }

    /** Liste d'un deck : depuis la page deck (RSC) ou depuis l'API Directus (même forme `deck_cards`). */
    fun parseDeckPage(html: String, deckId: String): DeckList {
        val flight = decodeFlight(html)
        val arr = arrayOfObjectsWith(flight, ScrapeConfig.KEY_DECK_CARDS, "amount_in_deck") ?: error("deck_cards introuvables")
        val deckData = findValues(flight, "deckData").filterIsInstance<JsonObject>().firstOrNull()
        return deckListFrom(deckId, arr, deckData?.str("deck_type"), deckData?.str("date_created"))
    }

    fun parseDeckApi(body: String): DeckList {
        val d = json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject
        return deckListFrom(d.str("id")!!, d["deck_cards"]!!.jsonArray, d.str("deck_type"), d.str("date_created"))
    }

    private fun deckListFrom(id: String, arr: JsonArray, leader: String?, date: String?): DeckList {
        val embedded = mutableListOf<Card>()
        val cards = arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val qty = o.int("amount_in_deck") ?: return@mapNotNull null
            val uid = when (val c = o["card_id"]) {
                is JsonObject -> cardFrom(c)?.also { embedded += it }?.uid
                is JsonPrimitive -> c.intOrNull ?: c.contentOrNull?.toIntOrNull()
                else -> null
            } ?: return@mapNotNull null
            uid to qty
        }
        return DeckList(id, leader, date, cards, embedded)
    }

    // ---------- Deck profiles communautaires ----------

    fun parseGuideUrls(sitemap: String): List<String> =
        ScrapeConfig.GUIDE_URL_REGEX.findAll(sitemap).map { it.groupValues[1] }.distinct().toList()

    fun parseGuide(html: String, url: String, now: Long = System.currentTimeMillis()): Guide? {
        val flight = decodeFlight(html)
        val leader = ScrapeConfig.GUIDE_LEADER_REGEX.find(flight)?.groupValues?.get(1) ?: return null
        fun slugs(key: String) = findValues(flight, key).filterIsInstance<JsonArray>()
            .firstOrNull { a -> a.isNotEmpty() && (a.first() as? JsonObject)?.containsKey("slug") == true }
            .orEmpty().mapNotNull { (it as? JsonObject)?.str("slug")?.let(::slugToNumber) }
        val body = longestTextRow(flight)
        val sections = articleSections(body)
        fun section(keys: List<String>) = sections.entries.firstOrNull { (h, _) -> keys.any { h.contains(it) } }?.value
        val full = sections.values.joinToString("\n")
        val matchups = section(ScrapeConfig.GUIDE_MATCHUP_HEADINGS)
            ?: full.indexOf("Matchup", ignoreCase = true).takeIf { it >= 0 }?.let { full.substring(it) }
        return Guide(
            url = url,
            leaderNumber = leader,
            title = Jsoup.parse(html).title().substringBefore(" | ").substringBefore(" - DB").trim(),
            good = slugs(ScrapeConfig.KEY_GUIDE_GOOD).joinToString(","),
            bad = slugs(ScrapeConfig.KEY_GUIDE_BAD).joinToString(","),
            summary = (sections[""] ?: full).lineSequence().firstOrNull { it.length > 40 }.orEmpty().take(500),
            gameplan = section(ScrapeConfig.GUIDE_PLAN_HEADINGS).orEmpty().take(2500),
            matchups = matchups.orEmpty().take(2500),
            fetchedAt = now,
        )
    }

    fun slugToNumber(slug: String): String? =
        ScrapeConfig.GUIDE_SLUG_REGEX.find(slug)?.let { "${it.groupValues[1].uppercase()}-${it.groupValues[2]}" }

    /** Lignes RSC "id:T<longueur en octets>,<texte>" : on garde la plus longue (le corps de l'article). */
    private fun longestTextRow(flight: String): String {
        val bytes = flight.toByteArray(Charsets.UTF_8)
        var best = ""
        for (m in ScrapeConfig.RSC_TEXT_ROW_REGEX.findAll(flight)) {
            val start = flight.substring(0, m.range.last + 1).toByteArray(Charsets.UTF_8).size
            val len = m.groupValues[2].toInt(16).coerceAtMost(bytes.size - start)
            if (len > best.length) best = String(bytes, start, len, Charsets.UTF_8)
        }
        return best
    }

    /** HTML d'article -> sections (titre en minuscules -> texte avec puces). "" = avant le premier titre. */
    private fun articleSections(html: String): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        var current = ""
        val sb = StringBuilder()
        fun flush() { if (sb.isNotBlank()) out[current] = (out[current]?.plus("\n") ?: "") + sb.toString().trim(); sb.clear() }
        for (el in Jsoup.parse(html).body().select("h1, h2, h3, h4, p, li")) {
            when (el.tagName()) {
                "h3", "h4" -> if (current.contains("matchup")) sb.append("▸ ").append(el.text()).append('\n')
                    else { flush(); current = el.text().lowercase() }
                "h1", "h2" -> { flush(); current = el.text().lowercase() }
                // Titres écrits en paragraphe gras (<p><strong>The Game Plan</strong></p>)
                "p" if el.text().length in 3..40 && el.select("strong, b").text() == el.text() && el.parent()?.tagName() != "li" ->
                    if (current.contains("matchup")) sb.append("▸ ").append(el.text()).append('\n')
                    else { flush(); current = el.text().lowercase() }
                "li" -> sb.append("• ").append(el.text()).append('\n')
                else -> if (el.parent()?.tagName() != "li") sb.append(el.text()).append('\n')
            }
        }
        flush()
        return out
    }

    // ---------- API JSON ----------

    fun parseUsage(body: String): List<UsageEntry> =
        json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            UsageEntry(o.int("id") ?: return@mapNotNull null, o.int("score") ?: 0, o.int("rank") ?: 0, o.dbl("avgCopies") ?: 0.0)
        }

    fun parseCardIds(body: String): List<Int> =
        json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray.mapNotNull { (it as? JsonObject)?.int("id") }

    fun parseCards(body: String): List<Card> =
        json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray.mapNotNull { (it as? JsonObject)?.let(::cardFrom) }

    fun cardFrom(o: JsonObject): Card? {
        val uid = o.int("id") ?: return null
        val number = o.str("card_number") ?: return null
        val img = o.str("img_link")?.ifBlank { null } ?: number
        val backName = o.str("card_back_name")?.ifBlank { null }
        return Card(
            uid = uid,
            number = number,
            name = o.str("card_name") ?: number,
            color = o.str("card_color"),
            type = o.str("card_type") ?: "",
            cost = o.int("card_energy_cost"),
            power = o.str("card_power")?.ifBlank { null },
            combo = o.str("card_combo_power")?.ifBlank { null },
            effect = cleanSkill(o.str("card_skill_unstyled")),
            traits = o.strList("card_traits").joinToString(" / "),
            set = o.str("card_series") ?: number.substringBefore('-'),
            rarity = o.str("card_rarity"),
            imageUrl = ScrapeConfig.imageUrl(img),
            backName = backName,
            backPower = o.str("card_back_power")?.ifBlank { null },
            backEffect = cleanSkill(o.str("card_back_skill_unstyled")).ifBlank { null },
            backTraits = o.strList("card_back_traits").joinToString(" / ").ifBlank { null },
            backImageUrl = if (backName != null) ScrapeConfig.backImageUrl(img) else null,
            variantOf = o.int("variant_of"),
        )
    }

    private val tagRegex = Regex("""\[([a-z][a-z0-9 /:'-]*)]""")

    /** "[when attacking] Draw 1.\n[br]\n[auto] ..." -> "[When Attacking] Draw 1.\n[Auto] ..." */
    fun cleanSkill(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace(Regex("""\s*\[br]\s*"""), "\n")
            .replace("[em]", "").replace("[/em]", "")
            .replace(tagRegex) { m -> "[" + m.groupValues[1].split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } + "]" }
            .replace(Regex("""\(\s+"""), "(")
            .lines().joinToString("\n") { it.trim() }.trim()
    }

    // ---------- helpers JSON tolérants (nombres parfois en chaînes, "$undefined" de React) ----------
    private fun JsonObject.prim(k: String) = (this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }
    private fun JsonObject.str(k: String) = prim(k)?.contentOrNull?.takeUnless { it.startsWith("$") }
    private fun JsonObject.int(k: String) = prim(k)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }
    private fun JsonObject.dbl(k: String) = prim(k)?.contentOrNull?.toDoubleOrNull()
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.strList(k: String) = (this[k] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
}
