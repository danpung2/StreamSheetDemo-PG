package com.example.pgdemo.main.batch

import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import java.time.Instant
import java.util.UUID
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class PaymentExportViewSyncRunner(
    private val lockService: PostgresAdvisoryLockService,
    private val syncStateStore: PaymentExportViewSyncStateStore,
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val refundTransactionRepository: RefundTransactionRepository,
    private val jobLauncher: JobLauncher,
    @Qualifier("paymentViewSyncJob")
    private val paymentViewSyncJob: Job
) {
    private data class Cursor(
        val updatedAt: Instant,
        val id: UUID?
    )

    private val lockKey = 90502001L

    fun runScheduledIfNeeded(): Boolean {
        val paymentCursor = paymentCursor()
        val refundCursor = refundCursor()
        if (!hasAnyNewData(paymentCursor, refundCursor)) {
            return false
        }

        return lockService.withTryLock(lockKey) {
            syncStateStore.migrateIfNeeded()
            val paymentCursorLocked = paymentCursor()
            val refundCursorLocked = refundCursor()
            if (!hasAnyNewData(paymentCursorLocked, refundCursorLocked)) {
                return@withTryLock false
            }

            jobLauncher.run(paymentViewSyncJob, buildJobParameters(paymentCursorLocked, refundCursorLocked))
            true
        } ?: false
    }

    fun runOnStartup(): Boolean {
        return lockService.withTryLock(lockKey) {
            syncStateStore.migrateIfNeeded()
            val paymentCursorLocked = paymentCursor()
            val refundCursorLocked = refundCursor()
            if (!hasAnyNewData(paymentCursorLocked, refundCursorLocked)) {
                return@withTryLock false
            }

            jobLauncher.run(paymentViewSyncJob, buildJobParameters(paymentCursorLocked, refundCursorLocked))
            true
        } ?: false
    }

    private fun paymentCursor(): Cursor {
        val (updatedAt, id) = syncStateStore.getPaymentLastSyncCursor()
        return Cursor(updatedAt = updatedAt, id = id)
    }

    private fun refundCursor(): Cursor {
        val (updatedAt, id) = syncStateStore.getRefundLastSyncCursor()
        return Cursor(updatedAt = updatedAt, id = id)
    }

    private fun hasAnyNewData(paymentCursor: Cursor, refundCursor: Cursor): Boolean {
        return hasNewPayments(paymentCursor) || hasNewRefunds(refundCursor)
    }

    private fun hasNewPayments(cursor: Cursor): Boolean {
        val pageable = PageRequest.of(0, 1)
        val page = if (cursor.id == null) {
            paymentTransactionRepository.findPaymentsForSyncFromTimeInclusive(cursor.updatedAt, pageable)
        } else {
            paymentTransactionRepository.findPaymentsForSyncCursor(cursor.updatedAt, cursor.id, pageable)
        }
        return page.hasContent()
    }

    private fun hasNewRefunds(cursor: Cursor): Boolean {
        val pageable = PageRequest.of(0, 1)
        val page = if (cursor.id == null) {
            refundTransactionRepository.findRefundsForSyncFromTimeInclusive(cursor.updatedAt, pageable)
        } else {
            refundTransactionRepository.findRefundsForSyncCursor(cursor.updatedAt, cursor.id, pageable)
        }
        return page.hasContent()
    }

    private fun buildJobParameters(paymentCursor: Cursor, refundCursor: Cursor): JobParameters {
        return JobParametersBuilder()
            .addLong("run.id", System.currentTimeMillis())
            .addLong("paymentSyncFrom", paymentCursor.updatedAt.toEpochMilli())
            .addString("paymentSyncFromId", paymentCursor.id?.toString() ?: "")
            .addLong("refundSyncFrom", refundCursor.updatedAt.toEpochMilli())
            .addString("refundSyncFromId", refundCursor.id?.toString() ?: "")
            .toJobParameters()
    }
}
