package com.example.pgdemo.admin.export

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque
import org.springframework.stereotype.Component

data class PaymentExportHistoryItem(
    val id: String,
    val range: String,
    val requestedBy: String,
    val status: String,
    val statusClass: String,
    val queuedAt: Instant,
    val downloadFilename: String
)

@Component
class ExportHistoryStore {
    private val items = ConcurrentLinkedDeque<PaymentExportHistoryItem>()

    fun recordPaymentExport(
        range: String,
        requestedBy: String,
        status: String,
        statusClass: String,
        queuedAt: Instant,
        downloadFilename: String
    ): PaymentExportHistoryItem {
        val item = PaymentExportHistoryItem(
            id = UUID.randomUUID().toString(),
            range = range,
            requestedBy = requestedBy,
            status = status,
            statusClass = statusClass,
            queuedAt = queuedAt,
            downloadFilename = downloadFilename
        )
        items.addFirst(item)
        while (items.size > 200) {
            items.pollLast()
        }
        return item
    }

    fun listPaymentExports(limit: Int = 50): List<PaymentExportHistoryItem> {
        return items.asSequence().take(limit).toList()
    }
}
