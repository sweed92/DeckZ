package fr.deckz.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Carte (uid = id interne DeckPlanet ; number = numéro officiel ex. FB10-025). Les leaders ont un verso. */
@Entity(tableName = "cards", indices = [Index("number"), Index("type")])
data class Card(
    @PrimaryKey val uid: Int,
    val number: String,
    val name: String,
    val color: String?,
    val type: String,
    val cost: Int?,
    val power: String?,
    val combo: String?,
    val effect: String,
    val traits: String,
    val set: String,
    val rarity: String?,
    val imageUrl: String,
    val backName: String?,
    val backPower: String?,
    val backEffect: String?,
    val backTraits: String?,
    val backImageUrl: String?,
    val variantOf: Int?,
) {
    val isLeader get() = type == "LEADER"

    /** Condition d'éveil d'un leader : partie du texte après le mot-clé [Éveil]. */
    val awakenCondition: String?
        get() = effect.lineSequence().firstOrNull { it.contains("Awaken", ignoreCase = true) }
            ?.substringAfter(")")?.trim()?.ifBlank { null }
}

@Entity(tableName = "leader_stats")
data class LeaderStats(
    @PrimaryKey val leaderUid: Int,
    val rank: Int,
    val decks: Int,
    val share: Double,
    val tier: String,
    val topCuts: Int,
    val wins: Int,
)

@Entity(tableName = "decks", indices = [Index("leaderUid")])
data class Deck(
    @PrimaryKey val id: String,
    val name: String,
    val leaderUid: Int,
    val eventId: String?,
    val event: String?,
    val date: String?,
    val placement: Int?,
    val hasList: Boolean = false,
)

@Entity(tableName = "deck_cards", primaryKeys = ["deckId", "cardUid"], indices = [Index("cardUid")])
data class DeckCard(val deckId: String, val cardUid: Int, val qty: Int)

@Entity(tableName = "card_usage", primaryKeys = ["leaderUid", "cardUid"])
data class CardUsage(
    val leaderUid: Int,
    val cardUid: Int,
    val rank: Int,
    val inclusion: Double,
    val avgCopies: Double,
)

/** Deck profile communautaire (db-fusionworld) : bons/mauvais matchups (numéros de leaders séparés par des virgules) et extraits. */
@Entity(tableName = "guides", indices = [Index("leaderNumber")])
data class Guide(
    @PrimaryKey val url: String,
    val leaderNumber: String,
    val title: String,
    val good: String,
    val bad: String,
    val summary: String,
    val gameplan: String,
    val matchups: String,
    val fetchedAt: Long,
)

