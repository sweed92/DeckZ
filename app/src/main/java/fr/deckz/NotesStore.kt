package fr.deckz.data

import android.content.Context

/**
 * Notes personnelles « Stratégie de joueurs », une par matchup (mon leader | adversaire).
 *
 * Stockées à part de la base Room : la base est un cache qui peut être vidé
 * (clearAllTables, migration destructive), alors que ces notes sont écrites par
 * l'utilisateur et ne doivent jamais être perdues.
 * La clé utilise les numéros de cartes (ex. « FB01-001 »), stables d'une synchro à l'autre.
 */
class NotesStore(context: Context) {
    private val prefs = context.getSharedPreferences("player_notes", Context.MODE_PRIVATE)

    private fun key(meNumber: String, oppNumber: String) = "$meNumber|$oppNumber"

    fun get(meNumber: String, oppNumber: String): String = prefs.getString(key(meNumber, oppNumber), "") ?: ""

    fun set(meNumber: String, oppNumber: String, text: String) {
        val k = key(meNumber, oppNumber)
        prefs.edit().apply { if (text.isBlank()) remove(k) else putString(k, text) }.apply()
    }
}
