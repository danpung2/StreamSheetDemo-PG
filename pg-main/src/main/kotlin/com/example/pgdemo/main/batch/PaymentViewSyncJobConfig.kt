package com.example.pgdemo.main.batch

import com.example.pgdemo.common.domain.document.PaymentExportView
import com.example.pgdemo.common.domain.entity.PaymentTransaction
import com.example.pgdemo.common.domain.entity.RefundTransaction
import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.data.RepositoryItemReader
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.beans.factory.annotation.Value

@Configuration
class PaymentViewSyncJobConfig(
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val refundTransactionRepository: RefundTransactionRepository,
    private val mongoTemplate: MongoTemplate
) {
    @Bean
    @StepScope
    fun paymentTransactionReader(
        @Value("#{jobParameters['paymentSyncFrom']}") paymentSyncFromEpochMs: Long?
        ,
        @Value("#{jobParameters['paymentSyncFromId']}") paymentSyncFromId: String?
    ): ItemReader<PaymentTransaction> {
        val syncFrom = Instant.ofEpochMilli(paymentSyncFromEpochMs ?: 0L)
        val syncFromId = paymentSyncFromId?.trim()?.takeIf { it.isNotBlank() }?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }

        val methodName = if (syncFromId == null) "findPaymentsForSyncFromTimeInclusive" else "findPaymentsForSyncCursor"
        val args = if (syncFromId == null) listOf(syncFrom) else listOf(syncFrom, syncFromId)
        return RepositoryItemReaderBuilder<PaymentTransaction>()
            .name("paymentTransactionReader")
            .repository(paymentTransactionRepository)
            .methodName(methodName)
            .arguments(args)
            .sorts(mapOf("updatedAt" to Sort.Direction.ASC, "id" to Sort.Direction.ASC))
            .pageSize(100)
            .build()
    }

    @Bean
    @StepScope
    fun refundTransactionReader(
        @Value("#{jobParameters['refundSyncFrom']}") refundSyncFromEpochMs: Long?
        ,
        @Value("#{jobParameters['refundSyncFromId']}") refundSyncFromId: String?
    ): ItemReader<RefundTransaction> {
        val syncFrom = Instant.ofEpochMilli(refundSyncFromEpochMs ?: 0L)
        val syncFromId = refundSyncFromId?.trim()?.takeIf { it.isNotBlank() }?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }

        val methodName = if (syncFromId == null) "findRefundsForSyncFromTimeInclusive" else "findRefundsForSyncCursor"
        val args = if (syncFromId == null) listOf(syncFrom) else listOf(syncFrom, syncFromId)
        return RepositoryItemReaderBuilder<RefundTransaction>()
            .name("refundTransactionReader")
            .repository(refundTransactionRepository)
            .methodName(methodName)
            .arguments(args)
            .sorts(mapOf("updatedAt" to Sort.Direction.ASC, "id" to Sort.Direction.ASC))
            .pageSize(100)
            .build()
    }

    @Bean
    fun paymentExportViewProcessor(): ItemProcessor<PaymentTransaction, PaymentExportViewUpsertItem> {
        return ItemProcessor { payment ->
            val paymentId = payment.id ?: throw IllegalStateException("Payment id is missing")
            val merchant = payment.merchant
            val merchantId = merchant.id ?: throw IllegalStateException("Merchant id is missing")
            val headquarters = merchant.headquarters ?: throw IllegalStateException("Headquarters is missing")
            val headquartersId = headquarters.id ?: throw IllegalStateException("Headquarters id is missing")

            val refund = refundTransactionRepository.findTopByPaymentIdOrderByUpdatedAtDesc(paymentId)
            val paymentDate = payment.completedAt ?: payment.requestedAt
            val exportPeriod = YearMonth.from(paymentDate.atZone(ZoneOffset.UTC))
                .format(DateTimeFormatter.ofPattern("yyyy-MM"))

            PaymentExportViewUpsertItem(
                view = PaymentExportView(
                    transactionId = paymentId,
                    headquartersId = headquartersId,
                    headquartersCode = headquarters.headquartersCode,
                    headquartersName = headquarters.name,
                    merchantId = merchantId,
                    merchantCode = merchant.merchantCode,
                    merchantName = merchant.name,
                    storeType = merchant.storeType.name,
                    businessType = merchant.businessType.name,
                    orderId = payment.orderId,
                    amount = payment.amount,
                    paymentMethod = payment.paymentMethod,
                    paymentStatus = payment.status.name,
                    refundId = refund?.id,
                    refundAmount = refund?.refundAmount,
                    refundStatus = refund?.status?.name,
                    refundReason = refund?.refundReason,
                    paymentDate = paymentDate,
                    refundDate = refund?.completedAt ?: refund?.requestedAt,
                    syncedAt = Instant.now(),
                    exportPeriod = exportPeriod
                ),
                sourceUpdatedAt = payment.updatedAt
                ,
                sourceId = paymentId
            )
        }
    }

    @Bean
    fun refundExportViewProcessor(): ItemProcessor<RefundTransaction, PaymentExportViewUpsertItem> {
        return ItemProcessor { refund ->
            val payment = refund.payment
            val paymentId = payment.id ?: throw IllegalStateException("Payment id is missing")
            val merchant = payment.merchant
            val merchantId = merchant.id ?: throw IllegalStateException("Merchant id is missing")
            val headquarters = merchant.headquarters ?: throw IllegalStateException("Headquarters is missing")
            val headquartersId = headquarters.id ?: throw IllegalStateException("Headquarters id is missing")

            val paymentDate = payment.completedAt ?: payment.requestedAt
            val exportPeriod = YearMonth.from(paymentDate.atZone(ZoneOffset.UTC))
                .format(DateTimeFormatter.ofPattern("yyyy-MM"))

            PaymentExportViewUpsertItem(
                view = PaymentExportView(
                    transactionId = paymentId,
                    headquartersId = headquartersId,
                    headquartersCode = headquarters.headquartersCode,
                    headquartersName = headquarters.name,
                    merchantId = merchantId,
                    merchantCode = merchant.merchantCode,
                    merchantName = merchant.name,
                    storeType = merchant.storeType.name,
                    businessType = merchant.businessType.name,
                    orderId = payment.orderId,
                    amount = payment.amount,
                    paymentMethod = payment.paymentMethod,
                    paymentStatus = payment.status.name,
                    refundId = refund.id,
                    refundAmount = refund.refundAmount,
                    refundStatus = refund.status.name,
                    refundReason = refund.refundReason,
                    paymentDate = paymentDate,
                    refundDate = refund.completedAt ?: refund.requestedAt,
                    syncedAt = Instant.now(),
                    exportPeriod = exportPeriod
                ),
                sourceUpdatedAt = refund.updatedAt
                ,
                sourceId = refund.id ?: throw IllegalStateException("Refund id is missing")
            )
        }
    }

}
