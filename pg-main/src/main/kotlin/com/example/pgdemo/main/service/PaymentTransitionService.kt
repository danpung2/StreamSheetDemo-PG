package com.example.pgdemo.main.service

import com.example.pgdemo.common.domain.enum.PaymentStatus
import com.example.pgdemo.common.domain.entity.PaymentTransaction
import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.main.dto.PaymentFailRequest
import com.example.pgdemo.main.dto.PaymentResponse
import com.example.pgdemo.main.exception.ResourceNotFoundException
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentTransitionService(
    private val paymentTransactionRepository: PaymentTransactionRepository
) {
    private val processFrom = listOf(PaymentStatus.PAYMENT_PENDING)
    private val terminalFrom = listOf(PaymentStatus.PAYMENT_PROCESSING)

    private fun getPaymentOrThrow(id: UUID): PaymentTransaction {
        return paymentTransactionRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Payment not found") }
    }

    @Transactional
    fun processPayment(id: UUID): PaymentResponse {
        val payment = getPaymentOrThrow(id)
        if (payment.status != PaymentStatus.PAYMENT_PENDING) {
            return payment.toResponse()
        }

        val now = Instant.now()
        paymentTransactionRepository.transitionToProcessing(
            id = id,
            fromStatuses = processFrom,
            toStatus = PaymentStatus.PAYMENT_PROCESSING,
            processedAt = now
        )

        return getPaymentOrThrow(id).toResponse()
    }

    @Transactional
    fun completePayment(id: UUID): PaymentResponse {
        val payment = getPaymentOrThrow(id)
        if (payment.status == PaymentStatus.PAYMENT_COMPLETED) {
            return payment.toResponse()
        }
        if (payment.status != PaymentStatus.PAYMENT_PROCESSING) {
            throw IllegalStateException("Payment must be processing to complete")
        }

        val now = Instant.now()
        val updated = paymentTransactionRepository.transitionToCompletedAt(
            id = id,
            fromStatuses = terminalFrom,
            toStatus = PaymentStatus.PAYMENT_COMPLETED,
            completedAt = now
        )

        val after = getPaymentOrThrow(id)
        if (updated == 0 && after.status != PaymentStatus.PAYMENT_COMPLETED) {
            throw IllegalStateException("Payment transition failed")
        }
        return after.toResponse()
    }

    @Transactional
    fun cancelPayment(id: UUID): PaymentResponse {
        val payment = getPaymentOrThrow(id)
        if (payment.status == PaymentStatus.PAYMENT_CANCELLED) {
            return payment.toResponse()
        }
        if (payment.status != PaymentStatus.PAYMENT_PROCESSING) {
            throw IllegalStateException("Payment must be processing to cancel")
        }

        val now = Instant.now()
        val updated = paymentTransactionRepository.transitionToCompletedAt(
            id = id,
            fromStatuses = terminalFrom,
            toStatus = PaymentStatus.PAYMENT_CANCELLED,
            completedAt = now
        )

        val after = getPaymentOrThrow(id)
        if (updated == 0 && after.status != PaymentStatus.PAYMENT_CANCELLED) {
            throw IllegalStateException("Payment transition failed")
        }
        return after.toResponse()
    }

    @Transactional
    fun failPayment(id: UUID, request: PaymentFailRequest): PaymentResponse {
        val payment = getPaymentOrThrow(id)
        if (payment.status == PaymentStatus.PAYMENT_FAILED) {
            return payment.toResponse()
        }
        if (payment.status != PaymentStatus.PAYMENT_PROCESSING) {
            throw IllegalStateException("Payment must be processing to fail")
        }

        val now = Instant.now()
        val updated = paymentTransactionRepository.transitionToFailed(
            id = id,
            fromStatuses = terminalFrom,
            toStatus = PaymentStatus.PAYMENT_FAILED,
            processedAt = now,
            failureReason = request.failureReason
        )

        val after = getPaymentOrThrow(id)
        if (updated == 0 && after.status != PaymentStatus.PAYMENT_FAILED) {
            throw IllegalStateException("Payment transition failed")
        }
        return after.toResponse()
    }
}
