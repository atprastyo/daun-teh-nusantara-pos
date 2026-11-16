package com.dauntehnusantara.pos.feature.printing.domain

import java.security.MessageDigest

data class PrinterDevice(val address:String,val name:String)
data class PrinterScope(val project:String,val user:String,val outlet:String) {
    val key:String get()=MessageDigest.getInstance("SHA-256").digest(listOf(project,user,outlet).joinToString("\u0000").toByteArray())
        .joinToString(""){"%02x".format(it)}
}
enum class PrinterResult { SENT, FAILED, UNKNOWN }
enum class PrinterProblem { PERMISSION, UNAVAILABLE, OFF, READ, WRITE, FAILED }
class PrinterFailure(val problem:PrinterProblem):Exception(problem.name)
interface PrinterRepository {
    suspend fun selected(scope:PrinterScope):PrinterDevice?
    suspend fun select(scope:PrinterScope,device:PrinterDevice?)
    suspend fun paired():List<PrinterDevice>
    suspend fun test(device:PrinterDevice):PrinterResult
}
object PrinterTestSlip {
    // Conservative plain ASCII: no ESC/POS, cutter, drawer, code-page or logo commands.
    fun bytes()="DAUN TEH NUSANTARA\nTES PRINTER 58 MM\n------------------------\nBUKAN STRUK PENJUALAN\nTidak ada transaksi baru\n1234567890 Rp 5.000\nPeriksa hasil cetak\n\n\n".toByteArray(Charsets.US_ASCII)
    fun safeName(value:String)=value.filterNot{it.isISOControl()||Character.getType(it)==Character.FORMAT.toInt()}.take(80).ifBlank{"Printer Bluetooth"}
}
