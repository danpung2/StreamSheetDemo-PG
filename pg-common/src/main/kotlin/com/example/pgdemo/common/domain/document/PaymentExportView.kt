package com.example.pgdemo.common.domain.document

import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.index.Indexed

@Document("payment_export_view")
data class PaymentExportView(
    @Id
    val transactionId: UUID,

    @Indexed
    val headquartersId: UUID,
    val headquartersCode: String,
    val headquartersName: String,

    @Indexed
    val merchantId: UUID,
    val merchantCode: String,
    val merchantName: String,
    val storeType: String,
    val businessType: String,
    val orderId: String,
    val amount: Long,
    val paymentMethod: String,

    @Indexed
    val paymentStatus: String,

    @Indexed
    val refundId: UUID?,
    val refundAmount: Long?,

    @Indexed
    val refundStatus: String?,
    val refundReason: String?,

    @Indexed
    val paymentDate: Instant,

    @Indexed
    val refundDate: Instant?,

    @Indexed
    val syncedAt: Instant,

    @Indexed
    val exportPeriod: String
)
