package com.example.pgdemo.admin.view

import com.example.pgdemo.admin.client.PgMainApiClient
import com.example.pgdemo.admin.client.PaymentResponse
import com.example.pgdemo.admin.export.PaymentExportJobService
import com.example.pgdemo.admin.tenant.TenantContext
import com.example.pgdemo.common.domain.enum.ExportJobStatus
import com.example.pgdemo.common.domain.enum.PaymentStatus
import com.example.pgdemo.common.domain.enum.RefundStatus
import com.example.pgdemo.common.domain.enum.TenantType
import com.example.pgdemo.common.domain.repository.HeadquartersRepository
import com.example.pgdemo.common.domain.repository.MerchantRepository
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
    private val merchantRepository: MerchantRepository,
    private val refundTransactionRepository: RefundTransactionRepository
) {

    private val displayZone = ZoneId.systemDefault()

    @GetMapping("/payments")
    fun payments(
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "20") size: Int,
        @RequestParam(name = "from", required = false) fromUtc: String?,
        @RequestParam(name = "to", required = false) toUtc: String?,
        @RequestParam(name = "headquartersId", required = false) headquartersId: String?,
        @RequestParam(name = "merchantId", required = false) merchantId: String?,
        @RequestParam(name = "type", required = false) type: String?,
        @RequestParam(name = "status", required = false) status: String?,
        model: Model
    ): String {
        model.addAttribute("pageTitle", "Transactions")
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)

        val tenantInfo = TenantContext.require()

        var filterError: String? = null

        val now = Instant.now()
        var resolvedFromUtc = now.minus(Duration.ofHours(24))
        var resolvedToUtc = now
        try {
            val parsedFromUtc = fromUtc
                ?.let { LocalDateTime.parse(it).atZone(displayZone).toInstant() }
            val parsedToUtc = toUtc
                ?.let { LocalDateTime.parse(it).atZone(displayZone).toInstant() }
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
            .withZone(displayZone)
        model.addAttribute("fromUtcLocal", inputFormatter.format(resolvedFromUtc))
        model.addAttribute("toUtcLocal", inputFormatter.format(resolvedToUtc))
        model.addAttribute("headquartersId", resolvedHeadquartersId?.toString() ?: "")
        model.addAttribute("merchantId", resolvedMerchantId?.toString() ?: "")

        val normalizedType = type
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                when (it) {
                    "payment" -> "Payment"
                    "refund" -> "Refund"
                    else -> null
                }
            }

        val rawStatus = status?.trim()?.takeIf { it.isNotBlank() }

        var resolvedStatus: String? = null
        var paymentStatusFilter: PaymentStatus? = null
        var refundStatusFilter: RefundStatus? = null

        if (normalizedType == null) {
            resolvedStatus = rawStatus
            paymentStatusFilter = rawStatus?.let { runCatching { PaymentStatus.valueOf(it) }.getOrNull() }
            refundStatusFilter = rawStatus?.let { runCatching { RefundStatus.valueOf(it) }.getOrNull() }
            if (rawStatus != null && paymentStatusFilter == null && refundStatusFilter == null) {
                filterError = "Invalid status"
            }
        }

        if (filterError != null) {
            model.addAttribute("filterError", filterError)
        }

        model.addAttribute("type", normalizedType ?: "")
        model.addAttribute("status", resolvedStatus ?: "")

        val statusOptions = when (normalizedType) {
            "Payment" -> PaymentStatus.values().map { s -> mapOf("value" to s.name, "label" to formatPaymentStatus(s)) }
            "Refund" -> RefundStatus.values().map { s -> mapOf("value" to s.name, "label" to formatRefundStatus(s)) }
            else -> PaymentStatus.values().map { s -> mapOf("value" to s.name, "label" to formatPaymentStatus(s)) } +
                RefundStatus.values().map { s -> mapOf("value" to s.name, "label" to formatRefundStatus(s)) }
        }
        model.addAttribute("statusOptions", statusOptions)

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

        val showPayments = normalizedType != "Refund" && (resolvedStatus == null || paymentStatusFilter != null)
        val showRefunds = normalizedType != "Payment" && (resolvedStatus == null || refundStatusFilter != null)

        // Export supports both payments and refunds.
        model.addAttribute("exportEnabled", true)

        val fetchSize = ((safePage + 1) * safeSize).coerceIn(1, 200)

        var loadError = false
        val paymentPage = if (showPayments) {
            try {
                pgMainApiClient.listPayments(
                    page = 0,
                    size = fetchSize,
                    fromUtc = resolvedFromUtc,
                    toUtc = resolvedToUtc,
                    headquartersId = resolvedHeadquartersId,
                    merchantId = resolvedMerchantId,
                    status = paymentStatusFilter
                )
            } catch (ex: RestClientException) {
                loadError = true
                null
            }
        } else {
            null
        }

        val refundPage = if (showRefunds) {
            val pageable = PageRequest.of(
                0,
                fetchSize,
                Sort.by(Sort.Direction.DESC, "requestedAt").and(Sort.by(Sort.Direction.DESC, "id"))
            )
            when {
                resolvedMerchantId != null ->
                    refundTransactionRepository.findByMerchantIdAndRequestedAtBetweenAndStatus(
                        merchantId = resolvedMerchantId,
                        startDate = resolvedFromUtc,
                        endDate = resolvedToUtc,
                        status = refundStatusFilter,
                        pageable = pageable
                    )
                resolvedHeadquartersId != null ->
                    refundTransactionRepository.findByHeadquartersIdAndRequestedAtBetweenAndStatus(
                        headquartersId = resolvedHeadquartersId,
                        startDate = resolvedFromUtc,
                        endDate = resolvedToUtc,
                        status = refundStatusFilter,
                        pageable = pageable
                    )
                refundStatusFilter != null ->
                    refundTransactionRepository.findByRequestedAtBetweenAndStatus(resolvedFromUtc, resolvedToUtc, refundStatusFilter, pageable)
                else ->
                    refundTransactionRepository.findByRequestedAtBetween(resolvedFromUtc, resolvedToUtc, pageable)
            }
        } else {
            null
        }

        data class TxRow(val requestedAt: Instant, val row: Map<String, String>)

        val paymentRows = paymentPage?.content.orEmpty().map { payment ->
            TxRow(
                requestedAt = payment.requestedAt,
                row = mapOf(
                    "id" to payment.id.toString(),
                    "type" to "Payment",
                    "amount" to formatAmount(payment.amount),
                    "status" to formatPaymentStatus(payment.status),
                    "statusClass" to paymentStatusClass(payment.status),
                    "createdAt" to formatInstant(payment.requestedAt)
                )
            )
        }

        val refundRows = refundPage?.content.orEmpty().mapNotNull { refund ->
            val id = refund.id ?: return@mapNotNull null
            TxRow(
                requestedAt = refund.requestedAt,
                row = mapOf(
                    "id" to id.toString(),
                    "type" to "Refund",
                    "amount" to formatAmount(refund.refundAmount),
                    "status" to formatRefundStatus(refund.status),
                    "statusClass" to refundStatusClass(refund.status),
                    "createdAt" to formatInstant(refund.requestedAt)
                )
            )
        }

        val combined = (paymentRows + refundRows)
            .sortedWith(
                compareByDescending<TxRow> { it.requestedAt }
                    .thenByDescending { it.row["id"] ?: "" }
            )

        val start = (safePage * safeSize).coerceAtLeast(0)
        val endExclusive = (start + safeSize).coerceAtMost(combined.size)
        val pageItems = if (start >= combined.size) emptyList() else combined.subList(start, endExclusive)

        val paymentTotal = if (showPayments) (paymentPage?.totalElements ?: 0L) else 0L
        val refundTotal = if (showRefunds) (refundPage?.totalElements ?: 0L) else 0L
        val totalElements = paymentTotal + refundTotal
        val totalPages = if (totalElements == 0L) 0 else ((totalElements + safeSize - 1) / safeSize).toInt()

        model.addAttribute("pageNumber", safePage)
        model.addAttribute("pageSize", safeSize)
        model.addAttribute("totalElements", totalElements)
        model.addAttribute("totalPages", totalPages)
        model.addAttribute("hasPrev", safePage > 0)
        model.addAttribute("hasNext", totalPages > 0 && (safePage + 1) < totalPages)
        model.addAttribute("prevPage", (safePage - 1).coerceAtLeast(0))
        model.addAttribute("nextPage", safePage + 1)

        model.addAttribute("transactions", pageItems.map { it.row })

        if (loadError) {
            model.addAttribute("loadError", true)
        }
        return "payments/list"
    }

    @GetMapping("/payments/{transactionId}")
    fun paymentDetail(@PathVariable("transactionId") transactionId: String, model: Model): String {
        model.addAttribute("pageTitle", "Transaction Detail")
        var loadError = false

        val txUuid = runCatching { UUID.fromString(transactionId) }.getOrNull()

        val payment = if (txUuid != null) {
            try {
                pgMainApiClient.getPayment(txUuid)
            } catch (ex: RestClientException) {
                null
            }
        } else {
            null
        }

        if (payment != null) {
            val merchantName = try {
                pgMainApiClient.getMerchant(payment.merchantId)?.name ?: payment.merchantId.toString()
            } catch (ex: RestClientException) {
                loadError = true
                payment.merchantId.toString()
            }

            model.addAttribute(
                "payment",
                mapOf(
                    "id" to payment.id.toString(),
                    "merchant" to merchantName,
                    "amount" to formatAmount(payment.amount),
                    "method" to payment.paymentMethod,
                    "customer" to payment.orderId,
                    "status" to formatPaymentStatus(payment.status),
                    "statusClass" to paymentStatusClass(payment.status),
                    "events" to buildPaymentEvents(payment)
                )
            )
        } else {
            val refund = txUuid?.let { refundTransactionRepository.findById(it).orElse(null) }
            if (refund != null) {
                val paymentId = refund.payment.id?.toString() ?: "-"

                val paymentForRefund = refund.payment.id?.let { pid ->
                    try {
                        pgMainApiClient.getPayment(pid)
                    } catch (ex: RestClientException) {
                        loadError = true
                        null
                    }
                }

                val merchantName = if (paymentForRefund != null) {
                    try {
                        pgMainApiClient.getMerchant(paymentForRefund.merchantId)?.name ?: paymentForRefund.merchantId.toString()
                    } catch (ex: RestClientException) {
                        loadError = true
                        paymentForRefund.merchantId.toString()
                    }
                } else {
                    "-"
                }
                model.addAttribute(
                    "payment",
                    mapOf(
                        "id" to (refund.id?.toString() ?: transactionId),
                        "merchant" to merchantName,
                        "amount" to formatAmount(refund.refundAmount),
                        "method" to (paymentForRefund?.paymentMethod ?: "-"),
                        "customer" to (paymentForRefund?.orderId ?: "-"),
                        "status" to formatRefundStatus(refund.status),
                        "statusClass" to refundStatusClass(refund.status),
                        "events" to buildCombinedEvents(paymentForRefund, refund, paymentId)
                    )
                )
            } else {
                loadError = true
                model.addAttribute(
                    "payment",
                    mapOf(
                        "id" to transactionId,
                        "merchant" to "-",
                        "amount" to "-",
                        "method" to "-",
                        "customer" to "-",
                        "status" to "-",
                        "statusClass" to "warning",
                        "events" to emptyList<Map<String, String>>()
                    )
                )
            }
        }

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

        model.addAttribute(
            "transactionStatusOptions",
            PaymentStatus.values().map { it.name } + RefundStatus.values().map { it.name }
        )

        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)

        val now = Instant.now()

        val parsedFromUtc = fromUtc
            ?.let { LocalDateTime.parse(it).atZone(displayZone).toInstant() }
        val parsedToUtc = toUtc
            ?.let { LocalDateTime.parse(it).atZone(displayZone).toInstant() }

        val resolvedToUtc = parsedToUtc ?: now
        val resolvedFromUtc = parsedFromUtc ?: now.minus(Duration.ofHours(24))

        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
            .withZone(displayZone)
        model.addAttribute("fromUtcLocal", inputFormatter.format(resolvedFromUtc))
        model.addAttribute("toUtcLocal", inputFormatter.format(resolvedToUtc))

        val parsedHeadquartersId = headquartersId?.takeIf { it.isNotBlank() }?.let(UUID::fromString)
        val parsedMerchantId = merchantId?.takeIf { it.isNotBlank() }?.let(UUID::fromString)

        val jobPage = paymentExportJobService.listJobs(
            pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "queuedAt")),
            fromUtc = resolvedFromUtc,
            toUtc = resolvedToUtc,
            headquartersId = parsedHeadquartersId,
            merchantId = parsedMerchantId,
            status = status
        )

        val exportJobs = jobPage.content

        val headquartersNameById = exportJobs.asSequence()
            .mapNotNull { it.headquartersId }
            .distinct()
            .toList()
            .let { ids ->
                if (ids.isEmpty()) {
                    emptyMap()
                } else {
                    headquartersRepository.findAllById(ids)
                        .mapNotNull { hq ->
                            val id = hq.id ?: return@mapNotNull null
                            id to hq.name
                        }
                        .toMap()
                }
            }

        model.addAttribute(
            "exportJobs",
            exportJobs.map { job ->
                mapOf(
                    "jobId" to job.jobId,
                    "range" to "${formatInstant(job.fromUtc)} — ${formatInstant(job.toUtc)}",
                    "headquarters" to (job.headquartersId?.let { id -> headquartersNameById[id] ?: id.toString() } ?: "All HQ"),
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

    private fun buildPaymentEvents(payment: PaymentResponse): List<Map<String, String>> {

        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS z")
            .withZone(displayZone)
        fun fmtTime(instant: Instant): String = timeFormatter.format(instant)

        val events = mutableListOf<Map<String, String>>()

        payment.completedAt?.let {
            val completedTitle = if (payment.status == PaymentStatus.PAYMENT_CANCELLED) "Cancelled" else "Completed"
            events.add(
                mapOf(
                    "time" to fmtTime(it),
                    "title" to "Payment $completedTitle",
                    "note" to "-"
                )
            )
        }

        if (payment.status == PaymentStatus.PAYMENT_FAILED) {
            val failTime = payment.processedAt
                ?.plusMillis(1)
                ?: payment.completedAt
                ?: payment.requestedAt
            events.add(
                mapOf(
                    "time" to fmtTime(failTime),
                    "title" to "Payment Failed",
                    "note" to (payment.failureReason ?: "-")
                )
            )
        }

        payment.processedAt?.let {
            events.add(
                mapOf(
                    "time" to fmtTime(it),
                    "title" to "Payment Processed",
                    "note" to "-"
                )
            )
        }

        events.add(
            mapOf(
                "time" to fmtTime(payment.requestedAt),
                "title" to "Payment Requested",
                "note" to "-"
            )
        )

        return events
    }

    private fun buildRefundEvents(refund: com.example.pgdemo.common.domain.entity.RefundTransaction, paymentId: String): List<Map<String, String>> {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS z")
            .withZone(displayZone)
        fun fmtTime(instant: Instant): String = timeFormatter.format(instant)

        val events = mutableListOf<Map<String, String>>()

        refund.completedAt?.let {
            events.add(
                mapOf(
                    "time" to fmtTime(it),
                    "title" to "Refund Completed",
                    "note" to paymentId
                )
            )
        }

        if (refund.status == RefundStatus.REFUND_FAILED) {
            val failBase = refund.processedAt ?: refund.completedAt ?: refund.requestedAt
            val failTime = failBase.plusMillis(1)
            val reason = refund.failureReason ?: "-"
            events.add(
                mapOf(
                    "time" to fmtTime(failTime),
                    "title" to "Refund Failed",
                    "note" to "$reason | $paymentId"
                )
            )
        }

        refund.processedAt?.let {
            events.add(
                mapOf(
                    "time" to fmtTime(it),
                    "title" to "Refund Processed",
                    "note" to paymentId
                )
            )
        }

        events.add(
            mapOf(
                "time" to fmtTime(refund.requestedAt),
                "title" to "Refund Requested",
                "note" to paymentId
            )
        )

        return events
    }

    private fun buildCombinedEvents(
        payment: PaymentResponse?,
        refund: com.example.pgdemo.common.domain.entity.RefundTransaction,
        paymentId: String
    ): List<Map<String, String>> {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS z")
            .withZone(displayZone)
        fun fmtTime(instant: Instant): String = timeFormatter.format(instant)

        val paymentEvents = if (payment == null) {
            emptyList()
        } else {
            buildPaymentEvents(payment)
        }

        val refundEvents = mutableListOf<Map<String, String>>()

        refund.completedAt?.let {
            refundEvents.add(
                mapOf(
                    "time" to fmtTime(it),
                    "title" to "Refund Completed",
                    "note" to "-"
                )
            )
        }

        if (refund.status == RefundStatus.REFUND_FAILED) {
            val failBase = refund.processedAt ?: refund.completedAt ?: refund.requestedAt
            val failTime = failBase.plusMillis(1)
            val reason = refund.failureReason ?: "-"
            refundEvents.add(
                mapOf(
                    "time" to fmtTime(failTime),
                    "title" to "Refund Failed",
                    "note" to "reason: $reason | -"
                )
            )
        }

        refund.processedAt?.let {
            refundEvents.add(
                mapOf(
                    "time" to fmtTime(it),
                    "title" to "Refund Processed",
                    "note" to "-"
                )
            )
        }

        refundEvents.add(
            mapOf(
                "time" to fmtTime(refund.requestedAt),
                "title" to "Refund Requested",
                "note" to "payment id: $paymentId"
            )
        )

        return if (refund.status == RefundStatus.REFUND_COMPLETED) {
            refundEvents + paymentEvents
        } else {
            refundEvents + paymentEvents
        }
    }

    private fun formatAmount(amount: Long): String {
        val formatted = NumberFormat.getNumberInstance().format(amount.toDouble() / 100)
        return "\$$formatted"
    }

    private fun formatInstant(instant: Instant?): String {
        if (instant == null) {
            return "-"
        }
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(displayZone)
        return formatter.format(instant)
    }

    private fun formatPaymentStatus(status: PaymentStatus): String {
        return when (status) {
            PaymentStatus.PAYMENT_PENDING -> "Payment Pending"
            PaymentStatus.PAYMENT_PROCESSING -> "Payment Processing"
            PaymentStatus.PAYMENT_COMPLETED -> "Payment Completed"
            PaymentStatus.PAYMENT_FAILED -> "Payment Failed"
            PaymentStatus.PAYMENT_CANCELLED -> "Payment Cancelled"
        }
    }

    private fun formatRefundStatus(status: RefundStatus): String {
        return when (status) {
            RefundStatus.REFUND_PENDING -> "Refund Pending"
            RefundStatus.REFUND_PROCESSING -> "Refund Processing"
            RefundStatus.REFUND_COMPLETED -> "Refund Completed"
            RefundStatus.REFUND_FAILED -> "Refund Failed"
        }
    }

    private fun paymentStatusClass(status: PaymentStatus): String {
        return when (status) {
            PaymentStatus.PAYMENT_COMPLETED -> "success"
            PaymentStatus.PAYMENT_FAILED, PaymentStatus.PAYMENT_CANCELLED -> "danger"
            PaymentStatus.PAYMENT_PENDING, PaymentStatus.PAYMENT_PROCESSING -> "warning"
        }
    }

    private fun refundStatusClass(status: RefundStatus): String {
        return when (status) {
            RefundStatus.REFUND_COMPLETED -> "success"
            RefundStatus.REFUND_FAILED -> "danger"
            RefundStatus.REFUND_PENDING, RefundStatus.REFUND_PROCESSING -> "warning"
        }
    }
}
