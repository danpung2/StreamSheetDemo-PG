package com.example.pgdemo.main.batch

import com.example.pgdemo.common.domain.document.PaymentExportView
import com.example.pgdemo.common.domain.entity.PaymentTransaction
import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.data.RepositoryItemReader
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate

@Configuration
class PaymentViewSyncJob(
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val refundTransactionRepository: RefundTransactionRepository,
    private val mongoTemplate: MongoTemplate
) {
    @Bean
    fun paymentTransactionReader(): RepositoryItemReader<PaymentTransaction> {
        return RepositoryItemReaderBuilder<PaymentTransaction>()
            .name("paymentTransactionReader")
            .repository(paymentTransactionRepository)
            .methodName("findAll")
            .sorts(mapOf("updatedAt" to Sort.Direction.ASC))
            .pageSize(100)
            .build()
    }

    @Bean
    fun paymentExportViewProcessor(): ItemProcessor<PaymentTransaction, PaymentExportView> {
        return ItemProcessor { payment ->
            val paymentId = payment.id ?: throw IllegalStateException("Payment id is missing")
            val merchant = payment.merchant
            val merchantId = merchant.id ?: throw IllegalStateException("Merchant id is missing")
            val headquarters = merchant.headquarters ?: throw IllegalStateException("Headquarters is missing")
            val headquartersId = headquarters.id ?: throw IllegalStateException("Headquarters id is missing")

            val refund = refundTransactionRepository.findFirstByPaymentId(paymentId)
            val paymentDate = payment.completedAt ?: payment.requestedAt
            val exportPeriod = YearMonth.from(paymentDate.atZone(ZoneOffset.UTC))
                .format(DateTimeFormatter.ofPattern("yyyy-MM"))

            PaymentExportView(
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
            )
        }
    }

    @Bean
    fun paymentExportViewWriter(): ItemWriter<PaymentExportView> {
        return ItemWriter { items ->
            items.forEach { mongoTemplate.save(it) }
        }
    }
}
