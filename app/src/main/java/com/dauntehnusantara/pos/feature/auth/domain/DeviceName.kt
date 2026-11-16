package com.dauntehnusantara.pos.feature.auth.domain

/** Display metadata only; not proof of hardware identity or approval. */
object DeviceName {
    fun clean(value:String?):String?=value?.filterNot{it.isISOControl()||Character.getType(it)==Character.FORMAT.toInt()}?.trim()?.take(160)?.takeIf{it.isNotBlank()}
    fun part(value:String?):String=clean(value)?.take(80).orEmpty()
}
