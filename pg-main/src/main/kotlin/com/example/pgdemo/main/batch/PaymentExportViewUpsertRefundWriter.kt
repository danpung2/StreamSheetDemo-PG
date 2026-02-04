package com.example.pgdemo.main.batch

import java.time.Instant
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component

@Component
@StepScope
class PaymentExportViewUpsertRefundWriter(
    private val mongoTemplate: MongoTemplate,
    private val syncStateStore: PaymentExportViewSyncStateStore
) : ItemWriter<PaymentExportViewUpsertItem>, StepExecutionListener {

    private var maxUpdatedAt: Instant? = null
    private var maxId: java.util.UUID? = null

    override fun beforeStep(stepExecution: StepExecution) {
        maxUpdatedAt = null
        maxId = null
    }

    override fun write(chunk: Chunk<out PaymentExportViewUpsertItem>) {
        chunk.forEach { item ->
            mongoTemplate.save(item.view, "payment_export_view")
            val updatedAt = item.sourceUpdatedAt
            val id = item.sourceId
            val currentMax = maxUpdatedAt
            val currentMaxId = maxId
            if (currentMax == null || updatedAt.isAfter(currentMax) || (updatedAt == currentMax && (currentMaxId == null || compareUuid(id, currentMaxId) > 0))) {
                maxUpdatedAt = updatedAt
                maxId = id
            }
        }
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus {
        if (stepExecution.exitStatus.exitCode == ExitStatus.COMPLETED.exitCode) {
            val max = maxUpdatedAt
            val id = maxId
            if (max != null && id != null) {
                syncStateStore.updateRefundLastSyncCursor(max, id)
            }
        }
        return stepExecution.exitStatus
    }

    private fun compareUuid(a: java.util.UUID, b: java.util.UUID): Int {
        val ab = uuidToBytes(a)
        val bb = uuidToBytes(b)
        for (i in 0 until 16) {
            val ai = ab[i].toInt() and 0xff
            val bi = bb[i].toInt() and 0xff
            if (ai != bi) {
                return ai - bi
            }
        }
        return 0
    }

    private fun uuidToBytes(u: java.util.UUID): ByteArray {
        val bytes = ByteArray(16)
        val msb = u.mostSignificantBits
        val lsb = u.leastSignificantBits
        for (i in 0..7) {
            bytes[i] = (msb ushr (8 * (7 - i))).toByte()
        }
        for (i in 8..15) {
            bytes[i] = (lsb ushr (8 * (15 - i))).toByte()
        }
        return bytes
    }
}
