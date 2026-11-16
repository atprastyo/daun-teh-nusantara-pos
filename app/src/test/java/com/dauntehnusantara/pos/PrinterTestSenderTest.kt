package com.dauntehnusantara.pos

import com.dauntehnusantara.pos.feature.printing.data.*
import com.dauntehnusantara.pos.feature.printing.domain.*
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PrinterTestSenderTest {
    private class Connection:PrinterConnection {
        var failConnect=false;var failWrite=false;var block=false
        val entered=CountDownLatch(1);val released=CountDownLatch(1)
        @Volatile var closed=false
        var bytes=byteArrayOf()
        override fun connect(){if(failConnect)throw IOException()}
        override fun write(bytes:ByteArray){
            this.bytes=bytes;entered.countDown()
            if(block){released.await(2,TimeUnit.SECONDS);throw IOException()}
            if(failWrite)throw IOException()
        }
        override fun close(){closed=true;released.countDown()}
    }
    @Test fun textTestContainsNoSaleOrHardwareCommandsAndClosesConnection()=runBlocking {
        val c=Connection();assertEquals(PrinterResult.SENT,sendPrinterTest(c));assertTrue(c.closed)
        val text=c.bytes.toString(Charsets.US_ASCII)
        assertTrue(text.contains("BUKAN STRUK PENJUALAN"));assertTrue(text.lines().all{it.length<=24})
        assertTrue(c.bytes.all{it==10.toByte()||it.toInt() in 32..126})
    }
    @Test fun preWriteFailureIsFailedButPartialWriteIsUnknown()=runBlocking {
        val early=Connection().apply{failConnect=true};assertEquals(PrinterResult.FAILED,sendPrinterTest(early));assertTrue(early.closed)
        val partial=Connection().apply{failWrite=true};assertEquals(PrinterResult.UNKNOWN,sendPrinterTest(partial));assertTrue(partial.closed)
    }
    @Test fun deadlineClosesBlockedOutputAndReturnsUnknown()=runBlocking {
        val c=Connection().apply{block=true}
        assertEquals(PrinterResult.UNKNOWN,sendPrinterTest(c,1_000));assertTrue(c.closed)
    }
    @Test fun cancellationClosesSocketWithoutRetry()=runBlocking {
        val c=Connection().apply{block=true}
        val job=launch(Dispatchers.Default){sendPrinterTest(c)}
        assertTrue(c.entered.await(2,TimeUnit.SECONDS));job.cancelAndJoin();assertTrue(c.closed)
    }
    @Test fun deviceNamesCannotInjectFormattingControls() {
        assertEquals("BadName",PrinterTestSlip.safeName("Bad\u001b\n\u202eName"))
        assertEquals(80,PrinterTestSlip.safeName("a".repeat(100)).length)
    }
}
