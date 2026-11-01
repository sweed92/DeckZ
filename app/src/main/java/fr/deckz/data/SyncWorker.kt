package fr.deckz.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.deckz.DeckZApp
import java.util.concurrent.TimeUnit

/** Mise à jour hebdomadaire (Wi-Fi + en charge). */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as DeckZApp).container.repository
        return if (repo.refresh() || repo.hasData) Result.success() else Result.retry()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresCharging(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("weekly-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
