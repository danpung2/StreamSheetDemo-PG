package com.example.pgdemo.admin.view

import com.example.pgdemo.admin.tenant.TenantContext
import com.example.pgdemo.common.domain.enum.PaymentStatus
import com.example.pgdemo.common.domain.enum.RefundStatus
import com.example.pgdemo.common.domain.enum.TenantType
import com.example.pgdemo.common.domain.repository.HeadquartersRepository
import com.example.pgdemo.common.domain.repository.MerchantRepository
import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import com.example.pgdemo.common.domain.repository.spec.MerchantSpecifications
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
@RequestMapping("/admin")
class MerchantViewController(
    private val merchantRepository: MerchantRepository,
    private val headquartersRepository: HeadquartersRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val refundTransactionRepository: RefundTransactionRepository
) {
    private val displayZone = ZoneId.systemDefault()
    private val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        .withZone(displayZone)

    @GetMapping("/merchants")
    fun merchants(
        @RequestParam(name = "page", defaultValue = "0") page: Int,
        @RequestParam(name = "size", defaultValue = "50") size: Int,
        @RequestParam(name = "headquartersId", required = false) headquartersId: String?,
        @RequestParam(name = "merchantQuery", required = false) merchantQuery: String?,
        @RequestParam(name = "status", required = false) status: String?,
        model: Model
    ): String {
        model.addAttribute("pageTitle", "Merchants")

        val tenantInfo = TenantContext.require()
        val safePage = page.coerceAtLeast(0)
        val safeSize = when (size) {
            50, 100, 200 -> size
            else -> 50
        }

        val parsedHeadquartersId = headquartersId?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val normalizedQuery = merchantQuery?.trim()?.takeIf { it.isNotBlank() }
        val normalizedStatus = status?.trim()?.takeIf { it.isNotBlank() }

        val resolvedHeadquartersId = when (tenantInfo.tenantType) {
            TenantType.OPERATOR -> parsedHeadquartersId
            TenantType.HEADQUARTERS -> tenantInfo.tenantId
            TenantType.MERCHANT -> null
        }

        val resolvedQuery = when (tenantInfo.tenantType) {
            TenantType.MERCHANT -> null
            else -> normalizedQuery
        }

        val resolvedStatus = normalizedStatus

        val pageable = PageRequest.of(
            safePage,
            safeSize,
            Sort.by(Sort.Direction.DESC, "updatedAt")
        )

        val merchantPage = when (tenantInfo.tenantType) {
            TenantType.MERCHANT -> {
                val merchantId = tenantInfo.tenantId
                val items = if (merchantId != null) {
                    merchantRepository.findById(merchantId).map { listOf(it) }.orElse(emptyList())
                } else {
                    emptyList()
                }
                org.springframework.data.domain.PageImpl(items, PageRequest.of(0, 50), items.size.toLong())
            }
            else -> {
                val spec = MerchantSpecifications.search(
                    headquartersId = resolvedHeadquartersId,
                    status = resolvedStatus,
                    merchantQuery = resolvedQuery
                )
                merchantRepository.findAll(spec, pageable)
            }
        }

        val riskFrom = Instant.now().minus(Duration.ofDays(7))
        val riskTo = Instant.now()
        val minCountForTier = 20L

        model.addAttribute(
            "merchants",
            merchantPage.content.map { merchant ->
                val merchantId = merchant.id ?: throw IllegalStateException("Merchant id is missing")
                val totalCount = paymentTransactionRepository.countByMerchantIdAndRequestedAtBetween(merchantId, riskFrom, riskTo)
                val failedCount = paymentTransactionRepository.countByMerchantIdAndRequestedAtBetweenAndStatus(
                    merchantId,
                    riskFrom,
                    riskTo,
                    PaymentStatus.PAYMENT_FAILED
                )
                val paymentAmountSum = paymentTransactionRepository.sumAmountByMerchantIdAndDateRange(
                    merchantId,
                    riskFrom,
                    riskTo,
                    PaymentStatus.PAYMENT_COMPLETED
                )
                val refundAmountSum = refundTransactionRepository.sumRefundAmountByMerchantIdAndDateRange(
                    merchantId,
                    riskFrom,
                    riskTo,
                    RefundStatus.REFUND_COMPLETED
                )

                val riskTier = calculateRiskTier(
                    totalCount = totalCount,
                    failedCount = failedCount,
                    paymentAmountSum = paymentAmountSum,
                    refundAmountSum = refundAmountSum,
                    minCount = minCountForTier
                )

                mapOf(
                    "name" to merchant.name,
                    "code" to merchant.merchantCode,
                    "industry" to formatLabel(merchant.businessType.name),
                    "storeType" to formatLabel(merchant.storeType.name),
                    "status" to merchant.status,
                    "riskTier" to riskTier,
                    "updatedAt" to displayFormatter.format(merchant.updatedAt)
                )
            }
        )

        model.addAttribute("pageNumber", merchantPage.number)
        model.addAttribute("pageSize", merchantPage.size)
        model.addAttribute("totalElements", merchantPage.totalElements)
        model.addAttribute("totalPages", merchantPage.totalPages)
        model.addAttribute("hasPrev", merchantPage.hasPrevious())
        model.addAttribute("hasNext", merchantPage.hasNext())
        model.addAttribute("prevPage", (merchantPage.number - 1).coerceAtLeast(0))
        model.addAttribute("nextPage", merchantPage.number + 1)

        model.addAttribute("headquartersId", resolvedHeadquartersId?.toString() ?: "")
        model.addAttribute("merchantQuery", resolvedQuery ?: "")
        model.addAttribute("status", resolvedStatus ?: "")

        val headquartersOptions = when (tenantInfo.tenantType) {
            TenantType.OPERATOR -> {
                headquartersRepository.findByStatus("ACTIVE")
                    .take(200)
                    .mapNotNull { hq ->
                        val id = hq.id
                        if (id == null) null else mapOf("id" to id.toString(), "name" to hq.name)
                    }
            }
            else -> emptyList()
        }
        model.addAttribute("headquartersOptions", headquartersOptions)

        model.addAttribute("statusOptions", listOf("ACTIVE", "INACTIVE", "SUSPENDED"))
        model.addAttribute("sizeOptions", listOf(50, 100, 200))

        return "merchants/list"
    }

    private fun calculateRiskTier(
        totalCount: Long,
        failedCount: Long,
        paymentAmountSum: Long,
        refundAmountSum: Long,
        minCount: Long
    ): String {
        if (totalCount < minCount) {
            return "N/A"
        }
        val failureRate = if (totalCount <= 0L) 0.0 else failedCount.toDouble() / totalCount.toDouble()
        val refundRate = if (paymentAmountSum <= 0L) 0.0 else refundAmountSum.toDouble() / paymentAmountSum.toDouble()

        if (failureRate >= 0.03 || refundRate >= 0.03) {
            return "HIGH"
        }
        if (failureRate >= 0.01 || refundRate >= 0.01) {
            return "MEDIUM"
        }
        return "LOW"
    }

    private fun formatLabel(value: String): String {
        return value.lowercase()
            .split("_", " ")
            .joinToString(" ") { segment ->
                segment.replaceFirstChar { char -> char.uppercase() }
            }
    }
}
