package fr.deckz.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

const val LEADERS_SQL = """SELECT c.*, s.leaderUid AS s_leaderUid, s.rank AS s_rank, s.decks AS s_decks, s.share AS s_share,
           s.tier AS s_tier, s.topCuts AS s_topCuts, s.wins AS s_wins
           FROM cards c LEFT JOIN leader_stats s ON s.leaderUid = c.uid
           WHERE c.type = 'LEADER' AND (c.variantOf IS NULL OR s.leaderUid IS NOT NULL)
           ORDER BY s.decks IS NULL, s.decks DESC, c.number DESC"""

const val USAGE_SQL = """SELECT u.*, c.uid AS c_uid, c.number AS c_number, c.name AS c_name, c.color AS c_color, c.type AS c_type,
           c.cost AS c_cost, c.power AS c_power, c.combo AS c_combo, c.effect AS c_effect, c.traits AS c_traits,
           c.`set` AS c_set, c.rarity AS c_rarity, c.imageUrl AS c_imageUrl, c.backName AS c_backName,
           c.backPower AS c_backPower, c.backEffect AS c_backEffect, c.backTraits AS c_backTraits,
           c.backImageUrl AS c_backImageUrl, c.variantOf AS c_variantOf
           FROM card_usage u JOIN cards c ON c.uid = u.cardUid"""

data class LeaderRow(@Embedded val card: Card, @Embedded(prefix = "s_") val stats: LeaderStats?)
data class UsageRow(@Embedded val usage: CardUsage, @Embedded(prefix = "c_") val card: Card)
data class DeckCardRow(val qty: Int, @Embedded val card: Card)
data class DeckCardOfDeck(val deckId: String, val qty: Int, @Embedded val card: Card)

@Dao
interface DeckZDao {
    // --- lecture ---
    @Query(LEADERS_SQL)
    fun leaders(): Flow<List<LeaderRow>>

    @Query("SELECT * FROM cards WHERE uid = :uid")
    fun card(uid: Int): Flow<Card?>

    @Query("SELECT * FROM leader_stats WHERE leaderUid = :uid")
    fun stats(uid: Int): Flow<LeaderStats?>

    @Query("$USAGE_SQL WHERE u.leaderUid = :uid ORDER BY u.inclusion DESC, u.rank")
    fun usage(uid: Int): Flow<List<UsageRow>>

    @Query("SELECT * FROM decks WHERE leaderUid = :uid ORDER BY date IS NULL, date DESC, placement")
    fun decksOf(uid: Int): Flow<List<Deck>>

    @Query("SELECT * FROM decks WHERE id = :id")
    fun deck(id: String): Flow<Deck?>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun deckNow(id: String): Deck?

    @Query("SELECT dc.qty, c.* FROM deck_cards dc JOIN cards c ON c.uid = dc.cardUid WHERE dc.deckId = :id ORDER BY c.cost, c.number")
    fun deckCards(id: String): Flow<List<DeckCardRow>>

    @Query(
        """SELECT dc.qty, c.* FROM deck_cards dc JOIN cards c ON c.uid = dc.cardUid
           JOIN decks d ON d.id = dc.deckId WHERE d.leaderUid = :uid AND d.hasList = 1"""
    )
    fun leaderDeckCards(uid: Int): Flow<List<DeckCardRow>>

    @Query("SELECT COUNT(*) FROM decks WHERE leaderUid = :uid AND hasList = 1")
    fun leaderListCount(uid: Int): Flow<Int>

    @Query("SELECT uid FROM cards")
    suspend fun cardUids(): List<Int>

    @Query("SELECT * FROM cards WHERE uid IN (:uids)")
    suspend fun cardsNow(uids: List<Int>): List<Card>

    @Query("SELECT uid FROM cards WHERE number = :number ORDER BY variantOf IS NOT NULL LIMIT 1")
    suspend fun uidByNumber(number: String): Int?

    @Query("SELECT * FROM decks WHERE hasList = 0 ORDER BY date IS NULL, date DESC, placement LIMIT :n")
    suspend fun decksWithoutList(n: Int): List<Deck>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun cardCount(): Int

    // --- stratégie / matchups ---
    @Query(LEADERS_SQL) suspend fun leadersNow(): List<LeaderRow>
    @Query("$USAGE_SQL ORDER BY u.leaderUid, u.inclusion DESC, u.rank") suspend fun allUsageNow(): List<UsageRow>
    @Query(
        """SELECT dc.deckId AS deckId, dc.qty AS qty, c.* FROM deck_cards dc JOIN cards c ON c.uid = dc.cardUid
           JOIN decks d ON d.id = dc.deckId WHERE d.leaderUid = :uid AND d.hasList = 1"""
    )
    suspend fun leaderDeckListsNow(uid: Int): List<DeckCardOfDeck>
    @Query("SELECT * FROM cards WHERE uid = :uid") suspend fun cardNow(uid: Int): Card?
    @Query("SELECT url FROM guides") suspend fun guideUrls(): List<String>
    @Query("SELECT * FROM guides") suspend fun guidesNow(): List<Guide>
    @Query("SELECT * FROM guides WHERE leaderNumber IN (:numbers)") fun guidesFor(numbers: List<String>): Flow<List<Guide>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGuides(g: List<Guide>)

    // --- écriture ---
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCards(c: List<Card>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDeckCards(c: List<DeckCard>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertStats(s: List<LeaderStats>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertUsage(u: List<CardUsage>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDecksIgnore(d: List<Deck>)

    @Query("UPDATE decks SET name = :name, leaderUid = :leader, eventId = :eventId, event = :event, date = COALESCE(:date, date), placement = :placement WHERE id = :id")
    suspend fun updateDeckMeta(id: String, name: String, leader: Int, eventId: String?, event: String?, date: String?, placement: Int?)

    @Query("UPDATE decks SET hasList = 1, date = COALESCE(date, :date) WHERE id = :id")
    suspend fun markListed(id: String, date: String?)

    @Query("DELETE FROM leader_stats") suspend fun clearStats()
    @Query("DELETE FROM card_usage WHERE leaderUid = :uid") suspend fun clearUsage(uid: Int)

    @Transaction
    suspend fun replaceStats(s: List<LeaderStats>) { clearStats(); insertStats(s) }

    @Transaction
    suspend fun replaceUsage(uid: Int, u: List<CardUsage>) { clearUsage(uid); insertUsage(u) }

    @Transaction
    suspend fun upsertDecks(d: List<Deck>) {
        insertDecksIgnore(d)
        d.forEach { updateDeckMeta(it.id, it.name, it.leaderUid, it.eventId, it.event, it.date, it.placement) }
    }
}

@Database(
    entities = [Card::class, LeaderStats::class, Deck::class, DeckCard::class, CardUsage::class, Guide::class],
    version = 3, exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): DeckZDao
}

/** v1 -> v2 : guides communautaires (sans toucher au cache existant). */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `guides` (`url` TEXT NOT NULL, `leaderNumber` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`good` TEXT NOT NULL, `bad` TEXT NOT NULL, `summary` TEXT NOT NULL, `gameplan` TEXT NOT NULL, " +
                "`matchups` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`url`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_guides_leaderNumber` ON `guides` (`leaderNumber`)")
    }
}

/** v2 -> v3 : suppression de l'option Claude (table des plans générés). */
val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `ai_plans`")
    }
}
