package com.dauntehnusantara.pos

import com.dauntehnusantara.pos.feature.reports.domain.ReportProduct
import com.dauntehnusantara.pos.feature.reports.presentation.*
import org.junit.Assert.*
import org.junit.Test

class ReportChartDataTest {
    @Test fun proportionsHandleZeroAndLongLimitsWithoutChangingAmounts() {
        assertEquals(0f,reportFraction(0,0),0f)
        assertEquals(1f,reportFraction(Long.MAX_VALUE,Long.MAX_VALUE),0f)
        assertEquals(0.5f,reportFraction(Long.MAX_VALUE/2,Long.MAX_VALUE),0.00001f)
        assertEquals("—",paymentPercent(0,0))
        assertEquals("100,0%",paymentPercent(Long.MAX_VALUE,Long.MAX_VALUE))
        assertEquals("33,3%",paymentPercent(1,3))
    }
    @Test fun productsRankWithinReturnedRowsAndKeepRenamedSnapshotsSeparate() {
        val rows=listOf(ReportProduct("same","Old name",3,100),ReportProduct("same","New name",1,200),
            ReportProduct("other","Other",8,50))
        assertEquals(listOf("New name","Old name","Other"),chartProducts(rows,false).map{it.name})
        assertEquals(listOf("Other","Old name","New name"),chartProducts(rows,true).map{it.name})
        assertEquals(5,chartProducts((1..20).map{ReportProduct("$it","$it",it.toLong(),it.toLong())},false).size)
    }
}
