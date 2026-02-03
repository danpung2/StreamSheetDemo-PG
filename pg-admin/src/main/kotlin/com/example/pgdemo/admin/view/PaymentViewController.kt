package com.example.pgdemo.admin.view

import com.example.pgdemo.admin.client.PgMainApiClient
import com.example.pgdemo.admin.client.PaymentResponse
import com.example.pgdemo.admin.export.PaymentExportJobService
import com.example.pgdemo.admin.tenant.TenantContext
import com.example.pgdemo.common.domain.enum.ExportJobStatus
import com.example.pgdemo.common.domain.enum.PaymentStatus
import com.example.pgdemo.common.domain.enum.TenantType
import com.example.pgdemo.common.domain.repository.HeadquartersRepository
import com.example.pgdemo.common.domain.repository.MerchantRepository
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.RestClientException

@Controller
@RequestMapping("/admin")
class PaymentViewController(
    private val pgMainApiClient: PgMainApiClient,
    private val paymentExportJobService: PaymentExportJobService,
    private val headquartersRepository: HeadquartersRepository,
    private val merchantRepository: MerchantRepository
) {

    @GetMapping("/payments")
    fun payments(
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "20") size: Int,
        @RequestParam(name = "from", required = false) fromUtc: String?,
        @RequestParam(name = "to", required = false) toUtc: String?,
        @RequestParam(name = "headquartersId", required = false) headquartersId: String?,
        @RequestParam(name = "merchantId", required = false) merchantId: String?,
        @RequestParam(name = "status", required = false) status: PaymentStatus?,
        model: Model
    ): String {
        model.addAttribute("pageTitle", "Payments")
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)

        val tenantInfo = TenantContext.require()

        var filterError: String? = null

        val now = Instant.now()
        var resolvedFromUtc = now.minus(Duration.ofHours(24))
        var resolvedToUtc = now
        try {
            val parsedFromUtc = fromUtc
                ?.let { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC) }
            val parsedToUtc = toUtc
                ?.let { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC) }
            resolvedToUtc = parsedToUtc ?: now
            resolvedFromUtc = parsedFromUtc ?: now.minus(Duration.ofHours(24))
        } catch (ex: Exception) {
            filterError = "Invalid from/to format"
        }

        val maxDays = when (tenantInfo.tenantType) {
            TenantType.OPERATOR -> 90L
            TenantType.HEADQUARTERS, TenantType.MERCHANT -> 30L
        }

        if (resolvedFromUtc.isAfter(resolvedToUtc)) {
            filterError = "from must be <= to"
            resolvedFromUtc = now.minus(Duration.ofHours(24))
            resolvedToUtc = now
        }
        if (Duration.between(resolvedFromUtc, resolvedToUtc) > Duration.ofDays(maxDays)) {
            filterError = "date range must be within $maxDays days"
            resolvedFromUtc = now.minus(Duration.ofHours(24))
            resolvedToUtc = now
        }

        val parsedHeadquartersId = headquartersId?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val parsedMerchantId = merchantId?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        val resolvedHeadquartersId = when (tenantInfo.tenantType) {
            TenantType.OPERATOR -> parsedHeadquartersId
            TenantType.HEADQUARTERS -> tenantInfo.tenantId
            TenantType.MERCHANT -> null
        }
        var resolvedMerchantId = when (tenantInfo.tenantType) {
            TenantType.OPERATOR, TenantType.HEADQUARTERS -> parsedMerchantId
            TenantType.MERCHANT -> tenantInfo.tenantId
        }

        if (tenantInfo.tenantType == TenantType.MERCHANT) {
            if (parsedHeadquartersId != null) {
                filterError = "Merchant cannot filter by headquarters"
            }
            if (parsedMerchantId != null && parsedMerchantId != tenantInfo.tenantId) {
                filterError = "Merchant cannot filter other merchants"
            }
        }
        if (tenantInfo.tenantType == TenantType.HEADQUARTERS) {
            if (parsedHeadquartersId != null && parsedHeadquartersId != tenantInfo.tenantId) {
                filterError = "Headquarters cannot filter other headquarters"
            }
        }

        if (resolvedMerchantId != null && (tenantInfo.tenantType == TenantType.OPERATOR || tenantInfo.tenantType == TenantType.HEADQUARTERS)) {
            val merchant = merchantRepository.findById(resolvedMerchantId)
                .orElseThrow { IllegalArgumentException("Merchant not found") }
            val merchantHeadquartersId = merchant.headquarters?.id
            when (tenantInfo.tenantType) {
                TenantType.OPERATOR -> {
                    if (resolvedHeadquartersId != null && merchantHeadquartersId != resolvedHeadquartersId) {
                        filterError = "Merchant is not under the selected headquarters"
                        resolvedMerchantId = null
                    }
                }
                TenantType.HEADQUARTERS -> {
                    if (tenantInfo.tenantId == null) {
                        filterError = "Headquarters tenant requires tenantId"
                    }
                    if (merchantHeadquartersId != tenantInfo.tenantId) {
                        filterError = "Headquarters cannot access other headquarters merchants"
                        resolvedMerchantId = null
                    }
                }
                TenantType.MERCHANT -> Unit
            }
        }

        if (filterError != null) {
            model.addAttribute("filterError", filterError)
        }

        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            .withZone(ZoneOffset.UTC)
        model.addAttribute("fromUtcLocal", inputFormatter.format(resolvedFromUtc))
        model.addAttribute("toUtcLocal", inputFormatter.format(resolvedToUtc))
        model.addAttribute("headquartersId", resolvedHeadquartersId?.toString() ?: "")
        model.addAttribute("merchantId", resolvedMerchantId?.toString() ?: "")
        model.addAttribute("status", status?.name ?: "")
        model.addAttribute("statusOptions", PaymentStatus.values().map { it.name })

        val headquartersOptions = when (tenantInfo.tenantType) {
            TenantType.OPERATOR -> {
                headquartersRepository.findByStatus("ACTIVE")
                    .take(200)
                    .mapNotNull { hq ->
                        val id = hq.id
                        if (id == null) null else mapOf("id" to id.toString(), "name" to hq.name)
                    }
            }
            TenantType.HEADQUARTERS -> {
                tenantInfo.tenantId?.let { hqId ->
                    val hq = headquartersRepository.findById(hqId).orElse(null)
                    if (hq?.id != null) listOf(mapOf("id" to hq.id.toString(), "name" to hq.name)) else emptyList()
                } ?: emptyList()
            }
            TenantType.MERCHANT -> emptyList()
        }
        model.addAttribute("headquartersOptions", headquartersOptions)

        val merchantOptions = when (tenantInfo.tenantType) {
            TenantType.OPERATOR, TenantType.HEADQUARTERS -> {
                val selectedHqId = when (tenantInfo.tenantType) {
                    TenantType.HEADQUARTERS -> tenantInfo.tenantId
                    else -> resolvedHeadquartersId
                }
                if (selectedHqId != null) {
                    merchantRepository.findByHeadquartersId(selectedHqId, PageRequest.of(0, 200))
                        .content
                        .mapNotNull { m ->
                            val id = m.id
                            if (id == null) null else mapOf("id" to id.toString(), "name" to m.name)
                        }
                } else {
                    emptyList()
                }
            }
            TenantType.MERCHANT -> emptyList()
        }
        model.addAttribute("merchantOptions", merchantOptions)

        var loadError = false
        val paymentPage = try {
            pgMainApiClient.listPayments(
                page = safePage,
                size = safeSize,
                fromUtc = resolvedFromUtc,
                toUtc = resolvedToUtc,
                headquartersId = resolvedHeadquartersId,
                merchantId = resolvedMerchantId,
                status = status
            )
        } catch (ex: RestClientException) {
            loadError = true
            null
        }

        val paymentResponses = paymentPage?.content.orEmpty()
        val totalElements = paymentPage?.totalElements ?: 0
        val currentPage = paymentPage?.number ?: safePage
        val pageSize = paymentPage?.size?.takeIf { it > 0 } ?: safeSize
        val totalPages = if (totalElements == 0L) {
            0
        } else {
            ((totalElements + pageSize - 1) / pageSize).toInt()
        }

        model.addAttribute("pageNumber", currentPage)
        model.addAttribute("pageSize", pageSize)
        model.addAttribute("totalElements", totalElements)
        model.addAttribute("totalPages", totalPages)
        model.addAttribute("hasPrev", currentPage > 0)
        model.addAttribute("hasNext", totalPages > 0 && (currentPage + 1) < totalPages)
        model.addAttribute("prevPage", (currentPage - 1).coerceAtLeast(0))
        model.addAttribute("nextPage", currentPage + 1)

        val merchantsById = merchantRepository.findByIdIn(paymentResponses.map { it.merchantId }.distinct())
            .associateBy { it.id }
        model.addAttribute(
            "payments",
            paymentResponses.map { payment ->
                val merchantName = merchantsById[payment.merchantId]?.name ?: payment.merchantId.toString()
                mapOf(
                    "id" to payment.id.toString(),
                    "merchant" to merchantName,
                    "amount" to formatAmount(payment.amount),
                    "method" to payment.paymentMethod,
                    "status" to formatStatus(payment.status),
                    "statusClass" to statusClass(payment.status),
                    "createdAt" to formatInstant(payment.requestedAt)
                )
            }
        )
        if (loadError) {
            model.addAttribute("loadError", true)
        }
        return "payments/list"
    }

    @GetMapping("/payments/{paymentId}")
    fun paymentDetail(@PathVariable paymentId: String, model: Model): String {
        model.addAttribute("pageTitle", "Payment Detail")
        var loadError = false
        val paymentUuid = runCatching { UUID.fromString(paymentId) }.getOrNull()
        val payment = if (paymentUuid != null) {
            try {
                pgMainApiClient.getPayment(paymentUuid)
            } catch (ex: RestClientException) {
                loadError = true
                null
            }
        } else {
            loadError = true
            null
        }

        val merchantName = if (payment != null) {
            try {
                pgMainApiClient.getMerchant(payment.merchantId)?.name ?: payment.merchantId.toString()
            } catch (ex: RestClientException) {
                loadError = true
                payment.merchantId.toString()
            }
        } else {
            "-"
        }
        model.addAttribute(
            "payment",
            mapOf(
                "id" to (payment?.id?.toString() ?: paymentId),
                "merchant" to merchantName,
                "amount" to (payment?.amount?.let { formatAmount(it) } ?: "-"),
                "method" to (payment?.paymentMethod ?: "-"),
                "customer" to (payment?.orderId ?: "-"),
                "status" to (payment?.status?.let { formatStatus(it) } ?: "-"),
                "statusClass" to (payment?.status?.let { statusClass(it) } ?: "warning"),
                "events" to buildEvents(payment)
            )
        )
        if (loadError) {
            model.addAttribute("loadError", true)
        }
        return "payments/detail"
    }

    @GetMapping("/exports/payments")
    fun paymentExports(
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "20") size: Int,
        @RequestParam(name = "fromUtc", required = false) fromUtc: String?,
        @RequestParam(name = "toUtc", required = false) toUtc: String?,
        @RequestParam(name = "headquartersId", required = false) headquartersId: String?,
        @RequestParam(name = "merchantId", required = false) merchantId: String?,
        @RequestParam(name = "status", required = false) status: ExportJobStatus?,
        model: Model
    ): String {
        model.addAttribute("pageTitle", "Exports")

        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)

        val now = Instant.now()

        val parsedFromUtc = fromUtc
            ?.let { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC) }
        val parsedToUtc = toUtc
            ?.let { LocalDateTime.parse(it).toInstant(ZoneOffset.UTC) }

        val resolvedToUtc = parsedToUtc ?: now
        val resolvedFromUtc = parsedFromUtc ?: now.minus(Duration.ofHours(24))

        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            .withZone(ZoneOffset.UTC)
        model.addAttribute("fromUtcLocal", inputFormatter.format(resolvedFromUtc))
        model.addAttribute("toUtcLocal", inputFormatter.format(resolvedToUtc))

        val parsedHeadquartersId = headquartersId?.takeIf { it.isNotBlank() }?.let(UUID::fromString)
        val parsedMerchantId = merchantId?.takeIf { it.isNotBlank() }?.let(UUID::fromString)

        val jobPage = paymentExportJobService.listJobs(
            pageable = PageRequest.of(safePage, safeSize),
            fromUtc = resolvedFromUtc,
            toUtc = resolvedToUtc,
            headquartersId = parsedHeadquartersId,
            merchantId = parsedMerchantId,
            status = status
        )

        val exportJobs = jobPage.content
        model.addAttribute(
            "exportJobs",
            exportJobs.map { job ->
                mapOf(
                    "jobId" to job.jobId,
                    "range" to "${formatInstant(job.fromUtc)} — ${formatInstant(job.toUtc)}",
                    "requestedBy" to job.requestedBy,
                    "status" to job.status.name,
                    "statusClass" to exportStatusClass(job.status),
                    "progress" to (job.progressLabel ?: "-"),
                    "queuedAt" to formatInstant(job.queuedAt),
                    "startedAt" to formatInstant(job.startedAt),
                    "finishedAt" to formatInstant(job.finishedAt),
                    "canDownload" to (job.status == ExportJobStatus.COMPLETED),
                    "canCancel" to (job.status == ExportJobStatus.QUEUED),
                    "hasError" to (job.status == ExportJobStatus.FAILED),
                    "errorSummary" to (job.errorSummary ?: "-")
                )
            }
        )

        model.addAttribute("fromUtc", resolvedFromUtc)
        model.addAttribute("toUtc", resolvedToUtc)
        model.addAttribute("headquartersId", parsedHeadquartersId?.toString() ?: "")
        model.addAttribute("merchantId", parsedMerchantId?.toString() ?: "")
        model.addAttribute("status", status?.name ?: "")
        model.addAttribute("statusOptions", ExportJobStatus.values().map { it.name })

        val tenantInfo = TenantContext.require()
        val headquartersOptions = when (tenantInfo.tenantType) {
            TenantType.OPERATOR -> {
                headquartersRepository.findByStatus("ACTIVE")
                    .take(200)
                    .mapNotNull { hq ->
                        val id = hq.id
                        if (id == null) null else mapOf("id" to id.toString(), "name" to hq.name)
                    }
            }
            TenantType.HEADQUARTERS -> {
                tenantInfo.tenantId?.let { hqId ->
                    val hq = headquartersRepository.findById(hqId).orElse(null)
                    if (hq?.id != null) listOf(mapOf("id" to hq.id.toString(), "name" to hq.name)) else emptyList()
                } ?: emptyList()
            }
            TenantType.MERCHANT -> emptyList()
        }
        model.addAttribute("headquartersOptions", headquartersOptions)

        val merchantOptions = when (tenantInfo.tenantType) {
            TenantType.OPERATOR, TenantType.HEADQUARTERS -> {
                val selectedHqId = when (tenantInfo.tenantType) {
                    TenantType.HEADQUARTERS -> tenantInfo.tenantId
                    else -> parsedHeadquartersId
                }
                if (selectedHqId != null) {
                    merchantRepository.findByHeadquartersId(selectedHqId, PageRequest.of(0, 200))
                        .content
                        .mapNotNull { merchant ->
                            val id = merchant.id
                            if (id == null) null else mapOf("id" to id.toString(), "name" to merchant.name)
                        }
                } else {
                    emptyList()
                }
            }
            TenantType.MERCHANT -> emptyList()
        }
        model.addAttribute("merchantOptions", merchantOptions)

        model.addAttribute("pageNumber", jobPage.number)
        model.addAttribute("pageSize", jobPage.size)
        model.addAttribute("totalElements", jobPage.totalElements)
        model.addAttribute("totalPages", jobPage.totalPages)
        model.addAttribute("hasPrev", jobPage.hasPrevious())
        model.addAttribute("hasNext", jobPage.hasNext())
        model.addAttribute("prevPage", (jobPage.number - 1).coerceAtLeast(0))
        model.addAttribute("nextPage", jobPage.number + 1)

        model.addAttribute(
            "autoRefresh",
            exportJobs.any { it.status == ExportJobStatus.QUEUED || it.status == ExportJobStatus.RUNNING }
        )

        return "exports/payments"
    }

    private fun exportStatusClass(status: ExportJobStatus): String {
        return when (status) {
            ExportJobStatus.COMPLETED -> "success"
            ExportJobStatus.FAILED -> "danger"
            ExportJobStatus.CANCELLED -> "muted"
            ExportJobStatus.QUEUED, ExportJobStatus.RUNNING -> "warning"
        }
    }

    private fun buildEvents(payment: PaymentResponse?): List<Map<String, String>> {
        if (payment == null) {
            return emptyList()
        }

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC)
        fun fmtTime(instant: Instant): String = timeFormatter.format(instant)

        val events = mutableListOf<Pair<Instant, Map<String, String>>>()

        events.add(
            payment.requestedAt to mapOf(
                "time" to fmtTime(payment.requestedAt),
                "title" to "Requested",
                "note" to "orderId=${payment.orderId}"
            )
        )

        payment.processedAt?.let {
            events.add(
                it to mapOf(
                    "time" to fmtTime(it),
                    "title" to "Processed",
                    "note" to (payment.failureReason?.let { reason -> "failureReason=$reason" } ?: "-")
                )
            )
        }

        payment.completedAt?.let {
            events.add(
                it to mapOf(
                    "time" to fmtTime(it),
                    "title" to "Completed",
                    "note" to "status=${payment.status}"
                )
            )
        }

        return events.sortedBy { it.first }.map { it.second }
    }

    private fun formatAmount(amount: Long): String {
        val formatted = NumberFormat.getNumberInstance().format(amount.toDouble() / 100)
        return "\$$formatted"
    }

    private fun formatInstant(instant: Instant?): String {
        if (instant == null) {
            return "-"
        }
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC)
        return formatter.format(instant)
    }

    private fun formatStatus(status: PaymentStatus): String {
        return when (status) {
            PaymentStatus.PAYMENT_PENDING -> "Pending"
            PaymentStatus.PAYMENT_PROCESSING -> "Processing"
            PaymentStatus.PAYMENT_COMPLETED -> "Completed"
            PaymentStatus.PAYMENT_FAILED -> "Failed"
            PaymentStatus.PAYMENT_CANCELLED -> "Cancelled"
        }
    }

    private fun statusClass(status: PaymentStatus): String {
        return when (status) {
            PaymentStatus.PAYMENT_COMPLETED -> "success"
            PaymentStatus.PAYMENT_FAILED, PaymentStatus.PAYMENT_CANCELLED -> "danger"
            PaymentStatus.PAYMENT_PENDING, PaymentStatus.PAYMENT_PROCESSING -> "warning"
        }
    }
}
