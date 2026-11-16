package com.dauntehnusantara.pos.feature.printing.data

import com.dauntehnusantara.pos.feature.printing.domain.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlin.coroutines.resume

internal interface PrinterConnection:java.io.Closeable {
    fun connect()
    fun write(bytes:ByteArray)
}

/** Only fictional test output. Cancellation closes the socket to unblock platform IO. */
internal suspend fun sendPrinterTest(connection:PrinterConnection,timeoutMs:Long=12_000):PrinterResult {
    val outputStarted=AtomicBoolean(false)
    return try {
        withTimeout(timeoutMs) {
            val scope=CoroutineScope(currentCoroutineContext())
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation{runCatching{connection.close()}}
                scope.launch(Dispatchers.IO) {
                    val result=try {
                        connection.connect()
                        outputStarted.set(true)
                        connection.write(PrinterTestSlip.bytes())
                        PrinterResult.SENT
                    }catch(_:Exception){if(outputStarted.get())PrinterResult.UNKNOWN else PrinterResult.FAILED}
                    continuation.resume(result)
                }
            }
        }
    }catch(_:TimeoutCancellationException){if(outputStarted.get())PrinterResult.UNKNOWN else PrinterResult.FAILED}
    finally{runCatching{connection.close()}}
}
