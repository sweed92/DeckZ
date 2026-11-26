package fr.deckz.strategy

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Profils de leaders rédigés à la main (assets/strategy/profiles.json), indexés par numéro de carte leader. */
@Serializable
data class Threat(val carte: String, val pourquoi: String)

@Serializable
data class GamePlan(val early: List<String> = emptyList(), val mid: List<String> = emptyList(), val end: List<String> = emptyList())

@Serializable
data class LeaderProfile(
    val nom: String,
    val archetype: String,
    val vitesse: Int,
    val pic: Int,
    val resume: String,
    val eveil: String,
    val plan: GamePlan,
    val mulligan: String,
    val forces: List<String> = emptyList(),
    val faiblesses: List<String> = emptyList(),
    val menaces: List<Threat> = emptyList(),
    val conseilsContre: List<String> = emptyList(),
)

@Serializable
data class CuratedMatchup(val avantage: Int, val note: String)

@Serializable
data class ProfileBook(
    val version: String,
    val meta: String,
    val sources: List<String> = emptyList(),
    val leaders: Map<String, LeaderProfile> = emptyMap(),
    val matchups: Map<String, CuratedMatchup> = emptyMap(),
) {
    fun profile(number: String) = leaders[number]
    fun curated(me: String, opp: String) = matchups["$me|$opp"]
    /** Note rédigée du point de vue de l'adversaire (avantage inversé). */
    fun curatedReverse(me: String, opp: String) = if (me == opp) null else matchups["$opp|$me"]

    companion object {
        const val ASSET = "strategy/profiles.json"
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): ProfileBook = json.decodeFromString(serializer(), text)
        fun load(context: Context): ProfileBook =
            runCatching { parse(context.assets.open(ASSET).bufferedReader().use { it.readText() }) }
                .getOrElse { ProfileBook("?", "?") }
    }
}

enum class Archetype(val label: String, val blurb: String) {
    AGGRO("Agressif", "frappe tôt et fort, veut finir vite"),
    TEMPO("Tempo", "gagne du temps sur l'adversaire (repos, renvois) tout en attaquant"),
    MIDRANGE("Polyvalent", "solide à tous les stades, gagne sur la qualité de ses cartes"),
    CONTROL("Contrôle", "survit, retire tes menaces puis gagne en fin de partie"),
    COMBO("Combo", "prépare un tour explosif qui retire beaucoup de vies d'un coup");

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.name == s } ?: MIDRANGE
    }
}
