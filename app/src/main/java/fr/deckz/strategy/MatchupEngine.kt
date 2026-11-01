package fr.deckz.strategy

import fr.deckz.data.db.Card
import fr.deckz.data.db.Guide
import fr.deckz.data.db.LeaderStats
import kotlin.math.roundToInt

/** Un camp du matchup : leader, profil rédigé (s'il existe), stats méta et cartes jouées (pondérées). */
data class Side(
    val leader: Card,
    val profile: LeaderProfile?,
    val stats: LeaderStats?,
    val cards: List<WeightedCard>,
    val variantName: String? = null,
)

enum class Verdict(val label: String) { FAVORABLE("Favorable"), EQUILIBRE("Équilibré"), DIFFICILE("Difficile") }

data class WatchCard(val card: Card, val reason: String)
data class CommunityNote(val title: String, val url: String, val text: String, val stance: String?)

data class MatchupPlan(
    val verdict: Verdict,
    val sourced: Boolean,
    val reasons: List<String>,
    val role: String,
    val express: List<String>,
    val early: List<String>,
    val mid: List<String>,
    val end: List<String>,
    val pitfalls: List<String>,
    val watch: List<WatchCard>,
    val mulligan: List<String>,
    val myArch: Archetype,
    val oppArch: Archetype,
    val mySpike: Int,
    val oppSpike: Int,
    val lifeMargin: Int,
    val curatedNote: String?,
    val community: List<CommunityNote>,
    val mirror: Boolean,
)

/** Analyse d'un deck à partir des textes de ses cartes clés. */
class DeckAnalysis(val side: Side) {
    val traits: Map<Card, Set<Trait>> = side.cards.filter { it.weight >= 0.5 }.associate { it.card to CardTraits.of(it.card) }
    private val weightByUid = side.cards.associate { it.card.uid to it.weight }

    fun weight(t: Trait) = traits.filterValues { t in it }.keys.sumOf { weightByUid[it.uid] ?: 0.0 }
    fun cardsWith(vararg t: Trait) = traits.filterValues { s -> t.any { it in s } }.keys
        .sortedByDescending { weightByUid[it.uid] ?: 0.0 }

    val avgCost: Double = side.cards.filter { !it.card.isLeader }.let { cs ->
        val w = cs.sumOf { it.weight }
        if (w == 0.0) 3.0 else cs.sumOf { (it.card.cost ?: 0) * it.weight } / w
    }
    val finishers = cardsWith(Trait.DOUBLE_STRIKE, Trait.TRIPLE_STRIKE, Trait.CRITICAL)
    val removal = cardsWith(Trait.REMOVAL, Trait.BOUNCE)
    val defenses = cardsWith(Trait.DEFENSE)
    val blockers = cardsWith(Trait.BLOCKER)
    val protectors = cardsWith(Trait.PROTECTION, Trait.BARRIER)
    val lockers = cardsWith(Trait.FREEZE, Trait.ATTACK_LOCK)
    val resters = cardsWith(Trait.REST)
    val awakenLife = CardTraits.awakenLife(side.leader)
    val awakenKi = CardTraits.awakenKi(side.leader)

    val archetype: Archetype = side.profile?.let { Archetype.of(it.archetype) } ?: when {
        weight(Trait.REMOVAL) + weight(Trait.BOUNCE) >= 8 && avgCost >= 2.8 -> Archetype.CONTROL
        weight(Trait.KI) >= 10 && finishers.isNotEmpty() -> Archetype.COMBO
        avgCost < 2.4 -> Archetype.AGGRO
        weight(Trait.REST) + weight(Trait.BOUNCE) >= 8 -> Archetype.TEMPO
        else -> Archetype.MIDRANGE
    }
    val speed: Int = side.profile?.vitesse ?: when (archetype) {
        Archetype.AGGRO -> 4; Archetype.CONTROL -> 2; else -> 3
    }
    val spike: Int = side.profile?.pic ?: when (archetype) {
        Archetype.AGGRO, Archetype.TEMPO -> 4; else -> 5
    }
}

object MatchupEngine {

    fun verdictOnly(me: Side, opp: Side, book: ProfileBook, guides: List<Guide>): Verdict =
        score(DeckAnalysis(me), DeckAnalysis(opp), book, guides).first

    fun build(me: Side, opp: Side, book: ProfileBook, guides: List<Guide>): MatchupPlan {
        val a = DeckAnalysis(me)
        val b = DeckAnalysis(opp)
        val mirror = me.leader.number == opp.leader.number
        val (verdict, sourced, reasons) = score(a, b, book, guides)
        val margin = when {
            b.cardsWith(Trait.TRIPLE_STRIKE).isNotEmpty() -> 3
            b.finishers.isNotEmpty() -> 2
            else -> 1
        }
        val role = role(a, b, mirror)
        val curated = book.curated(me.leader.number, opp.leader.number)?.note
            ?: book.curatedReverse(me.leader.number, opp.leader.number)?.note?.let { "(Point de vue adverse) $it" }

        return MatchupPlan(
            verdict = verdict,
            sourced = sourced,
            reasons = reasons,
            role = role,
            express = express(a, b, role, margin),
            early = early(a, b),
            mid = mid(a, b),
            end = end(a, b, margin),
            pitfalls = pitfalls(a, b),
            watch = watch(b),
            mulligan = mulligan(a, b),
            myArch = a.archetype,
            oppArch = b.archetype,
            mySpike = a.spike,
            oppSpike = b.spike,
            lifeMargin = margin,
            curatedNote = curated,
            community = community(me.leader.number, opp.leader, guides),
            mirror = mirror,
        )
    }

    // ---------- verdict ----------

    private fun score(a: DeckAnalysis, b: DeckAnalysis, book: ProfileBook, guides: List<Guide>): Triple<Verdict, Boolean, List<String>> {
        val me = a.side.leader.number
        val opp = b.side.leader.number
        val reasons = mutableListOf<String>()
        var s = 0.0
        var sourced = false

        book.curated(me, opp)?.let { s += it.avantage * 1.5; sourced = true; reasons += "Analyse DeckZ de ce matchup (méta ${book.meta})." }
            ?: book.curatedReverse(me, opp)?.let { s -= it.avantage * 1.5; sourced = true; reasons += "Analyse DeckZ du matchup inverse." }

        for (g in guides) {
            val good = g.good.split(',').filter { it.isNotBlank() }
            val bad = g.bad.split(',').filter { it.isNotBlank() }
            if (g.leaderNumber == me && opp in good) { s += 1; sourced = true; reasons += "La communauté le classe en bon matchup pour toi." }
            if (g.leaderNumber == me && opp in bad) { s -= 1; sourced = true; reasons += "La communauté le classe en mauvais matchup pour toi." }
            if (g.leaderNumber == opp && me in good && me != opp) { s -= 1; sourced = true; reasons += "Les joueurs de ce leader te considèrent comme une cible facile." }
            if (g.leaderNumber == opp && me in bad && me != opp) { s += 1; sourced = true; reasons += "Les joueurs de ce leader te considèrent comme un matchup difficile pour eux." }
        }

        // Petites tendances déduites des cartes (poids faible, pour départager).
        if (a.removal.size >= 3 && b.weight(Trait.EVOLVE) >= 6) { s += 0.4; reasons += "Tu as du retrait contre un deck qui mise sur ses cartes Evolve." }
        if (b.removal.size >= 3 && a.weight(Trait.EVOLVE) >= 6) { s -= 0.4; reasons += "Il a beaucoup de retrait contre tes cartes Evolve." }
        if (a.speed - b.speed >= 2 && b.archetype in setOf(Archetype.COMBO, Archetype.CONTROL)) { s += 0.4; reasons += "Tu es bien plus rapide qu'un deck qui a besoin de temps." }
        if (b.speed - a.speed >= 2 && a.archetype in setOf(Archetype.COMBO, Archetype.CONTROL)) { s -= 0.4; reasons += "Il est bien plus rapide alors que tu as besoin de temps." }
        if (b.finishers.isNotEmpty() && a.removal.isEmpty()) { s -= 0.3; reasons += "Tu as peu de moyens de retirer ses finishers." }
        val ta = tierValue(a.side.stats?.tier)
        val tb = tierValue(b.side.stats?.tier)
        if (ta - tb >= 2) { s += 0.4; reasons += "Ton deck est nettement mieux classé dans la méta." }
        if (tb - ta >= 2) { s -= 0.4; reasons += "Son deck est nettement mieux classé dans la méta." }

        if (a.side.leader.number == b.side.leader.number) {
            s = 0.0
            reasons.clear()
            reasons += "Miroir : même deck des deux côtés, le premier joueur et la gestion des ressources font la différence."
        }
        val verdict = when {
            s >= 0.9 -> Verdict.FAVORABLE
            s <= -0.9 -> Verdict.DIFFICILE
            else -> Verdict.EQUILIBRE
        }
        if (reasons.isEmpty()) reasons += "Pas de données directes sur ce matchup : estimation à partir des cartes jouées."
        return Triple(verdict, sourced, reasons.distinct())
    }

    private fun tierValue(t: String?) = when (t) { "S" -> 4; "A" -> 3; "B" -> 2; "C" -> 1; else -> 0 }

    // ---------- textes ----------

    private fun Card.short() = "$name ($number)"
    private fun List<Card>.names(n: Int = 2) = take(n).joinToString(", ") { it.short() }

    private fun role(a: DeckAnalysis, b: DeckAnalysis, mirror: Boolean): String = when {
        mirror -> "Miroir : celui qui joue en premier part avec l'avantage. Si tu joues en second, défends tôt et vise un tour fort plus tardif."
        a.speed > b.speed || a.spike < b.spike ->
            "Tu es l'agresseur : ton deck (${a.archetype.label}) est plus rapide que le sien (${b.archetype.label}). Mets la pression avant son tour fort (tour ${b.spike})."
        a.speed < b.speed || a.spike > b.spike ->
            "Tu es le défenseur : son deck (${b.archetype.label}) va plus vite que le tien (${a.archetype.label}). Survis jusqu'à ton tour fort (tour ${a.spike}) en gardant tes vies et tes combos."
        else -> "Rythmes similaires (${a.archetype.label} contre ${b.archetype.label}) : celui qui gère le mieux ses ressources (cartes en main, vies) gagne. Le premier joueur a l'initiative."
    }

    private fun express(a: DeckAnalysis, b: DeckAnalysis, role: String, margin: Int): List<String> {
        val out = mutableListOf(role.substringBefore(". ").trimEnd('.') + ".")
        val myWin = a.side.profile?.plan?.end?.firstOrNull() ?: a.finishers.firstOrNull()?.let { "Gagne avec ${it.short()}." }
        myWin?.let { out += "Ton plan pour gagner : $it" }
        val danger = b.side.profile?.menaces?.firstOrNull()?.let { t -> "${cardLabel(b, t.carte)} — ${t.pourquoi}" }
            ?: b.finishers.firstOrNull()?.let { "${it.short()} (${finisherWord(b, it)})" }
            ?: b.removal.firstOrNull()?.let { "${it.short()} (retrait)" }
        danger?.let { out += "Son danger n°1 : $it" }
        out += if (margin > 1) "Garde au moins $margin vies de marge à partir du tour ${b.spike} : il peut en retirer plusieurs d'un coup."
        else "Garde de l'énergie et des combos pour son tour fort (tour ${b.spike})."
        return out
    }

    private fun early(a: DeckAnalysis, b: DeckAnalysis): List<String> {
        val out = mutableListOf<String>()
        out += a.side.profile?.plan?.early?.take(3) ?: genericEarly(a)
        when {
            b.speed - a.speed >= 1 -> out += "Il est plus rapide que toi : ne prends pas de dégâts inutiles et garde des cartes à 10000 de combo pour défendre ses premières attaques."
            a.speed - b.speed >= 1 -> out += "Tu es plus rapide : attaque dès le tour 2 pour l'obliger à défendre avec les cartes de sa main au lieu de préparer son jeu."
        }
        b.awakenLife?.let { l ->
            out += if (l >= 5) "Il s'éveille dès $l vies (ou moins) : chaque dégât que tu lui infliges tôt le rapproche de sa face éveillée. N'attaque son leader que si ça te rapporte quelque chose ou si tu peux enchaîner."
            else if (l <= 3) "Il ne s'éveille qu'à $l vies : tu peux frapper son leader librement en début de partie."
            else "Il s'éveille à $l vies : le faire descendre de 8 à 5 est sans risque, mais réfléchis avant de passer à $l."
        }
        b.awakenKi?.let { k ->
            val kiCards = b.cardsWith(Trait.KI).filter { !it.isLeader }
            out += "Son éveil demande $k Ki : retire en priorité les cartes qui portent du Ki" + (if (kiCards.isNotEmpty()) " (${kiCards.names()})." else ".")
        }
        a.awakenLife?.let { l ->
            if (a.awakenKi == null) out += "Tu t'éveilles à $l vies : laisse passer quelques petites attaques pour y arriver, mais arrive au tour ${b.spike} avec assez de vies."
        }
        return out
    }

    private fun mid(a: DeckAnalysis, b: DeckAnalysis): List<String> {
        val out = mutableListOf<String>()
        out += a.side.profile?.plan?.mid?.take(3) ?: genericMid(a)
        out += "Son tour fort est le tour ${b.spike} : le tour d'avant, garde de l'énergie et des combos pour défendre."
        val threats = threatCards(b).take(2)
        if (threats.isNotEmpty()) {
            if (a.removal.isNotEmpty())
                out += "Garde tes réponses (${a.removal.names()}) pour ses menaces : ${threats.joinToString(", ") { it.card.short() }}."
            else
                out += "Tu as peu de retrait : contre ${threats.first().card.short()}, réponds en l'attaquant, en le mettant en repos ou en défendant avec tes combos."
        }
        if (b.protectors.isNotEmpty())
            out += "${b.protectors.names()} ne peu${if (b.protectors.size > 1) "vent" else "t"} pas être KO ou ciblé${if (b.protectors.size > 1) "s" else ""} par tes effets : prévois de les battre en combat ou de les ignorer."
        if (a.resters.isNotEmpty() && b.weight(Trait.EVOLVE) >= 4)
            out += "Utilise ${a.resters.names(1)} pour mettre ses cartes en repos : ses Evolve et fusions demandent souvent des cartes en mode actif."
        return out
    }

    private fun end(a: DeckAnalysis, b: DeckAnalysis, margin: Int): List<String> {
        val out = mutableListOf<String>()
        out += a.side.profile?.plan?.end?.take(2) ?: genericEnd(a)
        if (b.finishers.isNotEmpty())
            out += "Garde au moins $margin vies : ${b.finishers.names()} peu${if (b.finishers.size > 1) "vent" else "t"} retirer plusieurs vies d'un coup."
        if (b.defenses.isNotEmpty()) {
            val boost = b.defenses.mapNotNull { CardTraits.powerBoost(it) }.maxOrNull()
            out += "Avant ton attaque finale, compte ses défenses (${b.defenses.names()})" +
                (boost?.let { " : jusqu'à +$it en combat. Attaque d'abord avec des cartes secondaires pour les faire sortir." } ?: ".")
        }
        if (a.finishers.isNotEmpty() && a.side.profile == null)
            out += "Termine avec ${a.finishers.names()} quand il n'a plus beaucoup de cartes en main."
        return out
    }

    private fun pitfalls(a: DeckAnalysis, b: DeckAnalysis): List<String> {
        val out = mutableListOf<String>()
        b.side.profile?.conseilsContre?.let { out += it }
        b.lockers.firstOrNull()?.let { out += "${it.short()} peut geler tes cartes ou les empêcher d'attaquer : n'engage pas tout ton terrain sur un seul tour." }
        b.blockers.firstOrNull()?.let { out += "${it.short()} a Blocker : il peut détourner une attaque vers lui. Attaque-le d'abord ou prévois une attaque de plus." }
        if (b.weight(Trait.LIFE_TO_HAND) >= 6)
            out += "Il prend souvent ses propres vies en main : il paraît bas en vie, mais il a plus de cartes pour défendre."
        if (b.removal.size >= 3 && a.avgCost >= 3.2)
            out += "Il a beaucoup de retrait (${b.removal.names()}) : évite de tout miser sur une seule grosse carte."
        if (a.side.leader.number == b.side.leader.number)
            out += "En miroir, retiens quelles cartes clés l'adversaire a déjà jouées : vous avez les mêmes réponses."
        return out.distinct()
    }

    private fun threatCards(b: DeckAnalysis): List<WatchCard> {
        val out = LinkedHashMap<Int, WatchCard>()
        b.side.profile?.menaces?.forEach { t ->
            b.side.cards.firstOrNull { it.card.number == t.carte }?.card?.let { out[it.uid] = WatchCard(it, t.pourquoi) }
        }
        for (c in b.finishers) if (out.size < 6) out.putIfAbsent(c.uid, WatchCard(c, "${finisherWord(b, c).replaceFirstChar { it.uppercase() }} : plusieurs vies d'un coup."))
        for (c in b.removal) if (out.size < 6) out.putIfAbsent(c.uid, WatchCard(c, "Retire une de tes cartes du terrain."))
        for (c in b.protectors) if (out.size < 6) out.putIfAbsent(c.uid, WatchCard(c, "Résiste à tes effets (impossible à KO ou à cibler)."))
        for (c in b.defenses) if (out.size < 6) out.putIfAbsent(c.uid, WatchCard(c, "Défense : " + (CardTraits.powerBoost(c)?.let { "+$it en combat." } ?: "bonus en combat.")))
        return out.values.toList()
    }

    private fun watch(b: DeckAnalysis) = threatCards(b)

    private fun mulligan(a: DeckAnalysis, b: DeckAnalysis): List<String> {
        val out = mutableListOf<String>()
        out += a.side.profile?.mulligan ?: "Garde au moins une carte de recherche ou de pioche coût 1 et une carte à jouer au tour 2. Tu n'as droit qu'à un seul mulligan."
        when {
            b.speed - a.speed >= 1 -> out += "Contre ce deck rapide, garde aussi des cartes à 10000 de combo pour tes premières défenses."
            a.speed - b.speed >= 1 -> out += "Contre ce deck plus lent, privilégie tes cartes de pression des tours 2-3."
        }
        return out
    }

    private fun community(me: String, opp: Card, guides: List<Guide>): List<CommunityNote> =
        guides.filter { it.leaderNumber == me || it.leaderNumber == opp.number }.map { g ->
            val stance = when {
                g.leaderNumber == me && opp.number in g.good.split(',') -> "good"
                g.leaderNumber == me && opp.number in g.bad.split(',') -> "bad"
                g.leaderNumber == opp.number && me in g.good.split(',') -> "bad"
                g.leaderNumber == opp.number && me in g.bad.split(',') -> "good"
                else -> null
            }
            val text = relevantLines(g.matchups, opp.name).ifBlank { g.matchups.take(600) }
            CommunityNote(g.title.ifBlank { "Deck profile" }, g.url, text, stance)
        }.filter { it.text.isNotBlank() || it.stance != null }

    /** Lignes du texte de matchups qui parlent de l'adversaire (et les puces qui suivent). */
    private fun relevantLines(text: String, oppName: String): String {
        val key = oppName.substringBefore(':').substringBefore('/').trim().lowercase()
        if (key.length < 3) return ""
        val lines = text.lines()
        val out = mutableListOf<String>()
        var take = false
        for (l in lines) {
            val isHeader = !l.startsWith("•")
            if (isHeader) take = key in l.lowercase()
            if (take || key in l.lowercase()) out += l
        }
        return out.joinToString("\n").take(900)
    }

    private fun finisherWord(b: DeckAnalysis, c: Card): String {
        val t = b.traits[c].orEmpty()
        return when {
            Trait.TRIPLE_STRIKE in t -> "Triple Strike"
            Trait.DOUBLE_STRIKE in t -> "Double Strike"
            Trait.CRITICAL in t -> "Critical"
            else -> "finisher"
        }
    }

    private fun cardLabel(b: DeckAnalysis, number: String) =
        b.side.cards.firstOrNull { it.card.number == number }?.card?.short() ?: number

    private fun genericEarly(a: DeckAnalysis): List<String> {
        val searchers = a.side.cards.filter { (it.card.cost ?: 9) <= 1 && Trait.DRAW in CardTraits.of(it.card) }.map { it.card }
        return listOfNotNull(
            searchers.takeIf { it.isNotEmpty() }?.let { "Tours 1-2 : joue tes cartes de pioche/recherche (${it.names()}) pour construire ta main." }
                ?: "Tours 1-2 : joue tes cartes coût 1-2 les plus jouées pour développer ton terrain.",
            "Garde tes cartes à 10000 de combo pour défendre : elles valent plus en défense qu'en jeu au début.",
        )
    }

    private fun genericMid(a: DeckAnalysis): List<String> {
        val core = a.side.cards.filter { (it.card.cost ?: 0) in 3..5 && !it.card.isLeader && it.card.type != "EXTRA" }
            .sortedByDescending { it.weight }.map { it.card }
        return listOfNotNull(
            core.takeIf { it.isNotEmpty() }?.let { "Tours 3-4 : pose tes cartes clés (${it.names()})." },
            a.removal.takeIf { it.isNotEmpty() }?.let { "Utilise ${it.names()} pour retirer ses cartes les plus menaçantes." },
        )
    }

    private fun genericEnd(a: DeckAnalysis): List<String> = listOf(
        a.finishers.takeIf { it.isNotEmpty() }?.let { "Fin de partie : ${it.names()} pour retirer plusieurs vies d'un coup." }
            ?: "Fin de partie : attaque avec tes plus grosses cartes et force-le à vider sa main.",
    )

    /** Pourcentage arrondi pour l'affichage. */
    fun pct(x: Double) = (x * 100).roundToInt()
}
