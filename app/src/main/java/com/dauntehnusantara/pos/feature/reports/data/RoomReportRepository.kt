package com.dauntehnusantara.pos.feature.reports.data

import androidx.room.withTransaction
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import javax.inject.Inject
import org.json.JSONObject

class RoomReportRepository @Inject constructor(private val db:PosDatabase,private val gateway:ReportingGateway):ReportRepository {
    private suspend fun check(q:ReportQuery) { if(gateway.reportingScope(false)!=q.source)throw ManagementFailure("AUTH_REQUIRED") }
    override suspend fun cached(query:ReportQuery):SalesReport? {
        check(query)
        val raw=db.pullDao().metadata("report.${query.key}")?:return null
        return ReportJson.read(JSONObject(raw),query)
    }
    override suspend fun refresh(query:ReportQuery):SalesReport {
        check(query)
        val report=gateway.report(query);require(report.query==query);ReportRules.validate(report);check(query)
        db.withTransaction { db.metadataDao().put(AppMetadata("report.${query.key}",ReportJson.write(report))) }
        return report
    }
    override suspend fun export(report:SalesReport):String { check(report.query);return ReportCsv.render(report) }
}
