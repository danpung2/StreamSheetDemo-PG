package com.example.pgdemo.common.domain.document

import java.time.Instant
import java.util.UUID
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document("payment_export_view")
data class PaymentExportView(
    @Id
    val id: ObjectId? = null,
    val transactionId: UUID,
    val headquartersId: UUID,
    val headquartersCode: String,
    val headquartersName: String,
    val merchantId: UUID,
    val merchantCode: String,
    val merchantName: String,
    val storeType: String,
    val businessType: String,
    val orderId: String,
    val amount: Long,
    val paymentMethod: String,
    val paymentStatus: String,
    val refundId: UUID?,
    val refundAmount: Long?,
    val refundStatus: String?,
    val refundReason: String?,
    val paymentDate: Instant,
    val refundDate: Instant?,
    val syncedAt: Instant,
    val exportPeriod: String
)
