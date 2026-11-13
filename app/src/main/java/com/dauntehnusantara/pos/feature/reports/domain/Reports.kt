package com.dauntehnusantara.pos.feature.reports.domain

import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class ManagementFailure(val code: String) : Exception(code)
data class ReportQuery(val source: PullScope, val from: LocalDate, val to: LocalDate, val outletId: String?) {
    init { require(!to.isBefore(from) && to.toEpochDay()-from.toEpochDay()<=365); require(source.role in listOf("OWNER","MANAGER")); require(source.role=="OWNER" || outletId==source.sale.outletId) }
    val key: String get() = "${source.key}|$from|$to|${outletId ?: "ALL"}"
    val startUtcMs: Long get() = from.atStartOfDay(ZoneId.of("Asia/Jakarta")).toInstant().toEpochMilli()
    val endUtcMs: Long get() = to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Jakarta")).toInstant().toEpochMilli()
}
data class ReportSummary(val count: Long, val subtotal: Long, val discount: Long, val total: Long, val cash: Long, val qris: Long)
data class ReportDay(val date: LocalDate, val count: Long, val total: Long)
data class ReportOutlet(val id: String, val name: String, val count: Long, val total: Long)
data class ReportProduct(val id: String, val name: String, val quantity: Long, val gross: Long)
data class OutletChoice(val id: String, val name: String, val active: Boolean)
data class SalesReport(val query: ReportQuery, val asOfMs: Long, val summary: ReportSummary, val days: List<ReportDay>,
    val outlets: List<ReportOutlet>, val products: List<ReportProduct>, val choices: List<OutletChoice>)
object ReportRules {
    fun validate(r: SalesReport) {
        val s=r.summary
        require(r.asOfMs>=0 && listOf(s.count,s.subtotal,s.discount,s.total,s.cash,s.qris).all { it>=0 })
        require(Math.subtractExact(s.subtotal,s.discount)==s.total && Math.addExact(s.cash,s.qris)==s.total)
        require(r.days.map { it.date }.distinct().size==r.days.size && r.outlets.map { it.id }.distinct().size==r.outlets.size)
        require(r.days.all { !it.date.isBefore(r.query.from) && !it.date.isAfter(r.query.to) && it.count>0 && it.total>=0 })
        require(r.outlets.all { it.count>0 && it.total>=0 && (r.query.outletId==null || it.id==r.query.outletId) })
        fun sum(values:List<Long>)=values.fold(0L,Math::addExact)
        require(sum(r.days.map { it.count })==s.count && sum(r.days.map { it.total })==s.total)
        require(sum(r.outlets.map { it.count })==s.count && sum(r.outlets.map { it.total })==s.total)
        if(s.count==0L)require(s.subtotal==0L&&s.discount==0L&&s.total==0L&&r.days.isEmpty()&&r.outlets.isEmpty()&&r.products.isEmpty())
        require(r.products.size<=20 && r.products.all { it.quantity>0 && it.gross>=0 } && sum(r.products.map { it.gross })<=s.subtotal)
        require(r.choices.map { it.id }.distinct().size==r.choices.size)
        if(r.query.source.role=="MANAGER") require(r.choices.all { it.id==r.query.source.sale.outletId })
    }
}
interface ReportingGateway {
    suspend fun reportingScope(online: Boolean): PullScope?
    suspend fun report(query: ReportQuery): SalesReport
}
interface ReportRepository {
    suspend fun cached(query: ReportQuery): SalesReport?
    suspend fun refresh(query: ReportQuery): SalesReport
    suspend fun export(report: SalesReport): String
}
class Reports @Inject constructor(private val repository: ReportRepository) {
    suspend fun cached(q:ReportQuery)=repository.cached(q)
    suspend fun refresh(q:ReportQuery)=repository.refresh(q)
    suspend fun export(r:SalesReport)=repository.export(r)
}
object ReportCsv {
    fun cell(value:String):String {
        val safe=if(value.trimStart().firstOrNull() in listOf('=','+','-','@') || value.firstOrNull() in listOf('\t','\r','\n')) "'$value" else value
        return "\"${safe.replace("\"","\"\"")}\""
    }
    fun render(r:SalesReport):String {
        ReportRules.validate(r)
        val rows=mutableListOf(listOf("section","date","id","label","count_or_quantity","amount_idr","as_of_utc_ms"))
        val s=r.summary
        rows+=listOf("summary","","","accepted_sales",s.count.toString(),s.total.toString(),r.asOfMs.toString())
        rows+=listOf("summary","","","cash","",s.cash.toString(),r.asOfMs.toString())
        rows+=listOf("summary","","","qris_manual","",s.qris.toString(),r.asOfMs.toString())
        rows+=listOf("scope","${r.query.from}/${r.query.to}",r.query.source.sale.brandId,r.query.outletId?:"ALL_BRAND_OUTLETS","","",r.asOfMs.toString())
        r.days.forEach { rows+=listOf("day",it.date.toString(),"","",it.count.toString(),it.total.toString(),r.asOfMs.toString()) }
        r.outlets.forEach { rows+=listOf("outlet","",it.id,it.name,it.count.toString(),it.total.toString(),r.asOfMs.toString()) }
        r.products.forEach { rows+=listOf("product","",it.id,it.name,it.quantity.toString(),it.gross.toString(),r.asOfMs.toString()) }
        return rows.joinToString("\r\n",postfix="\r\n") { row->row.joinToString(",",transform=::cell) }
    }
}
