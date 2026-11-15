package com.dauntehnusantara.pos.feature.recovery.domain

import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import javax.inject.Inject

data class RecoverySnapshot(val raw:String,val capturedAtMs:Long)
interface RecoveryRepository { suspend fun capture(scope:PullScope):String; suspend fun snapshot(scope:PullScope):RecoverySnapshot }
class ExportRecovery @Inject constructor(private val repository:RecoveryRepository) { suspend operator fun invoke(scope:PullScope)=repository.capture(scope)
    suspend fun snapshot(scope:PullScope)=repository.snapshot(scope)
}
