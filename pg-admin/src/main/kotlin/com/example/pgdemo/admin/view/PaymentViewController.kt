package com.example.pgdemo.admin.view

import com.example.pgdemo.admin.client.PgMainApiClient
import com.example.pgdemo.common.domain.enum.PaymentStatus
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.RestClientException

@Controller
@RequestMapping("/admin")
class PaymentViewController(
    private val pgMainApiClient: PgMainApiClient
) {

    @GetMapping("/payments")
    fun payments(model: Model): String {
        model.addAttribute("pageTitle", "Payments")
        var loadError = false
        val paymentResponses = try {
            pgMainApiClient.listPayments(0, 20)?.content.orEmpty()
        } catch (ex: RestClientException) {
            loadError = true
            emptyList()
        }

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
                "amount" to formatAmount(payment?.amount ?: 0),
                "method" to (payment?.paymentMethod ?: "-"),
                "customer" to (payment?.orderId ?: "-"),
                "status" to (payment?.status?.let { formatStatus(it) } ?: "Unknown"),
                "statusClass" to (payment?.status?.let { statusClass(it) } ?: "warning"),
                "events" to emptyList<Map<String, String>>()
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
        model.addAttribute("exportJobs", emptyList<Map<String, String>>())
        return "exports/payments"
    }

    private fun formatAmount(amount: Long): String {
        val formatted = NumberFormat.getNumberInstance().format(amount.toDouble() / 100)
        return "\$$formatted"
    }

    private fun formatInstant(instant: Instant?): String {
        if (instant == null) {
            return "-"
        }
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault())
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
