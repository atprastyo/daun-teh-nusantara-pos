package com.dauntehnusantara.pos.core.designsystem

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatIdr(amount: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply {
    maximumFractionDigits = 0
}.format(amount)
fun formatJakarta(utcMs: Long): String = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.forLanguageTag("id-ID"))
    .withZone(ZoneId.of("Asia/Jakarta")).format(Instant.ofEpochMilli(utcMs))
