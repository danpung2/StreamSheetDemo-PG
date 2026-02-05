package com.example.pgdemo.main.service

import com.example.pgdemo.common.domain.entity.PaymentTransaction
import com.example.pgdemo.common.domain.enum.PaymentStatus
import com.example.pgdemo.common.domain.repository.MerchantRepository
import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.common.domain.repository.spec.PaymentTransactionSpecifications
import com.example.pgdemo.main.dto.PaymentRequest
import com.example.pgdemo.main.dto.PaymentResponse
import com.example.pgdemo.main.exception.ResourceNotFoundException
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val merchantRepository: MerchantRepository
) {
    @Transactional
    fun requestPayment(request: PaymentRequest): PaymentResponse {
        val merchantId = request.merchantId ?: throw IllegalArgumentException("merchantId is required")
        val merchant = merchantRepository.findById(merchantId)
            .orElseThrow { ResourceNotFoundException("Merchant not found") }

        val amount = request.amount ?: throw IllegalArgumentException("amount is required")
        val now = Instant.now()
        val payment = PaymentTransaction().apply {
            this.merchant = merchant
            orderId = request.orderId
            this.amount = amount
            paymentMethod = request.paymentMethod
            requestedAt = now
            status = PaymentStatus.PAYMENT_PENDING
            processedAt = null
            completedAt = null
            failureReason = null
        }

        return paymentTransactionRepository.save(payment).toResponse()
    }

    @Transactional(readOnly = true)
    fun getPayment(id: UUID): PaymentResponse {
        val payment = paymentTransactionRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Payment not found") }
        return payment.toResponse()
    }

    @Transactional(readOnly = true)
    fun listPayments(
        pageable: Pageable,
        merchantId: UUID?,
        headquartersId: UUID?,
        status: PaymentStatus?,
        fromUtc: Instant?,
        toUtc: Instant?
    ): Page<PaymentResponse> {
        val spec = PaymentTransactionSpecifications.search(
            merchantId = merchantId,
            headquartersId = headquartersId,
            status = status,
            fromUtc = fromUtc,
            toUtc = toUtc
        )

        return paymentTransactionRepository.findAll(spec, pageable)
            .map { it.toResponse() }
    }
}

fun PaymentTransaction.toResponse(): PaymentResponse {
    val paymentId = id ?: throw IllegalStateException("Payment id is missing")
    val merchantId = merchant.id ?: throw IllegalStateException("Merchant id is missing")

    return PaymentResponse(
        id = paymentId,
        merchantId = merchantId,
        orderId = orderId,
        amount = amount,
        paymentMethod = paymentMethod,
        status = status,
        requestedAt = requestedAt,
        processedAt = processedAt,
        completedAt = completedAt,
        failureReason = failureReason
    )
}
