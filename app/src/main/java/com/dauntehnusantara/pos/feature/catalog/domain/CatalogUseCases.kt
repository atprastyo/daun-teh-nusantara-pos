package com.dauntehnusantara.pos.feature.catalog.domain

import javax.inject.Inject

class ObserveCatalog @Inject constructor(private val repository: CatalogRepository) {
    operator fun invoke(brandId: String) = repository.observe(brandId)
}
class LoadDemoCatalog @Inject constructor(private val repository: CatalogRepository) {
    suspend operator fun invoke() = repository.seedDemo()
}
