package com.example.pgdemo.main.batch

import com.example.pgdemo.common.domain.document.PaymentExportView
import java.time.Instant
import java.util.UUID
import org.bson.types.ObjectId
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexOperations
import org.springframework.stereotype.Service

@Service
class PaymentExportViewSyncStateStore(
    private val mongoTemplate: MongoTemplate
) {
    private val stateId = "payment_export_view"
    private val schemaVersion = 2

    fun getPaymentLastSyncCursor(): Pair<Instant, UUID?> {
        val s = loadOrInitState()
        return s.paymentLastSyncUpdatedAt to s.paymentLastSyncId
    }

    fun getRefundLastSyncCursor(): Pair<Instant, UUID?> {
        val s = loadOrInitState()
        return s.refundLastSyncUpdatedAt to s.refundLastSyncId
    }

    fun updatePaymentLastSyncCursor(updatedAt: Instant, id: UUID) {
        val current = loadOrInitState()
        if (isAfterCursor(updatedAt, id, current.paymentLastSyncUpdatedAt, current.paymentLastSyncId)) {
            saveState(
                current.copy(
                    paymentLastSyncUpdatedAt = updatedAt,
                    paymentLastSyncId = id,
                    updatedAt = Instant.now()
                )
            )
        }
    }

    fun updateRefundLastSyncCursor(updatedAt: Instant, id: UUID) {
        val current = loadOrInitState()
        if (isAfterCursor(updatedAt, id, current.refundLastSyncUpdatedAt, current.refundLastSyncId)) {
            saveState(
                current.copy(
                    refundLastSyncUpdatedAt = updatedAt,
                    refundLastSyncId = id,
                    updatedAt = Instant.now()
                )
            )
        }
    }

    fun reset(dropViewCollection: Boolean) {
        if (dropViewCollection) {
            mongoTemplate.dropCollection(PaymentExportView::class.java)
        }
        ensureIndexes()
        saveState(
            PaymentExportViewSyncState(
                id = stateId,
                schemaVersion = schemaVersion,
                paymentLastSyncUpdatedAt = Instant.EPOCH,
                paymentLastSyncId = null,
                refundLastSyncUpdatedAt = Instant.EPOCH,
                refundLastSyncId = null,
                updatedAt = Instant.now()
            )
        )
    }

    fun migrateIfNeeded() {
        val existing = mongoTemplate.findById(stateId, PaymentExportViewSyncState::class.java)
        val legacyViewDetected = isLegacyObjectIdCollection()

        if (existing == null || existing.schemaVersion != schemaVersion || legacyViewDetected) {
            reset(dropViewCollection = true)
        } else {
            ensureIndexes()
        }
    }

    private fun loadOrInitState(): PaymentExportViewSyncState {
        val existing = mongoTemplate.findById(stateId, PaymentExportViewSyncState::class.java)
        if (existing != null) {
            return existing
        }

        val created = PaymentExportViewSyncState(
            id = stateId,
            schemaVersion = schemaVersion,
            paymentLastSyncUpdatedAt = Instant.EPOCH,
            paymentLastSyncId = null,
            refundLastSyncUpdatedAt = Instant.EPOCH,
            refundLastSyncId = null,
            updatedAt = Instant.now()
        )
        return mongoTemplate.save(created)
    }

    private fun saveState(state: PaymentExportViewSyncState): PaymentExportViewSyncState {
        return mongoTemplate.save(state)
    }

    private fun isAfterCursor(
        updatedAt: Instant,
        id: UUID,
        cursorUpdatedAt: Instant,
        cursorId: UUID?
    ): Boolean {
        if (updatedAt.isAfter(cursorUpdatedAt)) {
            return true
        }
        if (updatedAt != cursorUpdatedAt) {
            return false
        }
        if (cursorId == null) {
            return true
        }
        return compareUuid(id, cursorId) > 0
    }

    private fun compareUuid(a: UUID, b: UUID): Int {
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

    private fun uuidToBytes(u: UUID): ByteArray {
        val bytes = ByteArray(16)
        var msb = u.mostSignificantBits
        var lsb = u.leastSignificantBits
        for (i in 0..7) {
            bytes[i] = (msb ushr (8 * (7 - i))).toByte()
        }
        for (i in 8..15) {
            bytes[i] = (lsb ushr (8 * (15 - i))).toByte()
        }
        return bytes
    }

    private fun isLegacyObjectIdCollection(): Boolean {
        val coll = mongoTemplate.getCollection("payment_export_view")
        val doc = coll.find().projection(org.bson.Document("_id", 1)).limit(1).first() ?: return false
        val id = doc.get("_id")
        return id is ObjectId
    }

    private fun ensureIndexes() {
        val ops: IndexOperations = mongoTemplate.indexOps(PaymentExportView::class.java)
        ops.ensureIndex(Index().on("headquartersId", org.springframework.data.domain.Sort.Direction.ASC))
        ops.ensureIndex(Index().on("merchantId", org.springframework.data.domain.Sort.Direction.ASC))
        ops.ensureIndex(Index().on("paymentDate", org.springframework.data.domain.Sort.Direction.DESC))
        ops.ensureIndex(Index().on("refundDate", org.springframework.data.domain.Sort.Direction.DESC))
        ops.ensureIndex(Index().on("paymentStatus", org.springframework.data.domain.Sort.Direction.ASC))
        ops.ensureIndex(Index().on("refundStatus", org.springframework.data.domain.Sort.Direction.ASC))
        ops.ensureIndex(Index().on("exportPeriod", org.springframework.data.domain.Sort.Direction.ASC))
        ops.ensureIndex(Index().on("syncedAt", org.springframework.data.domain.Sort.Direction.DESC))
        ops.ensureIndex(Index().on("refundId", org.springframework.data.domain.Sort.Direction.ASC))
    }
}
