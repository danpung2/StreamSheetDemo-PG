package com.example.pgdemo.main.service

import com.example.pgdemo.common.domain.enum.RefundStatus
import com.example.pgdemo.common.domain.entity.RefundTransaction
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import com.example.pgdemo.main.dto.RefundFailRequest
import com.example.pgdemo.main.dto.RefundResponse
import com.example.pgdemo.main.exception.ResourceNotFoundException
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RefundTransitionService(
    private val refundTransactionRepository: RefundTransactionRepository
) {
    private val processFrom = listOf(RefundStatus.REFUND_PENDING)
    private val terminalFrom = listOf(RefundStatus.REFUND_PROCESSING)

    private fun getRefundOrThrow(id: UUID): RefundTransaction {
        return refundTransactionRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Refund not found") }
    }

    @Transactional
    fun processRefund(id: UUID): RefundResponse {
        val refund = getRefundOrThrow(id)
        if (refund.status != RefundStatus.REFUND_PENDING) {
            return refund.toResponse()
        }

        val now = Instant.now()
        refundTransactionRepository.transitionToProcessing(
            id = id,
            fromStatuses = processFrom,
            toStatus = RefundStatus.REFUND_PROCESSING,
            processedAt = now
        )
        return getRefundOrThrow(id).toResponse()
    }

    @Transactional
    fun completeRefund(id: UUID): RefundResponse {
        val refund = getRefundOrThrow(id)
        if (refund.status == RefundStatus.REFUND_COMPLETED) {
            return refund.toResponse()
        }
        if (refund.status != RefundStatus.REFUND_PROCESSING) {
            throw IllegalStateException("Refund must be processing to complete")
        }

        val now = Instant.now()
        val updated = refundTransactionRepository.transitionToCompletedAt(
            id = id,
            fromStatuses = terminalFrom,
            toStatus = RefundStatus.REFUND_COMPLETED,
            completedAt = now
        )
        val after = getRefundOrThrow(id)
        if (updated == 0 && after.status != RefundStatus.REFUND_COMPLETED) {
            throw IllegalStateException("Refund transition failed")
        }
        return after.toResponse()
    }

    @Transactional
    fun failRefund(id: UUID, request: RefundFailRequest): RefundResponse {
        val refund = getRefundOrThrow(id)
        if (refund.status == RefundStatus.REFUND_FAILED) {
            return refund.toResponse()
        }
        if (refund.status != RefundStatus.REFUND_PROCESSING) {
            throw IllegalStateException("Refund must be processing to fail")
        }

        val now = Instant.now()
        val updated = refundTransactionRepository.transitionToFailed(
            id = id,
            fromStatuses = terminalFrom,
            toStatus = RefundStatus.REFUND_FAILED,
            processedAt = now,
            failureReason = request.failureReason
        )
        val after = getRefundOrThrow(id)
        if (updated == 0 && after.status != RefundStatus.REFUND_FAILED) {
            throw IllegalStateException("Refund transition failed")
        }
        return after.toResponse()
    }
}
