package com.example.pgdemo.main.service

import com.example.pgdemo.common.domain.entity.PaymentTransaction
import com.example.pgdemo.common.domain.entity.RefundTransaction
import com.example.pgdemo.common.domain.enum.PaymentStatus
import com.example.pgdemo.common.domain.enum.RefundStatus
import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import com.example.pgdemo.main.dto.RefundRequest
import com.example.pgdemo.main.exception.ResourceNotFoundException
import java.time.Instant
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito

@DisplayName("환불 서비스 테스트")
class RefundServiceTest {
    @Test
    @DisplayName("유효한 요청에 대해 환불 요청 처리 및 응답 반환")
    fun `requestRefund returns response for valid request`() {
        val refundRepository = Mockito.mock(RefundTransactionRepository::class.java)
        val paymentRepository = Mockito.mock(PaymentTransactionRepository::class.java)
        val service = RefundService(refundRepository, paymentRepository)

        val paymentId = UUID.randomUUID()
        val payment = PaymentTransaction().apply {
            id = paymentId
            status = PaymentStatus.PAYMENT_COMPLETED
        }
        Mockito.`when`(paymentRepository.findById(paymentId))
            .thenReturn(Optional.of(payment))

        val refundId = UUID.randomUUID()
        val requestedAt = Instant.parse("2024-01-02T00:00:00Z")
        Mockito.`when`(refundRepository.save(Mockito.any(RefundTransaction::class.java)))
            .thenAnswer { invocation ->
                val saved = invocation.getArgument<RefundTransaction>(0)
                saved.id = refundId
                saved.requestedAt = requestedAt
                saved
            }

        val response = service.requestRefund(
            paymentId,
            RefundRequest(refundAmount = 500L, refundReason = "CUSTOMER")
        )

        assertEquals(refundId, response.id)
        assertEquals(paymentId, response.paymentId)
        assertEquals(500L, response.refundAmount)
        assertEquals("CUSTOMER", response.refundReason)
        assertEquals(RefundStatus.REFUND_PENDING, response.status)
        assertEquals(requestedAt, response.requestedAt)
        assertEquals(null, response.processedAt)
        assertEquals(null, response.completedAt)
    }

    @Test
    @DisplayName("결제가 완료되지 않은 상태에서는 환불 요청이 거절된다")
    fun `requestRefund throws when payment not completed`() {
        val refundRepository = Mockito.mock(RefundTransactionRepository::class.java)
        val paymentRepository = Mockito.mock(PaymentTransactionRepository::class.java)
        val service = RefundService(refundRepository, paymentRepository)

        val paymentId = UUID.randomUUID()
        val payment = PaymentTransaction().apply {
            id = paymentId
            status = PaymentStatus.PAYMENT_PENDING
        }
        Mockito.`when`(paymentRepository.findById(paymentId))
            .thenReturn(Optional.of(payment))

        val exception = assertThrows(IllegalStateException::class.java) {
            service.requestRefund(paymentId, RefundRequest(refundAmount = 500L, refundReason = "CUSTOMER"))
        }
        assertEquals("Payment must be completed to request refund", exception.message)
    }

    @Test
    @DisplayName("결제 내역을 찾을 수 없을 때 환불 요청 시 예외 발생")
    fun `requestRefund throws when payment not found`() {
        val refundRepository = Mockito.mock(RefundTransactionRepository::class.java)
        val paymentRepository = Mockito.mock(PaymentTransactionRepository::class.java)
        val service = RefundService(refundRepository, paymentRepository)

        val paymentId = UUID.randomUUID()
        Mockito.`when`(paymentRepository.findById(paymentId))
            .thenReturn(Optional.empty())

        val exception = assertThrows(ResourceNotFoundException::class.java) {
            service.requestRefund(paymentId, RefundRequest(refundAmount = 500L, refundReason = null))
        }

        assertEquals("Payment not found", exception.message)
    }

    @Test
    @DisplayName("RefundAmount 누락 시 예외 발생")
    fun `requestRefund throws when refundAmount missing`() {
        val refundRepository = Mockito.mock(RefundTransactionRepository::class.java)
        val paymentRepository = Mockito.mock(PaymentTransactionRepository::class.java)
        val service = RefundService(refundRepository, paymentRepository)

        val paymentId = UUID.randomUUID()
        val payment = PaymentTransaction().apply {
            id = paymentId
            status = PaymentStatus.PAYMENT_COMPLETED
        }
        Mockito.`when`(paymentRepository.findById(paymentId))
            .thenReturn(Optional.of(payment))

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.requestRefund(paymentId, RefundRequest(refundAmount = null, refundReason = null))
        }

        assertEquals("refundAmount is required", exception.message)
    }
}
