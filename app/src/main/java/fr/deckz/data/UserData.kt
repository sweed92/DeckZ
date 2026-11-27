package fr.deckz.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/** Une partie jouée : mon leader contre son leader (numéros de cartes), gagnée ou perdue. */
@Serializable
data class GameResult(
    val time: Long,
    val me: String,
    val opp: String,
    val win: Boolean,
    /** Deck perso joué (id de [MyDeck]), si un deck perso était choisi dans le plan de bataille. */
    val deckId: String? = null,
    /** true = j'ai commencé la partie, false = j'étais second, null = pas précisé (anciennes parties). */
    val first: Boolean? = null,
)

/** Deck perso : leader + cartes (numéro -> nombre d'exemplaires, leader exclu). */
@Serializable
data class MyDeck(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val leader: String,
    val cards: Map<String, Int> = emptyMap(),
    val updated: Long = System.currentTimeMillis(),
) {
    val size get() = cards.values.sum()
}

/** Tout ce qui est exporté / importé. */
@Serializable
data class UserBackup(
    val app: String = "DeckZ",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val notes: Map<String, String> = emptyMap(),
    val games: List<GameResult> = emptyList(),
    val decks: List<MyDeck> = emptyList(),
    val favorites: Set<String> = emptySet(),
)

/**
 * Données écrites par l'utilisateur : notes, journal de parties, decks perso, leaders favoris.
 *
 * Gardées à part de la base Room (qui n'est qu'un cache, vidable à tout moment) et indexées par
 * numéro de carte (ex. « FB01-001 »), stable d'une synchro à l'autre.
 */
class UserData(context: Context) {
    // Les notes gardent leur fichier d'origine : celles déjà écrites sont conservées.
    private val notesPrefs = context.getSharedPreferences("player_notes", Context.MODE_PRIVATE)
    private val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _notes = MutableStateFlow(readNotes())
    private val _games = MutableStateFlow(read(K_GAMES, ListSerializer(GameResult.serializer())).orEmpty())
    private val _decks = MutableStateFlow(read(K_DECKS, ListSerializer(MyDeck.serializer())).orEmpty())
    private val _favorites = MutableStateFlow(prefs.getStringSet(K_FAVORITES, emptySet())!!.toSet())

    /** Notes par affrontement, clé « monLeader|sonLeader ». */
    val notes: StateFlow<Map<String, String>> = _notes.asStateFlow()
    val games: StateFlow<List<GameResult>> = _games.asStateFlow()
    val decks: StateFlow<List<MyDeck>> = _decks.asStateFlow()
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    // --- notes ---
    fun note(me: String, opp: String): String = _notes.value[key(me, opp)].orEmpty()

    fun setNote(me: String, opp: String, text: String) {
        val k = key(me, opp)
        if (text == _notes.value[k].orEmpty()) return
        notesPrefs.edit().apply { if (text.isBlank()) remove(k) else putString(k, text) }.apply()
        _notes.value = if (text.isBlank()) _notes.value - k else _notes.value + (k to text)
    }

    // --- journal de parties ---
    fun addGame(me: String, opp: String, win: Boolean, deckId: String?, first: Boolean?) {
        saveGames(_games.value + GameResult(System.currentTimeMillis(), me, opp, win, deckId, first))
    }

    fun removeGame(g: GameResult) = saveGames(_games.value - g)

    private fun saveGames(list: List<GameResult>) {
        _games.value = list
        write(K_GAMES, ListSerializer(GameResult.serializer()), list)
    }

    // --- decks perso ---
    fun deck(id: String): MyDeck? = _decks.value.firstOrNull { it.id == id }

    fun saveDeck(d: MyDeck) {
        val saved = d.copy(updated = System.currentTimeMillis())
        saveDecks(_decks.value.filter { it.id != d.id } + saved)
    }

    fun deleteDeck(id: String) = saveDecks(_decks.value.filter { it.id != id })

    private fun saveDecks(list: List<MyDeck>) {
        val sorted = list.sortedByDescending { it.updated }
        _decks.value = sorted
        write(K_DECKS, ListSerializer(MyDeck.serializer()), sorted)
    }

    // --- favoris ---
    fun toggleFavorite(leaderNumber: String) {
        val now = _favorites.value.let { if (leaderNumber in it) it - leaderNumber else it + leaderNumber }
        _favorites.value = now
        prefs.edit().putStringSet(K_FAVORITES, now).apply()
    }

    // --- sauvegarde ---
    fun exportJson(): String = json.encodeToString(
        UserBackup.serializer(),
        UserBackup(notes = _notes.value, games = _games.value, decks = _decks.value, favorites = _favorites.value),
    )

    /** Lit une sauvegarde (lève une exception si le fichier n'en est pas une) sans rien modifier. */
    fun parseBackup(text: String): UserBackup = json.decodeFromString(UserBackup.serializer(), text).also {
        require(it.app == "DeckZ") { "Ce fichier n'est pas une sauvegarde DeckZ" }
    }

    /** Remplace toutes les données perso par celles de la sauvegarde. */
    fun restore(b: UserBackup) {
        notesPrefs.edit().clear().apply { b.notes.filterValues { it.isNotBlank() }.forEach { (k, v) -> putString(k, v) } }.apply()
        _notes.value = b.notes.filterValues { it.isNotBlank() }
        saveGames(b.games)
        saveDecks(b.decks)
        _favorites.value = b.favorites
        prefs.edit().putStringSet(K_FAVORITES, b.favorites).apply()
    }

    private fun readNotes(): Map<String, String> =
        notesPrefs.all.mapNotNull { (k, v) -> (v as? String)?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()

    private fun <T> read(key: String, s: kotlinx.serialization.KSerializer<T>): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString(s, it) }.getOrNull() }

    private fun <T> write(key: String, s: kotlinx.serialization.KSerializer<T>, value: T) {
        prefs.edit().putString(key, json.encodeToString(s, value)).apply()
    }

    companion object {
        private const val K_GAMES = "games"
        private const val K_DECKS = "decks"
        private const val K_FAVORITES = "favorites"

        fun key(me: String, opp: String) = "$me|$opp"
        fun splitKey(k: String): Pair<String, String>? = k.split('|').takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }
}
