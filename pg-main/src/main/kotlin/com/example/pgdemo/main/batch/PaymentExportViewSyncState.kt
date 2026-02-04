package com.example.pgdemo.main.batch

import java.time.Instant
import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document("sync_state")
data class PaymentExportViewSyncState(
    @Id
    val id: String,
    val schemaVersion: Int,
    val paymentLastSyncUpdatedAt: Instant,
    val paymentLastSyncId: UUID?,
    val refundLastSyncUpdatedAt: Instant,
    val refundLastSyncId: UUID?,
    val updatedAt: Instant
)
