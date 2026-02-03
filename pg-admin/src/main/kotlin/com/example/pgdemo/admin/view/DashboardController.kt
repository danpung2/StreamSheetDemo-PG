package com.example.pgdemo.admin.view

import com.example.pgdemo.admin.tenant.TenantContext
import com.example.pgdemo.common.domain.document.PaymentExportView
import com.example.pgdemo.common.domain.enum.ExportJobStatus
import com.example.pgdemo.common.domain.enum.ExportJobType
import com.example.pgdemo.common.domain.enum.PaymentStatus
import com.example.pgdemo.common.domain.enum.RefundStatus
import com.example.pgdemo.common.domain.enum.TenantType
import com.example.pgdemo.common.domain.repository.ExportJobRepository
import com.example.pgdemo.common.domain.repository.MerchantRepository
import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/admin")
class DashboardController(
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val refundTransactionRepository: RefundTransactionRepository,
    private val merchantRepository: MerchantRepository,
    private val exportJobRepository: ExportJobRepository,
    private val mongoTemplate: MongoTemplate
) {
    private val utcInputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        .withZone(ZoneOffset.UTC)

    private val utcDisplayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
        .withZone(ZoneOffset.UTC)

    @GetMapping("", "/dashboard")
    fun dashboard(model: Model): String {
        model.addAttribute("pageTitle", "Dashboard")

        val tenantInfo = TenantContext.require()
        val now = Instant.now()
        val toUtc = now
        val fromUtc = now.minus(Duration.ofHours(24))

        val kpi = loadKpis(tenantInfo.tenantType, tenantInfo.tenantId, fromUtc, toUtc)
        model.addAttribute(
            "kpis",
            mapOf(
                "totalAmount" to formatAmount(kpi.completedAmountSum),
                "successRate" to formatPercent(kpi.successRate),
                "failureRate" to formatPercent(kpi.failureRate),
                "refundRate" to formatPercent(kpi.refundRate),
                "delayP95" to formatDurationMs(kpi.delayP95Ms)
            )
        )
        model.addAttribute("dashboardUpdatedAt", utcDisplayFormatter.format(now))

        val topMerchants = loadTopMerchants(tenantInfo.tenantType, tenantInfo.tenantId, fromUtc, toUtc)
        model.addAttribute("topMerchants", topMerchants)

        val exportStatus = loadExportStatus()
        model.addAttribute("exportStatus", exportStatus)

        val warnings = buildWarnings(tenantInfo.tenantType, tenantInfo.tenantId, now)
        model.addAttribute("warnings", warnings)

        val paymentsLinkFrom = utcInputFormatter.format(fromUtc)
        val paymentsLinkTo = utcInputFormatter.format(toUtc)
        model.addAttribute("paymentsLinkFrom", paymentsLinkFrom)
        model.addAttribute("paymentsLinkTo", paymentsLinkTo)

        return "dashboard"
    }

    private data class KpiResult(
        val totalCount: Long,
        val completedCount: Long,
        val failedCount: Long,
        val completedAmountSum: Long,
        val refundAmountSum: Long,
        val delayP95Ms: Double
    ) {
        val successRate: Double get() = if (totalCount <= 0L) 0.0 else completedCount.toDouble() / totalCount.toDouble()
        val failureRate: Double get() = if (totalCount <= 0L) 0.0 else failedCount.toDouble() / totalCount.toDouble()
        val refundRate: Double get() = if (completedAmountSum <= 0L) 0.0 else refundAmountSum.toDouble() / completedAmountSum.toDouble()
    }

    private fun loadKpis(tenantType: TenantType, tenantId: UUID?, fromUtc: Instant, toUtc: Instant): KpiResult {
        return when (tenantType) {
            TenantType.OPERATOR -> {
                val total = paymentTransactionRepository.countByRequestedAtBetween(fromUtc, toUtc)
                val completed = paymentTransactionRepository.countByRequestedAtBetweenAndStatus(fromUtc, toUtc, PaymentStatus.PAYMENT_COMPLETED)
                val failed = paymentTransactionRepository.countByRequestedAtBetweenAndStatus(fromUtc, toUtc, PaymentStatus.PAYMENT_FAILED)
                val completedAmount = paymentTransactionRepository.sumAmountByRequestedAtBetweenAndStatus(fromUtc, toUtc, PaymentStatus.PAYMENT_COMPLETED)
                val refundAmount = refundTransactionRepository.sumRefundAmountByRequestedAtBetweenAndStatus(fromUtc, toUtc, RefundStatus.REFUND_COMPLETED)
                val p95 = paymentTransactionRepository.p95DelayMsByRequestedAtBetween(fromUtc, toUtc)
                KpiResult(total, completed, failed, completedAmount, refundAmount, p95)
            }
            TenantType.HEADQUARTERS -> {
                val hqId = tenantId ?: return KpiResult(0, 0, 0, 0, 0, 0.0)
                val total = paymentTransactionRepository.countByHeadquartersIdAndRequestedAtBetween(hqId, fromUtc, toUtc)
                val completed = paymentTransactionRepository.countByHeadquartersIdAndRequestedAtBetweenAndStatus(hqId, fromUtc, toUtc, PaymentStatus.PAYMENT_COMPLETED)
                val failed = paymentTransactionRepository.countByHeadquartersIdAndRequestedAtBetweenAndStatus(hqId, fromUtc, toUtc, PaymentStatus.PAYMENT_FAILED)
                val completedAmount = paymentTransactionRepository.sumAmountByHeadquartersIdAndRequestedAtBetweenAndStatus(hqId, fromUtc, toUtc, PaymentStatus.PAYMENT_COMPLETED)
                val refundAmount = refundTransactionRepository.sumRefundAmountByHeadquartersIdAndDateRangeAndStatus(hqId, fromUtc, toUtc, RefundStatus.REFUND_COMPLETED)
                val p95 = paymentTransactionRepository.p95DelayMsByHeadquartersIdAndRequestedAtBetween(hqId, fromUtc, toUtc)
                KpiResult(total, completed, failed, completedAmount, refundAmount, p95)
            }
            TenantType.MERCHANT -> {
                val merchantId = tenantId ?: return KpiResult(0, 0, 0, 0, 0, 0.0)
                val total = paymentTransactionRepository.countByMerchantIdAndRequestedAtBetween(merchantId, fromUtc, toUtc)
                val completed = paymentTransactionRepository.countByMerchantIdAndRequestedAtBetweenAndStatus(merchantId, fromUtc, toUtc, PaymentStatus.PAYMENT_COMPLETED)
                val failed = paymentTransactionRepository.countByMerchantIdAndRequestedAtBetweenAndStatus(merchantId, fromUtc, toUtc, PaymentStatus.PAYMENT_FAILED)
                val completedAmount = paymentTransactionRepository.sumAmountByMerchantIdAndDateRange(merchantId, fromUtc, toUtc, PaymentStatus.PAYMENT_COMPLETED)
                val refundAmount = refundTransactionRepository.sumRefundAmountByMerchantIdAndDateRange(merchantId, fromUtc, toUtc, RefundStatus.REFUND_COMPLETED)
                val p95 = paymentTransactionRepository.p95DelayMsByMerchantIdAndRequestedAtBetween(merchantId, fromUtc, toUtc)
                KpiResult(total, completed, failed, completedAmount, refundAmount, p95)
            }
        }
    }

    private fun loadTopMerchants(tenantType: TenantType, tenantId: UUID?, fromUtc: Instant, toUtc: Instant): List<Map<String, String>> {
        val limit = 10
        val minCount = 20L
        val rows = when (tenantType) {
            TenantType.OPERATOR -> paymentTransactionRepository.topMerchantsByFailureRate(fromUtc, toUtc, minCount, limit)
            TenantType.HEADQUARTERS -> {
                val hqId = tenantId ?: return emptyList()
                paymentTransactionRepository.topMerchantsByFailureRateForHeadquarters(hqId, fromUtc, toUtc, minCount, limit)
            }
            TenantType.MERCHANT -> return emptyList()
        }

        val merchantIds = rows.map { it.getMerchantId() }
        val merchantsById = merchantRepository.findByIdIn(merchantIds).associateBy { it.id }

        return rows.map { row ->
            val merchantId = row.getMerchantId()
            val total = row.getTotalCount().coerceAtLeast(1)
            val failed = row.getFailedCount().coerceAtLeast(0)
            val failureRate = failed.toDouble() / total.toDouble()
            val merchantName = merchantsById[merchantId]?.name ?: merchantId.toString()
            mapOf(
                "merchantId" to merchantId.toString(),
                "merchantName" to merchantName,
                "failureRate" to formatPercent(failureRate),
                "failedCount" to failed.toString(),
                "totalCount" to total.toString()
            )
        }
    }

    private fun loadExportStatus(): Map<String, String> {
        val latestExport = exportJobRepository.findByJobType(
            ExportJobType.PAYMENTS,
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "queuedAt"))
        ).content.firstOrNull()

        val latestExportStatus = latestExport?.status?.name ?: "-"
        val latestExportQueuedAt = latestExport?.queuedAt?.let { utcDisplayFormatter.format(it) } ?: "-"
        val latestExportFinishedAt = latestExport?.finishedAt?.let { utcDisplayFormatter.format(it) } ?: "-"

        val latestView = mongoTemplate.findOne(
            Query().with(Sort.by(Sort.Direction.DESC, "syncedAt")).limit(1),
            PaymentExportView::class.java
        )
        val latestSyncedAt = latestView?.syncedAt?.let { utcDisplayFormatter.format(it) } ?: "-"

        return mapOf(
            "exportJobStatus" to latestExportStatus,
            "exportJobQueuedAt" to latestExportQueuedAt,
            "exportJobFinishedAt" to latestExportFinishedAt,
            "paymentViewSyncedAt" to latestSyncedAt
        )
    }

    private fun buildWarnings(tenantType: TenantType, tenantId: UUID?, now: Instant): List<Map<String, String>> {
        val warnings = mutableListOf<Map<String, String>>()

        val currentFrom = now.minus(Duration.ofHours(1))
        val currentTo = now
        val baselineFrom = currentFrom.minus(Duration.ofDays(1))
        val baselineTo = currentTo.minus(Duration.ofDays(1))

        val current = loadKpis(tenantType, tenantId, currentFrom, currentTo)
        val baseline = loadKpis(tenantType, tenantId, baselineFrom, baselineTo)

        if (baseline.totalCount > 0 && current.totalCount > 0) {
            val failureSpike = current.failureRate >= (baseline.failureRate * 2.0) && (current.failureRate - baseline.failureRate) >= 0.01
            val refundSpike = current.refundRate >= (baseline.refundRate * 2.0) && (current.refundRate - baseline.refundRate) >= 0.01

            if (failureSpike) {
                warnings.add(
                    mapOf(
                        "title" to "Failure rate spike",
                        "detail" to "${formatPercent(current.failureRate)} (baseline ${formatPercent(baseline.failureRate)})",
                        "link" to "/admin/payments?from=${utcInputFormatter.format(currentFrom)}&to=${utcInputFormatter.format(currentTo)}&status=${PaymentStatus.PAYMENT_FAILED.name}"
                    )
                )
            }
            if (refundSpike) {
                warnings.add(
                    mapOf(
                        "title" to "Refund rate spike",
                        "detail" to "${formatPercent(current.refundRate)} (baseline ${formatPercent(baseline.refundRate)})",
                        "link" to "/admin/payments?from=${utcInputFormatter.format(currentFrom)}&to=${utcInputFormatter.format(currentTo)}"
                    )
                )
            }
        }

        val latestExport = exportJobRepository.findByJobType(
            ExportJobType.PAYMENTS,
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "queuedAt"))
        ).content.firstOrNull()
        if (latestExport?.status == ExportJobStatus.FAILED) {
            warnings.add(
                mapOf(
                    "title" to "Latest export failed",
                    "detail" to (latestExport.errorSummary ?: "FAILED"),
                    "link" to "/admin/exports/payments"
                )
            )
        }

        val latestView = mongoTemplate.findOne(
            Query().with(Sort.by(Sort.Direction.DESC, "syncedAt")).limit(1),
            PaymentExportView::class.java
        )
        val syncedAt = latestView?.syncedAt
        if (syncedAt != null && Duration.between(syncedAt, now) > Duration.ofHours(2)) {
            warnings.add(
                mapOf(
                    "title" to "Payment view sync is stale",
                    "detail" to "Last synced ${utcDisplayFormatter.format(syncedAt)}",
                    "link" to "/admin/exports/payments"
                )
            )
        }

        return warnings
    }

    private fun formatAmount(amount: Long): String {
        val formatted = NumberFormat.getNumberInstance().format(amount.toDouble() / 100)
        return "\$$formatted"
    }

    private fun formatPercent(rate: Double): String {
        val pct = rate * 100.0
        return String.format(java.util.Locale.US, "%.1f%%", pct)
    }

    private fun formatDurationMs(ms: Double): String {
        val rounded = ms.toLong()
        if (rounded < 1000) {
            return "${rounded}ms"
        }
        val seconds = rounded / 1000.0
        return String.format(java.util.Locale.US, "%.2fs", seconds)
    }
}
