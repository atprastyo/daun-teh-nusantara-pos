package com.dauntehnusantara.pos.feature.sync.data

import android.content.Context
import androidx.work.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

class SyncWorker(context: Context, parameters: WorkerParameters, private val repository: SyncRepository, private val pull: PullRepository) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val pushing = repository.drain()
        val pulling = pull.sync()
        if (pushing || pulling) Result.retry() else Result.success()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { Result.retry() } // Never log receipt bodies or credentials.
}
class SyncWorkerFactory @Inject constructor(private val repository: dagger.Lazy<SyncRepository>, private val pull: dagger.Lazy<PullRepository>) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) SyncWorker(appContext, workerParameters, repository.get(), pull.get()) else null
}
@Singleton
class WorkSyncScheduler @Inject constructor(@param:ApplicationContext private val context: Context) : SyncScheduler {
    private val work get() = WorkManager.getInstance(context)
    private val constraints get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    override fun enqueue(force: Boolean) {
        work.enqueueUniqueWork("push-sales", if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    override fun ensurePeriodic() {
        work.enqueueUniquePeriodicWork("push-sales-sweep", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
}
