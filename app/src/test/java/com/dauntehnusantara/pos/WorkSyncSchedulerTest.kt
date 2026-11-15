package com.dauntehnusantara.pos

import android.app.Application
import androidx.work.*
import androidx.work.impl.WorkManagerImpl
import androidx.work.impl.schedulers
import androidx.work.impl.close
import com.dauntehnusantara.pos.feature.sync.data.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class WorkSyncSchedulerTest {
    @Test fun existingFifteenMinuteWorkUpdatesInPlaceWithoutDuplicateWorkers() {
        val context=RuntimeEnvironment.getApplication()
        // Real WorkManager persistence, no schedulers: never execute a sync/network worker.
        val manager=WorkManagerImpl(context,Configuration.Builder().build(),schedulersCreator=schedulers())
        val queries=Executors.newSingleThreadExecutor()
        WorkManagerImpl.setDelegate(manager)
        try {
            val old=PeriodicWorkRequestBuilder<SyncWorker>(15,TimeUnit.MINUTES).build()
            manager.enqueueUniquePeriodicWork("push-sales-sweep",ExistingPeriodicWorkPolicy.KEEP,old).result.get(10,TimeUnit.SECONDS)
            val scheduler=WorkSyncScheduler(context)
            scheduler.ensurePeriodic()
            // Queue a query behind the update operation on WorkManager's serial executor.
            manager.getWorkInfosForUniqueWork("push-sales-sweep").get(10,TimeUnit.SECONDS)
            val spec=queries.submit<androidx.work.impl.model.WorkSpec> {
                requireNotNull(manager.workDatabase.workSpecDao().getWorkSpec(old.id.toString()))
            }.get(10,TimeUnit.SECONDS)
            assertEquals(TimeUnit.HOURS.toMillis(1),spec.intervalDuration)
            assertEquals(NetworkType.CONNECTED,spec.constraints.requiredNetworkType)
            assertEquals(BackoffPolicy.EXPONENTIAL,spec.backoffPolicy)
            assertEquals(TimeUnit.SECONDS.toMillis(30),spec.backoffDelayDuration)
            scheduler.ensurePeriodic()
            val entries=manager.getWorkInfosForUniqueWork("push-sales-sweep").get(10,TimeUnit.SECONDS)
            assertEquals(1,entries.size)
            assertEquals(old.id,entries.single().id)
        } finally {
            WorkManagerImpl.setDelegate(null)
            manager.close()
            queries.shutdownNow()
        }
    }
}
