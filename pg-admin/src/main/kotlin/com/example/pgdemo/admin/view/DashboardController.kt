package com.example.pgdemo.admin.view

import com.example.pgdemo.admin.client.PgMainApiClient
import com.example.pgdemo.common.domain.enum.PaymentStatus
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.client.RestClientException

@Controller
@RequestMapping("/admin")
class DashboardController(
    private val pgMainApiClient: PgMainApiClient
) {

    @GetMapping("", "/dashboard")
    fun dashboard(model: Model): String {
        model.addAttribute("pageTitle", "Dashboard")
        var loadError = false

        val recentPayments = try {
            pgMainApiClient.listPayments(0, 5)?.content.orEmpty()
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

        val totalVolume = recentPayments.sumOf { it.amount }
        val successfulPayments = recentPayments.count { it.status == PaymentStatus.PAYMENT_COMPLETED }
        model.addAttribute(
            "kpis",
            mapOf(
                "totalVolume" to formatAmount(totalVolume),
                "successfulPayments" to successfulPayments.toString()
            )
        )
        model.addAttribute(
            "recentPayments",
            recentPayments.map { payment ->
                val merchantName = merchantsById[payment.merchantId]?.name ?: payment.merchantId.toString()
                mapOf(
                    "id" to payment.id.toString(),
                    "merchant" to merchantName,
                    "amount" to formatAmount(payment.amount),
                    "status" to formatStatus(payment.status),
                    "statusClass" to statusClass(payment.status),
                    "updatedAt" to formatInstant(payment.completedAt ?: payment.processedAt ?: payment.requestedAt)
                )
            }
        )
        if (loadError) {
            model.addAttribute("loadError", true)
        }
        return "dashboard"
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
