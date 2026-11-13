package com.dauntehnusantara.pos.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.Binds
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import java.time.Clock
import com.dauntehnusantara.pos.BuildConfig
import com.dauntehnusantara.pos.core.common.NetworkAvailability
import com.dauntehnusantara.pos.core.common.AccessClock
import com.dauntehnusantara.pos.core.network.*
import com.dauntehnusantara.pos.core.security.*
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.auth.data.AuthRepositoryImpl
import com.dauntehnusantara.pos.feature.checkout.domain.CheckoutRepository
import com.dauntehnusantara.pos.feature.checkout.data.RoomCheckoutRepository
import com.dauntehnusantara.pos.core.database.CatalogConstraints
import com.dauntehnusantara.pos.feature.catalog.domain.CatalogRepository
import com.dauntehnusantara.pos.feature.catalog.data.RoomCatalogRepository
import com.dauntehnusantara.pos.core.database.PosDatabase
import com.dauntehnusantara.pos.feature.foundation.data.RoomFoundationRepository
import com.dauntehnusantara.pos.feature.foundation.domain.FoundationRepository

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): PosDatabase =
        Room.databaseBuilder(context, PosDatabase::class.java, "daun-teh.db").addCallback(CatalogConstraints).build()

    @Provides @Singleton
    fun backendConfig() = BackendConfig(BuildConfig.SUPABASE_URL.trimEnd('/'), BuildConfig.SUPABASE_PUBLIC_KEY)

    @Provides @Singleton
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    fun catalogDao(database: PosDatabase) = database.catalogDao()

    @Provides
    fun metadataDao(database: PosDatabase) = database.metadataDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun auth(implementation: AuthRepositoryImpl): AuthRepository
    @Binds @Singleton
    abstract fun connectivity(implementation: AndroidNetworkAvailability): NetworkAvailability
    @Binds @Singleton
    abstract fun transport(implementation: JsonHttpClient): AuthTransport
    @Binds @Singleton
    abstract fun secrets(implementation: KeystoreSecretStore): SecretStore
    @Binds @Singleton
    abstract fun accessClock(implementation: AndroidAccessClock): AccessClock

    @Binds @Singleton
    abstract fun checkout(implementation: RoomCheckoutRepository): CheckoutRepository

    @Binds @Singleton
    abstract fun catalog(implementation: RoomCatalogRepository): CatalogRepository

    @Binds @Singleton
    abstract fun foundation(implementation: RoomFoundationRepository): FoundationRepository
}
