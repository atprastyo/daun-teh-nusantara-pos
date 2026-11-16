package com.dauntehnusantara.pos.feature.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.dauntehnusantara.pos.feature.settings.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

private val Context.appearanceStore by preferencesDataStore(name="appearance")
@Singleton
class DataStoreAppearanceRepository(private val store:DataStore<Preferences>):AppearanceRepository {
    @Inject constructor(@ApplicationContext context:Context):this(context.appearanceStore)
    private val key=stringPreferencesKey("theme")
    override val theme=store.data.map{preferences->ThemeMode.entries.firstOrNull{it.name==preferences[key]}?:ThemeMode.LIGHT}
    override suspend fun setTheme(mode:ThemeMode){store.edit{it[key]=mode.name}}
}
