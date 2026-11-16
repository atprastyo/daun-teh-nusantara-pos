package com.dauntehnusantara.pos.feature.printing.data

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.dauntehnusantara.pos.feature.printing.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.printerStore by preferencesDataStore(name="receipt_printer")
@Singleton
class AndroidPrinterRepository @Inject constructor(@param:ApplicationContext private val context:Context):PrinterRepository {
    private val mutex=Mutex()
    private fun adapter():android.bluetooth.BluetoothAdapter {
        if(Build.VERSION.SDK_INT>=31&&ContextCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
            throw PrinterFailure(PrinterProblem.PERMISSION)
        val adapter=context.getSystemService(BluetoothManager::class.java)?.adapter?:throw PrinterFailure(PrinterProblem.UNAVAILABLE)
        if(!adapter.isEnabled)throw PrinterFailure(PrinterProblem.OFF)
        return adapter
    }
    private val selections=PrinterSelectionStore(context.printerStore)
    override suspend fun selected(scope:PrinterScope)=selections.read(scope)
    override suspend fun select(scope:PrinterScope,device:PrinterDevice?)=selections.write(scope,device)
    override suspend fun paired():List<PrinterDevice> = withContext(Dispatchers.IO) {
        try {
            adapter().bondedDevices.filter{it.type!=BluetoothDevice.DEVICE_TYPE_LE}
                .map{PrinterDevice(it.address,PrinterTestSlip.safeName(it.name.orEmpty()))}.sortedWith(compareBy({it.name},{it.address}))
        }catch(_:SecurityException){throw PrinterFailure(PrinterProblem.PERMISSION)}
    }
    override suspend fun test(device:PrinterDevice):PrinterResult = mutex.withLock {
        var socket:android.bluetooth.BluetoothSocket?=null
        try {
            withContext(Dispatchers.IO) {
                val remote=adapter().getRemoteDevice(device.address)
                if(remote.bondState!=BluetoothDevice.BOND_BONDED)throw PrinterFailure(PrinterProblem.FAILED)
                // Bluetooth SIG SerialPort service 0x1101. No reflection/insecure fallback.
                socket=remote.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"))
            }
            val active=requireNotNull(socket)
            sendPrinterTest(object:PrinterConnection {
                override fun connect(){active.connect()}
                override fun write(bytes:ByteArray){active.outputStream.let{it.write(bytes);it.flush()}}
                override fun close(){active.close()}
            })
        }catch(e:CancellationException){throw e}
        catch(e:PrinterFailure){throw e}
        catch(_:SecurityException){throw PrinterFailure(PrinterProblem.PERMISSION)}
        catch(_:Exception){PrinterResult.FAILED}
        finally{runCatching{socket?.close()}}
    }
}
