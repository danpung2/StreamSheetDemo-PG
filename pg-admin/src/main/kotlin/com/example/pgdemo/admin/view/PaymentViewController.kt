package com.example.pgdemo.admin.view

import com.example.pgdemo.admin.client.PgMainApiClient
import com.example.pgdemo.admin.client.PaymentResponse
import com.example.pgdemo.admin.export.ExportHistoryStore
import com.example.pgdemo.common.domain.enum.PaymentStatus
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
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
    private val exportHistoryStore: ExportHistoryStore
) {

    @GetMapping("/payments")
    fun payments(
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "20") size: Int,
        model: Model
    ): String {
        model.addAttribute("pageTitle", "Payments")
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)
        var loadError = false
        val paymentPage = try {
            pgMainApiClient.listPayments(safePage, safeSize)
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

        val merchantsById = try {
            pgMainApiClient.listMerchants(0, 200)?.content.orEmpty().associateBy { it.id }
        } catch (ex: RestClientException) {
            loadError = true
            emptyMap()
        }
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
    fun paymentExports(model: Model): String {
        model.addAttribute("pageTitle", "Exports")
        model.addAttribute(
            "exportJobs",
            exportHistoryStore.listPaymentExports(50).map { item ->
                mapOf(
                    "id" to item.id,
                    "range" to item.range,
                    "requestedBy" to item.requestedBy,
                    "status" to item.status,
                    "statusClass" to item.statusClass,
                    "queuedAt" to formatInstant(item.queuedAt)
                )
            }
        )
        return "exports/payments"
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
