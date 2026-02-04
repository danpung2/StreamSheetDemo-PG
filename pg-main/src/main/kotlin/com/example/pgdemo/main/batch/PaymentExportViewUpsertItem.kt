package com.example.pgdemo.main.batch

import com.example.pgdemo.common.domain.document.PaymentExportView
import java.time.Instant
import java.util.UUID

data class PaymentExportViewUpsertItem(
    val view: PaymentExportView,
    val sourceUpdatedAt: Instant,
    val sourceId: UUID
)
