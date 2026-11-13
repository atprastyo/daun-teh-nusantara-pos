package com.dauntehnusantara.pos

import android.app.Application
import androidx.work.Configuration
import com.dauntehnusantara.pos.feature.sync.data.SyncWorkerFactory
import com.dauntehnusantara.pos.feature.sync.domain.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PosApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: SyncWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
    override fun onCreate() {
        super.onCreate()
        syncScheduler.ensurePeriodic()
        syncScheduler.enqueue()
    }
}
