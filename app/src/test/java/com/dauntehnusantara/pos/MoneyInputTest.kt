package com.dauntehnusantara.pos

import androidx.compose.ui.text.AnnotatedString
import com.dauntehnusantara.pos.core.designsystem.components.*
import org.junit.Test
import org.junit.Assert.*

class MoneyInputTest {
    @Test fun formattedPasteNormalizesOnlyWholeRupiah() {
        mapOf("Rp 10.000" to "10000","rp\u00a01.234.567" to "1234567"," 1000 " to "1000","0" to "0","001.000" to "001000","" to "").forEach{(input,raw)->assertEquals(raw,normalizeMoneyInput(input))}
        listOf("1,50","1.5","-1000","Rp -1000","1e3","1.000,50","1..000","1 000","１２３","Rp").forEach{assertEquals(it,normalizeMoneyInput(it));assertNull(moneyInputLongOrNull(it))}
    }
    @Test fun exactLongBoundsAndEmptyAreNotRoundedOrClamped() {
        assertEquals(Long.MAX_VALUE,moneyInputLongOrNull(Long.MAX_VALUE.toString()))
        assertEquals("9.223.372.036.854.775.807",IdrInputTransformation.filter(AnnotatedString(Long.MAX_VALUE.toString())).text.text)
        assertNull(moneyInputLongOrNull("9223372036854775808"));assertNull(moneyInputLongOrNull(""));assertEquals(0L,moneyInputLongOrNull("0"))
        val over="9223372036854775808";assertEquals(over,normalizeMoneyInput(over))
        assertEquals(over,IdrInputTransformation.filter(AnnotatedString(over)).text.text.replace(".",""))
    }
    @Test fun everyCursorOffsetIsBoundedMonotonicAndRawRoundTrips() {
        for(length in 0..80){
            val raw="1234567890".repeat(8).take(length)
            val result=IdrInputTransformation.filter(AnnotatedString(raw));val map=result.offsetMapping
            var prior=-1
            for(i in 0..raw.length){val at=map.originalToTransformed(i);assertTrue(at in 0..result.text.length);assertTrue(at>=prior);assertEquals(i,map.transformedToOriginal(at));prior=at}
            prior=-1
            for(i in 0..result.text.length){val at=map.transformedToOriginal(i);assertTrue(at in 0..raw.length);assertTrue(at>=prior);prior=at}
        }
    }
    @Test fun invalidDraftRemainsVisibleWithoutMisleadingFormatting() {
        listOf("1.5","-4","abc","1,00").forEach{assertEquals(it,IdrInputTransformation.filter(AnnotatedString(it)).text.text)}
    }
}
