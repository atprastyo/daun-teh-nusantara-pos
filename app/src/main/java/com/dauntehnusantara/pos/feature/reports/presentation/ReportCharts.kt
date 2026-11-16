package com.dauntehnusantara.pos.feature.reports.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.formatIdr
import com.dauntehnusantara.pos.core.designsystem.components.AppSection
import com.dauntehnusantara.pos.feature.reports.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Floating point is only a drawing proportion; displayed/exported amounts remain Long IDR. */
internal fun reportFraction(value:Long,maximum:Long):Float =
    if(maximum<=0)0f else (value.toDouble()/maximum.toDouble()).coerceIn(0.0,1.0).toFloat()
internal fun paymentPercent(value:Long,total:Long):String = if(total<=0)"—" else
    BigDecimal.valueOf(value).multiply(BigDecimal(100)).divide(BigDecimal.valueOf(total),1,RoundingMode.HALF_UP)
        .toPlainString().replace('.',',')+"%"
internal fun chartProducts(products:List<ReportProduct>,units:Boolean):List<ReportProduct> =
    products.sortedWith(compareByDescending<ReportProduct>{if(units)it.quantity else it.gross}.thenBy{it.id}.thenBy{it.name}).take(5)

@Composable
internal fun ReportOverviewCharts(report:SalesReport) {
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints {
        if(report.days.isNotEmpty()&&maxWidth>=900.dp&&fontScale<1.5f) {
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(2f)){DailySalesChart(report)}
                Box(Modifier.weight(1f)){PaymentSalesChart(report.summary)}
            }
        } else Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            PaymentSalesChart(report.summary)
            if(report.days.isNotEmpty())DailySalesChart(report)
        }
    }
}

@Composable
internal fun DailySalesChart(report:SalesReport) {
    var counts by remember(report.query.key){mutableStateOf(false)}
    var chosenDate by remember(report.query.key,report.asOfMs){mutableStateOf(report.days.lastOrNull()?.date)}
    val maximum=report.days.maxOfOrNull{if(counts)it.count else it.total}?:0
    AppSection(stringResource(R.string.report_chart_daily)) {
        ChartMode(counts,{counts=it},stringResource(R.string.report_chart_rupiah),stringResource(R.string.report_chart_transactions))
        Text(stringResource(R.string.report_chart_scale,if(counts)maximum.toString() else formatIdr(maximum)),style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("report.chart.daily"),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            report.days.forEach { day ->
                val value=if(counts)day.count else day.total
                val description="${day.date} · ${day.count} penjualan · ${formatIdr(day.total)}"
                Column(Modifier.width(72.dp).clickable(role=Role.Button,onClick={chosenDate=day.date})
                    .semantics{contentDescription=description;selected=day.date==chosenDate},horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(128.dp),contentAlignment=Alignment.BottomCenter) {
                        Box(Modifier.width(32.dp).fillMaxHeight(reportFraction(value,maximum))
                            .background(if(chosenDate==day.date)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,MaterialTheme.shapes.small))
                    }
                    Text(day.date.format(DateTimeFormatter.ofPattern("dd MMM",Locale.forLanguageTag("id-ID"))),
                        style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(vertical=12.dp))
                }
            }
        }
        report.days.find{it.date==chosenDate}?.let{Text("${it.date} · ${it.count} penjualan · ${formatIdr(it.total)}",Modifier.testTag("report.chart.selection"))}
        Text(stringResource(R.string.report_chart_days_help),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ProductSalesChart(report:SalesReport) {
    var units by remember(report.query.key){mutableStateOf(false)}
    val rows=chartProducts(report.products,units)
    val maximum=rows.maxOfOrNull{if(units)it.quantity else it.gross}?:0
    AppSection(stringResource(R.string.report_chart_products)) {
        ChartMode(units,{units=it},stringResource(R.string.report_chart_rupiah),stringResource(R.string.report_chart_units))
        Text(stringResource(R.string.report_chart_products_help),style=MaterialTheme.typography.bodySmall)
        rows.forEachIndexed { index,product ->
            val value=if(units)product.quantity else product.gross
            Column(Modifier.fillMaxWidth().testTag("report.chart.product.$index"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(product.name,style=MaterialTheme.typography.titleSmall)
                Text("${product.quantity} unit · ${formatIdr(product.gross)}",style=MaterialTheme.typography.bodyMedium)
                ChartTrack(reportFraction(value,maximum))
            }
        }
    }
}

@Composable
internal fun PaymentSalesChart(summary:ReportSummary) {
    AppSection(stringResource(R.string.report_chart_payment)) {
        val cash=reportFraction(summary.cash,summary.total)
        Row(Modifier.fillMaxWidth().height(16.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest,MaterialTheme.shapes.small)
            .testTag("report.chart.payment")) {
            if(cash>0)Box(Modifier.weight(cash).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            val qris=reportFraction(summary.qris,summary.total)
            if(qris>0)Box(Modifier.weight(qris).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
        }
        PaymentLegend(MaterialTheme.colorScheme.primary,"${stringResource(R.string.pos_cash)} · ${formatIdr(summary.cash)} · ${paymentPercent(summary.cash,summary.total)}")
        PaymentLegend(MaterialTheme.colorScheme.tertiary,"QRIS manual · ${formatIdr(summary.qris)} · ${paymentPercent(summary.qris,summary.total)}")
        Text(stringResource(R.string.report_chart_payment_help),style=MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ChartTrack(fraction:Float) {
    Box(Modifier.fillMaxWidth().height(12.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest,MaterialTheme.shapes.small)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(MaterialTheme.colorScheme.primary,MaterialTheme.shapes.small))
    }
}
@Composable
private fun ChartMode(second:Boolean,onChange:(Boolean)->Unit,firstLabel:String,secondLabel:String) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(!second,{onChange(false)},label={Text(firstLabel)})
        FilterChip(second,{onChange(true)},label={Text(secondLabel)})
    }
}

@Composable
private fun PaymentLegend(color:androidx.compose.ui.graphics.Color,label:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color,MaterialTheme.shapes.small))
        Text(label,Modifier.weight(1f))
    }
}
