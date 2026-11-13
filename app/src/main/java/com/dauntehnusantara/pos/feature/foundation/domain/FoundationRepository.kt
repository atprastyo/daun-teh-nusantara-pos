package com.dauntehnusantara.pos.feature.foundation.domain

import kotlinx.coroutines.flow.Flow

interface FoundationRepository {
    fun observeStorageReady(): Flow<Boolean>
}
