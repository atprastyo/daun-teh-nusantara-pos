package com.dauntehnusantara.pos.feature.recovery.domain

import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import javax.inject.Inject

interface RecoveryRepository { suspend fun capture(scope:PullScope):String }
class ExportRecovery @Inject constructor(private val repository:RecoveryRepository) { suspend operator fun invoke(scope:PullScope)=repository.capture(scope) }
