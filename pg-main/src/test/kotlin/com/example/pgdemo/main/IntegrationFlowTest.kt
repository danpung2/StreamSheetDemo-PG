package com.example.pgdemo.main

import com.fasterxml.jackson.databind.ObjectMapper
import com.example.pgdemo.common.domain.repository.MerchantRepository
import com.example.pgdemo.common.domain.repository.PaymentTransactionRepository
import com.example.pgdemo.common.domain.repository.RefundTransactionRepository
import com.example.pgdemo.main.dto.MerchantRequest
import com.example.pgdemo.main.dto.MerchantResponse
import com.example.pgdemo.main.dto.PaymentRequest
import com.example.pgdemo.main.dto.PaymentResponse
import com.example.pgdemo.main.dto.RefundRequest
import com.example.pgdemo.main.dto.RefundResponse
import com.example.pgdemo.common.domain.enum.BusinessType
import com.example.pgdemo.common.domain.enum.StoreType
import javax.sql.DataSource
import java.time.LocalDate
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/pgdemo",
        "spring.datasource.username=pgdemo",
        "spring.datasource.password=pgdemo",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.data.mongodb.uri=mongodb://localhost:27017/pgdemo"
    ]
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("메인 서비스 통합 플로우 테스트")
class IntegrationFlowTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var merchantRepository: MerchantRepository

    @Autowired
    private lateinit var paymentTransactionRepository: PaymentTransactionRepository

    @Autowired
    private lateinit var refundTransactionRepository: RefundTransactionRepository

    private val createdMerchantIds = mutableListOf<UUID>()
    private val createdPaymentIds = mutableListOf<UUID>()
    private val createdRefundIds = mutableListOf<UUID>()

    @BeforeAll
    @DisplayName("데이터베이스 연결 확인")
    fun verifyDatabaseConnection() {
        try {
            dataSource.connection.use { connection ->
                if (!connection.isValid(2)) {
                    fail("Database connection is not valid. Ensure docker-compose DBs are running.")
                }
            }
        } catch (ex: Exception) {
            fail("Database connection failed. Ensure docker-compose DBs are running.", ex)
        }
    }

    @AfterEach
    @DisplayName("테스트 데이터 정리")
    fun cleanup() {
        if (createdRefundIds.isNotEmpty()) {
            refundTransactionRepository.deleteAllById(createdRefundIds.toList())
            createdRefundIds.clear()
        }
        if (createdPaymentIds.isNotEmpty()) {
            paymentTransactionRepository.deleteAllById(createdPaymentIds.toList())
            createdPaymentIds.clear()
        }
        if (createdMerchantIds.isNotEmpty()) {
            merchantRepository.deleteAllById(createdMerchantIds.toList())
            createdMerchantIds.clear()
        }
    }

    @Test
    @DisplayName("가맹점 생성, 결제, 환불 플로우 테스트")
    fun `merchant payment refund flow`() {
        val merchantRequest = MerchantRequest(
            merchantCode = "M-${System.nanoTime()}",
            name = "Integration Test Merchant",
            storeType = StoreType.DIRECT,
            businessType = BusinessType.RETAIL,
            contractStartDate = LocalDate.now(),
            contractEndDate = LocalDate.now().plusDays(30),
            storeNumber = 1
        )

        val merchantResponse = postJson(path = "/api/v1/merchants", request = merchantRequest)
        assertThat(merchantResponse.statusCode).isEqualTo(HttpStatus.CREATED)
        val merchant = requireNotNull(parseJson(merchantResponse.body, MerchantResponse::class.java))
        createdMerchantIds.add(merchant.id)

        val paymentRequest = PaymentRequest(
            merchantId = merchant.id,
            orderId = "ORD-${System.nanoTime()}",
            amount = 15000,
            paymentMethod = "CARD"
        )

        val paymentResponse = postJson(path = "/api/v1/payments", request = paymentRequest)
        assertThat(paymentResponse.statusCode).isEqualTo(HttpStatus.CREATED)
        val payment = requireNotNull(parseJson(paymentResponse.body, PaymentResponse::class.java))
        createdPaymentIds.add(payment.id)

        val refundRequest = RefundRequest(
            refundAmount = 5000,
            refundReason = "Integration test refund"
        )

        val refundResponse = postJson(path = "/api/v1/payments/${payment.id}/refund", request = refundRequest)
        assertThat(refundResponse.statusCode).isEqualTo(HttpStatus.CREATED)
        val refund = requireNotNull(parseJson(refundResponse.body, RefundResponse::class.java))
        createdRefundIds.add(refund.id)
        assertThat(refund.paymentId).isEqualTo(payment.id)
    }

    private fun postJson(path: String, request: Any): ResponseEntity<String> {
        val url = "http://localhost:$port$path"
        val headers = org.springframework.http.HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val entity = HttpEntity(request, headers)
        return restTemplate.exchange(url, HttpMethod.POST, entity, String::class.java)
    }

    private fun <T> parseJson(body: String?, type: Class<T>): T? {
        if (body.isNullOrBlank()) {
            return null
        }
        return objectMapper.readValue(body, type)
    }

}
