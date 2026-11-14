package com.dauntehnusantara.pos.feature.catalog.photos.domain

import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import java.util.UUID

object PhotoRules {
    const val MAX_BYTES = 204800
    const val EDGE = 512
    const val BUCKET = "product-photos"
    private val uuid = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    private val pattern = Regex("$uuid/$uuid/$uuid\\.jpg")
    fun path(value: String, brand: String, product: String? = null) {
        require(pattern.matches(value) && value.substringBefore('/') == brand)
        if (product != null) require(value.split('/')[1] == product)
    }
    fun newPath(brand: String, product: String) = "$brand/$product/${UUID.randomUUID()}.jpg".also { path(it,brand,product) }
}
data class PhotoCommand(val operationId: String = UUID.randomUUID().toString(), val productId: String,
    val expectedVersion: Long, val path: String?) {
    fun validate(brand: String) {
        require(UUID.fromString(operationId).toString()==operationId && UUID.fromString(productId).toString()==productId)
        require(expectedVersion in 0 until Long.MAX_VALUE); path?.let{PhotoRules.path(it,brand,productId)}
    }
}
data class PhotoAck(val version: Long, val throughSeq: Long)
interface ProductPhotoGateway {
    suspend fun photoScope(): PullScope?
    suspend fun uploadPhoto(scope: PullScope, path: String, bytes: ByteArray)
    suspend fun downloadPhoto(scope: PullScope, path: String): ByteArray
    suspend fun setPhoto(scope: PullScope, epoch: String, command: PhotoCommand): PhotoAck
}
interface ProductPhotoRepository {
    suspend fun load(path: String): ByteArray?
    suspend fun currentScope(): PullScope?
    suspend fun upload(scope: PullScope, path: String, bytes: ByteArray)
}

interface ProductPhotoProcessor { suspend fun prepare(uri:String):ByteArray }
