package com.example.pgdemo.main.service

import com.example.pgdemo.common.domain.entity.RefundTransaction
import com.example.pgdemo.common.domain.enum.RefundStatus
import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import com.example.pgdemo.main.dto.RefundRequest
import com.example.pgdemo.main.dto.RefundResponse
import com.example.pgdemo.main.exception.ResourceNotFoundException
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RefundService(
    private val refundTransactionRepository: RefundTransactionRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository
) {
    @Transactional
    fun requestRefund(paymentId: UUID, request: RefundRequest): RefundResponse {
        val payment = paymentTransactionRepository.findById(paymentId)
            .orElseThrow { ResourceNotFoundException("Payment not found") }

        val refundAmount = request.refundAmount ?: throw IllegalArgumentException("refundAmount is required")
        val now = Instant.now()
        val refund = RefundTransaction().apply {
            this.payment = payment
            this.refundAmount = refundAmount
            refundReason = request.refundReason
            status = RefundStatus.REFUND_COMPLETED
            requestedAt = now
            processedAt = now
            completedAt = now
        }

        return refundTransactionRepository.save(refund).toResponse()
    }
}

private fun RefundTransaction.toResponse(): RefundResponse {
    val refundId = id ?: throw IllegalStateException("Refund id is missing")
    val paymentId = payment.id ?: throw IllegalStateException("Payment id is missing")

    return RefundResponse(
        id = refundId,
        paymentId = paymentId,
        refundAmount = refundAmount,
        refundReason = refundReason,
        status = status,
        requestedAt = requestedAt,
        processedAt = processedAt,
        completedAt = completedAt,
        failureReason = failureReason
    )
}
