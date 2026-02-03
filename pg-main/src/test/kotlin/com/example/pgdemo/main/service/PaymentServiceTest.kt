package com.example.pgdemo.main.service

import com.example.pgdemo.common.domain.entity.Merchant
import com.example.pgdemo.common.domain.entity.PaymentTransaction
import com.example.pgdemo.common.domain.enum.BusinessType
import com.example.pgdemo.common.domain.enum.PaymentStatus
import com.example.pgdemo.common.domain.enum.StoreType
import com.example.pgdemo.common.domain.repository.MerchantRepository
import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.main.dto.PaymentRequest
import com.example.pgdemo.main.exception.ResourceNotFoundException
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito

@DisplayName("결제 서비스 테스트")
class PaymentServiceTest {
    @Test
    @DisplayName("유효한 요청에 대해 결제 요청 처리 및 응답 반환")
    fun `requestPayment returns response for valid request`() {
        val paymentRepository = Mockito.mock(PaymentTransactionRepository::class.java)
        val merchantRepository = Mockito.mock(MerchantRepository::class.java)
        val service = PaymentService(paymentRepository, merchantRepository)

        val merchantId = UUID.randomUUID()
        val merchant = Merchant().apply {
            id = merchantId
            merchantCode = "M-100"
            name = "Coffee Club"
            storeType = StoreType.DIRECT
            businessType = BusinessType.CAFE
            contractStartDate = LocalDate.of(2024, 1, 1)
        }

        Mockito.`when`(merchantRepository.findById(merchantId))
            .thenReturn(Optional.of(merchant))

        val paymentId = UUID.randomUUID()
        val requestedAt = Instant.parse("2024-01-01T00:00:00Z")
        Mockito.`when`(paymentRepository.save(Mockito.any(PaymentTransaction::class.java)))
            .thenAnswer { invocation ->
                val saved = invocation.getArgument<PaymentTransaction>(0)
                saved.id = paymentId
                saved.requestedAt = requestedAt
                saved
            }

        val response = service.requestPayment(
            PaymentRequest(
                merchantId = merchantId,
                orderId = "ORDER-1",
                amount = 1500L,
                paymentMethod = "CARD"
            )
        )

        assertEquals(paymentId, response.id)
        assertEquals(merchantId, response.merchantId)
        assertEquals("ORDER-1", response.orderId)
        assertEquals(1500L, response.amount)
        assertEquals("CARD", response.paymentMethod)
        assertEquals(PaymentStatus.PAYMENT_COMPLETED, response.status)
        assertEquals(requestedAt, response.requestedAt)
        assertNotNull(response.processedAt)
        assertNotNull(response.completedAt)
    }

    @Test
    @DisplayName("가맹점을 찾을 수 없을 때 결제 요청 시 예외 발생")
    fun `requestPayment throws when merchant not found`() {
        val paymentRepository = Mockito.mock(PaymentTransactionRepository::class.java)
        val merchantRepository = Mockito.mock(MerchantRepository::class.java)
        val service = PaymentService(paymentRepository, merchantRepository)

        val merchantId = UUID.randomUUID()
        Mockito.`when`(merchantRepository.findById(merchantId))
            .thenReturn(Optional.empty())

        val exception = assertThrows(ResourceNotFoundException::class.java) {
            service.requestPayment(
                PaymentRequest(
                    merchantId = merchantId,
                    orderId = "ORDER-404",
                    amount = 1000L,
                    paymentMethod = "CARD"
                )
            )
        }

        assertEquals("Merchant not found", exception.message)
    }

    @Test
    @DisplayName("MerchantId 누락 시 예외 발생")
    fun `requestPayment throws when merchantId missing`() {
        val paymentRepository = Mockito.mock(PaymentTransactionRepository::class.java)
        val merchantRepository = Mockito.mock(MerchantRepository::class.java)
        val service = PaymentService(paymentRepository, merchantRepository)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            service.requestPayment(
                PaymentRequest(
                    merchantId = null,
                    orderId = "ORDER-NULL",
                    amount = 1000L,
                    paymentMethod = "CARD"
                )
            )
        }

        assertEquals("merchantId is required", exception.message)
    }
}
