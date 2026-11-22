package com.dauntehnusantara.pos.feature.stock.domain

import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import java.util.UUID

data class StockQuery(val search:String="",val offset:Int=0,val productId:String?=null,val before:Long?=null)
data class StockProduct(val id:String,val name:String,val active:Boolean,val quantity:Long?,val revision:Long)
data class StockMovement(val seq:Long,val operationId:String,val productId:String,val productName:String,val previous:Long,val quantity:Long,val delta:Long,val revision:Long,val actor:String,val reason:String,val timeMs:Long)
data class StockReview(val operationId:String,val productId:String,val name:String,val quantity:Long,val issue:String,val balance:Long?,val revision:Long)
data class StockSnapshot(val epoch:String,val capturedAtMs:Long,val products:List<StockProduct>,val hasMore:Boolean,val history:List<StockMovement>,val automatic:Boolean=false,val reviews:List<StockReview> = emptyList())
data class StockCommand(val operationId:String,val epoch:String,val productId:String,val quantity:Long,val expectedRevision:Long,val reason:String,val saleOperationId:String?=null,val deduct:Boolean=false) {
    fun validate() {
        listOfNotNull(operationId,epoch,productId,saleOperationId).forEach { require(UUID.fromString(it).toString()==it) }
        require(quantity in 0..StockRules.MAX_COUNT && expectedRevision>=0 && reason.trim().length in 3..240)
    }
}
object StockRules {
    const val MIN_BALANCE=-1_000_000_000_000L
    const val MAX_COUNT=1_000_000_000L
    fun parse(value:String):Long? = value.takeIf { it.matches(Regex("[0-9]{1,10}")) }?.toLongOrNull()?.takeIf { it<=MAX_COUNT }
}
data class StockWriteAck(val quantity:Long,val revision:Long,val generation:String?)
interface StockGateway {
    suspend fun writeStock(scope:PullScope,command:StockCommand):StockWriteAck? { setStock(scope,command);return null }
    suspend fun stock(scope:PullScope,query:StockQuery):StockSnapshot
    suspend fun setStock(scope:PullScope,command:StockCommand)
}
interface StockRepository {
    suspend fun cached(scope:PullScope,query:StockQuery):StockSnapshot?
    suspend fun refresh(scope:PullScope,query:StockQuery):StockSnapshot
    suspend fun pending(scope:PullScope):StockCommand?
    suspend fun prepare(scope:PullScope,command:StockCommand)
    suspend fun submit(scope:PullScope):Unit
}
