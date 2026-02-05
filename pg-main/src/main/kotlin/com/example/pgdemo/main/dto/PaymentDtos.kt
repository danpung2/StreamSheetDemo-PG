package com.example.pgdemo.main.dto

import com.example.pgdemo.common.domain.enum.PaymentStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.Instant
import java.util.UUID

data class PaymentRequest(
    @field:NotNull(message = "merchantId is required")
    val merchantId: UUID?,
    @field:NotBlank(message = "orderId is required")
    val orderId: String,
    @field:NotNull(message = "amount is required")
    @field:Positive(message = "amount must be positive")
    val amount: Long?,
    @field:NotBlank(message = "paymentMethod is required")
    val paymentMethod: String,
    // /api/v1/payments는 '요청 생성'만 처리하므로 status/failureReason은 서버에서 무시됩니다.
    // (현실적인 타임라인을 위해 status 전이는 별도 전이 API로 수행합니다.)
    val status: PaymentStatus? = null,
    val failureReason: String? = null
)

data class PaymentResponse(
    val id: UUID,
    val merchantId: UUID,
    val orderId: String,
    val amount: Long,
    val paymentMethod: String,
    val status: PaymentStatus,
    val requestedAt: Instant,
    val processedAt: Instant?,
    val completedAt: Instant?,
    val failureReason: String?
)
