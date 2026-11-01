package fr.deckz.strategy

import fr.deckz.data.db.Card

/** Rôles d'une carte, déduits de son texte d'effet (anglais officiel). */
enum class Trait(val label: String) {
    REMOVAL("retrait (KO / défausse / fond du deck)"),
    BOUNCE("renvoi en main"),
    REST("mise en repos"),
    FREEZE("gel (ne se relève pas)"),
    ATTACK_LOCK("empêche d'attaquer"),
    POWER_DOWN("réduction de puissance"),
    BLOCKER("Blocker"),
    DOUBLE_STRIKE("Double Strike"),
    TRIPLE_STRIKE("Triple Strike"),
    CRITICAL("Critical"),
    PROTECTION("impossible à KO / retirer"),
    BARRIER("Barrier (non ciblable)"),
    DEFENSE("défense en combat"),
    DRAW("pioche"),
    LIFE_TO_HAND("vie en main"),
    KI("Ki"),
    EVOLVE("Evolve"),
    BURN("dégâts directs"),
}

object CardTraits {
    private val powerUp = Regex("""\+(\d{4,5}) (?:combo )?power""")
    private val powerDown = Regex("""-(\d{4,5}) power""")
    private val lifeAwaken = Regex("""life is at (\d+) or less""")
    private val kiAwaken = Regex("""(\d+) or more \[ki]""")

    fun of(card: Card): Set<Trait> {
        val t = card.effect.lowercase() + "\n" + card.backEffect.orEmpty().lowercase()
        val out = mutableSetOf<Trait>()
        val sentences = t.split(Regex("""(?<=[.!])\s+|\n"""))
        for (raw in sentences) {
            // Coûts de type "Place this card ... into its owner's Drop:" = sacrifice de SA carte, pas un retrait adverse.
            val s = raw.replace(Regex("""(place|ko) (this card|1 of your|1 battle card in your)[^:]*:"""), "")
            val opp = "opponent" in s
            if (opp && (Regex("""\bko (it|them)\b""").containsMatchIn(s) || "into its owner's drop" in s ||
                    "bottom of its owner's deck" in s || "bottom of their owner's deck" in s && "opponent's battle area" in s)
            ) out += Trait.REMOVAL
            if (opp && "return" in s && "hand" in s) out += Trait.BOUNCE
            if (opp && "rest mode" in s && "switch" in s) out += Trait.REST
            if ("can't switch to active mode" in s) out += Trait.FREEZE
            if (opp && "can't attack" in s) out += Trait.ATTACK_LOCK
            if (powerDown.containsMatchIn(s)) out += Trait.POWER_DOWN
            if ("can't be ko'd" in s || "can't be removed" in s) out += Trait.PROTECTION
            if (opp && Regex("""deal \d""").containsMatchIn(s)) out += Trait.BURN
        }
        if ("[blocker]" in t) out += Trait.BLOCKER
        if ("[double strike]" in t) out += Trait.DOUBLE_STRIKE
        if ("[triple strike]" in t) out += Trait.TRIPLE_STRIKE
        if ("[critical]" in t) out += Trait.CRITICAL
        if ("[barrier]" in t) out += Trait.BARRIER
        if ("[evolve]" in t || "[fusion evolve]" in t) out += Trait.EVOLVE
        if ("[ki]" in t) out += Trait.KI
        if (Regex("""draw \d""").containsMatchIn(t)) out += Trait.DRAW
        if ("from your life to your hand" in t) out += Trait.LIFE_TO_HAND
        if (card.type == "EXTRA" && "[activate battle]" in t && powerUp.containsMatchIn(t)) out += Trait.DEFENSE
        return out
    }

    /** Plus gros bonus de puissance écrit sur la carte (ex. Results of Training -> 15000). */
    fun powerBoost(card: Card): Int? = powerUp.findAll(card.effect.lowercase()).maxOfOrNull { it.groupValues[1].toInt() }

    fun powerDown(card: Card): Int? = powerDown.findAll(card.effect.lowercase()).maxOfOrNull { it.groupValues[1].toInt() }

    /** Seuil de vie de l'éveil d'un leader (ex. 4 pour "life is at 4 or less"). */
    fun awakenLife(leader: Card): Int? = awakenText(leader)?.let { lifeAwaken.find(it)?.groupValues?.get(1)?.toInt() }

    /** Ki requis pour l'éveil, s'il en faut. */
    fun awakenKi(leader: Card): Int? = awakenText(leader)?.let { kiAwaken.find(it)?.groupValues?.get(1)?.toInt() }

    private fun awakenText(leader: Card) =
        leader.effect.lowercase().lineSequence().firstOrNull { "[awaken]" in it }

    fun isFinisher(traits: Set<Trait>) =
        Trait.DOUBLE_STRIKE in traits || Trait.TRIPLE_STRIKE in traits || Trait.CRITICAL in traits
}
