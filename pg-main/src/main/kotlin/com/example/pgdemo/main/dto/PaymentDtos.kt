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
    // If omitted, server defaults to PAYMENT_COMPLETED.
    // 미지정 시 서버가 PAYMENT_COMPLETED로 처리합니다.
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
