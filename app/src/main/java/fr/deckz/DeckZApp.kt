package fr.deckz

import android.app.Application
import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import fr.deckz.data.Repository
import fr.deckz.data.Scraper
import fr.deckz.data.SyncWorker
import fr.deckz.data.db.AppDb
import fr.deckz.data.db.MIGRATION_1_2
import fr.deckz.data.db.MIGRATION_2_3
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/** Conteneur manuel (pas de DI). */
class AppContainer(context: Context) {
    val db = Room.databaseBuilder(context, AppDb::class.java, "deckz.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).fallbackToDestructiveMigration(true).build()
    val scraper = Scraper()
    val repository = Repository(context, db, scraper)
    val strategy = fr.deckz.strategy.StrategyRepo(context, db)
    val notes = fr.deckz.data.NotesStore(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class DeckZApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncWorker.schedule(this)
    }

    override fun newImageLoader(context: PlatformContext) = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory()) }
        .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images")).maxSizeBytes(300L * 1024 * 1024).build() }
        .crossfade(true)
        .build()
}
