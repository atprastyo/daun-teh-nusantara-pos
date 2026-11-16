package com.dauntehnusantara.pos.feature.printing.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.dauntehnusantara.pos.feature.printing.domain.*
import kotlinx.coroutines.flow.first

internal class PrinterSelectionStore(private val store:DataStore<Preferences>) {
    suspend fun read(scope:PrinterScope):PrinterDevice? {
        val data=store.data.first()
        val address=data[stringPreferencesKey(scope.key+".address")]?:return null
        return PrinterDevice(address,PrinterTestSlip.safeName(data[stringPreferencesKey(scope.key+".name")].orEmpty()))
    }
    suspend fun write(scope:PrinterScope,device:PrinterDevice?) {
        require(device==null||device.address.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")))
        store.edit {
            val address=stringPreferencesKey(scope.key+".address");val name=stringPreferencesKey(scope.key+".name")
            if(device==null){it.remove(address);it.remove(name)}else{
                it[address]=device.address;it[name]=PrinterTestSlip.safeName(device.name)
            }
        }
    }
}
