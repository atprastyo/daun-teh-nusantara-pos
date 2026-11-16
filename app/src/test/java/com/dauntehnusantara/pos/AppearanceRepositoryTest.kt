package com.dauntehnusantara.pos

import android.app.Application
import androidx.datastore.preferences.core.*
import com.dauntehnusantara.pos.feature.settings.data.DataStoreAppearanceRepository
import com.dauntehnusantara.pos.feature.settings.domain.ThemeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class AppearanceRepositoryTest {
    @get:Rule val folder=TemporaryFolder()
    @Test fun defaultLightDarkSurvivesStoreReopenAndUnknownValueFallsBack():Unit=runBlocking {
        val file=folder.root.resolve("appearance.preferences_pb")
        val job=SupervisorJob();val scope=CoroutineScope(Dispatchers.IO+job)
        val store=PreferenceDataStoreFactory.create(scope=scope,produceFile={file})
        try{
            val repo=DataStoreAppearanceRepository(store)
            assertEquals(ThemeMode.LIGHT,repo.theme.first())
            repo.setTheme(ThemeMode.DARK);assertEquals(ThemeMode.DARK,repo.theme.first())
        }finally{job.cancelAndJoin()}
        val next=SupervisorJob();val nextStore=PreferenceDataStoreFactory.create(scope=CoroutineScope(Dispatchers.IO+next),produceFile={file})
        try{
            val repo=DataStoreAppearanceRepository(nextStore)
            assertEquals(ThemeMode.DARK,repo.theme.first())
            nextStore.edit{it[stringPreferencesKey("theme")]="unknown-future-value"}
            assertEquals(ThemeMode.LIGHT,repo.theme.first())
            repo.setTheme(ThemeMode.LIGHT);assertEquals("LIGHT",nextStore.data.first()[stringPreferencesKey("theme")])
        }finally{next.cancelAndJoin()}
    }
}
