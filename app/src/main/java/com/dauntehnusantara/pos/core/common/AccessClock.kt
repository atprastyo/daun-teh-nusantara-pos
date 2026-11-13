package com.dauntehnusantara.pos.core.common

data class ClockReading(val wallMs: Long, val elapsedMs: Long, val bootCount: Int, val deviceSecure: Boolean)
interface AccessClock { fun read(): ClockReading }
