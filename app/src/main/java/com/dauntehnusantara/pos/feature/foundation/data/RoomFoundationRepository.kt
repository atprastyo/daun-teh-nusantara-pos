package com.dauntehnusantara.pos.feature.foundation.data

import javax.inject.Inject
import com.dauntehnusantara.pos.core.database.MetadataDao
import com.dauntehnusantara.pos.feature.foundation.domain.FoundationRepository
import kotlinx.coroutines.flow.map

class RoomFoundationRepository @Inject constructor(private val dao: MetadataDao) : FoundationRepository {
    override fun observeStorageReady() = dao.observeEntryCount().map { true }
}
